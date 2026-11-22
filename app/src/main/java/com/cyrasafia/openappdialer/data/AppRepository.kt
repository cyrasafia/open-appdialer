package com.cyrasafia.openappdialer.data

import android.content.Intent
import android.content.pm.PackageManager
import com.cyrasafia.openappdialer.search.DefaultPinyinSource
import com.cyrasafia.openappdialer.search.LabelIndexer
import com.cyrasafia.openappdialer.search.PinyinSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 应用数据仓库（spec F1）。
 * - 冷启动快路径：apps.json 直读 → 后台全量校准（M1 指标：<500ms 可用）
 * - 包事件 targeted 增量（upsert/remove）
 * - ensureLoaded() 带 30s 节流的全量扫描兜底漏广播（resume 时调用）
 */
class AppRepository(
    private val pm: PackageManager,
    private val cache: AppsCache,
    private val scope: CoroutineScope,
    private val pinyinSource: PinyinSource = DefaultPinyinSource(),
    /** 屏蔽自身（spec v2.5）：不出现在候选列表 */
    private val excludePackage: String? = null,
) {

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** 变更互斥（v3.10）：MainActivity 与 PanelService 双接收器并发事件防竞态 */
    private val mutationMutex = Mutex()

    @Volatile
    private var initialLoadStarted = false

    @Volatile
    private var lastScanAt = 0L

    /**
     * 全异步（spec v2.2）：绝不阻塞主线程/首帧——冷启动面板立即渲染，
     * 缓存列表在后台补上，随后节流全量校准。
     */
    fun ensureLoaded() {
        if (!initialLoadStarted) {
            initialLoadStarted = true
            scope.launch {
                cache.load()?.let { cached -> _apps.value = cached }
                lastScanAt = System.currentTimeMillis()
                fullScan()
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastScanAt >= RESCAN_THROTTLE_MS) {
            lastScanAt = now
            scope.launch { fullScan() }
        }
    }

    /** 全量扫描：重建列表，usage（次数/时间）按包名保留 */
    suspend fun fullScan() = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val old = _apps.value.associateBy { it.packageName } // 旧 usage 按包名合并（v3.7：同包多入口首个继承）
            val entries = pm.queryIntentActivities(intent, 0)
                .asSequence()
                .mapNotNull { ri ->
                    val info = ri.activityInfo ?: return@mapNotNull null
                    val pkg = info.applicationInfo?.packageName ?: return@mapNotNull null
                    if (pkg == excludePackage) return@mapNotNull null
                    val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                    val installedAt = runCatching {
                        pm.getPackageInfo(pkg, 0).firstInstallTime
                    }.getOrDefault(0L)
                    val indexed = LabelIndexer.index(label, pinyinSource)
                    val usage = old[pkg]
                    if (usage != null) {
                        usage.copy(
                            label = label,
                            firstInstallTime = installedAt,
                            index = indexed,
                            packageKeys = LabelIndexer.packageKeys(pkg),
                            activityName = info.name,
                        )
                    } else {
                        AppEntry(
                            packageName = pkg,
                            activityName = info.name,
                            label = label,
                            firstInstallTime = installedAt,
                            index = indexed,
                            packageKeys = LabelIndexer.packageKeys(pkg),
                        )
                    }
                }
                .distinctBy { it.packageName to it.activityName } // v3.7：同包多入口共存
                .sortedWith(compareBy({ it.label }, { it.activityName }))
                .toList()
                _apps.value = entries
                cache.save(entries)
            }
        }
    }

    /** 包事件（spec F1）：ADDED/REPLACED/CHANGED → upsert；REMOVED → remove */
    fun onPackageEvent(action: String, pkg: String) {
        scope.launch {
            if (action == PackageEventReceiver.ACTION_REMOVE) remove(pkg) else upsert(pkg)
        }
    }

    /** 启动记录（spec F1 使用数据；M3 由结果点击接入）。v3.7：按 包名+Activity 条目级计数 */
    fun recordLaunch(pkg: String, activity: String) {
        scope.launch {
            mutationMutex.withLock {
                val now = System.currentTimeMillis()
                _apps.value = _apps.value.map {
                    if (it.packageName == pkg && it.activityName == activity) {
                        it.copy(launchCount = it.launchCount + 1, lastLaunchAt = now)
                    } else {
                        it
                    }
                }
                persist()
            }
        }
    }

    private suspend fun upsert(pkg: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            runCatching {
                if (pkg == excludePackage) return@runCatching
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0).filter {
                it.activityInfo?.applicationInfo?.packageName == pkg
            }
            if (resolved.isEmpty()) {
                // 无启动入口（或已卸载）→ 移除该包全部条目
                _apps.value = _apps.value.filterNot { it.packageName == pkg }
                persist()
                return@runCatching
            }
            val oldByActivity = _apps.value.filter { it.packageName == pkg }.associateBy { it.activityName }
            val oldByPkg = _apps.value.firstOrNull { it.packageName == pkg } // 旧数据迁移
            val installedAt = runCatching {
                pm.getPackageInfo(pkg, 0).firstInstallTime
            }.getOrDefault(0L)
            val rebuilt = resolved.map { ri ->
                val info = ri.activityInfo
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                val old = oldByActivity[info.name] ?: oldByPkg?.takeIf { oldByActivity.isEmpty() }
                AppEntry(
                    packageName = pkg,
                    activityName = info.name,
                    label = label,
                    firstInstallTime = installedAt,
                    launchCount = old?.launchCount ?: 0,
                    lastLaunchAt = old?.lastLaunchAt ?: 0L,
                    index = LabelIndexer.index(label, pinyinSource),
                    packageKeys = LabelIndexer.packageKeys(pkg),
                )
            }.distinctBy { it.activityName }
                _apps.value = _apps.value.filterNot { it.packageName == pkg } + rebuilt
                persist()
            }
        }
    }

    private suspend fun remove(pkg: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            _apps.value = _apps.value.filterNot { it.packageName == pkg }
            persist()
        }
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        cache.save(_apps.value)
    }

    companion object {
        private const val RESCAN_THROTTLE_MS = 30_000L
    }
}
