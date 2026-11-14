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
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val old = _apps.value.associateBy { it.packageName }
            val entries = pm.queryIntentActivities(intent, 0)
                .asSequence()
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.applicationInfo?.packageName ?: return@mapNotNull null
                    if (pkg == excludePackage) return@mapNotNull null
                    val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                    val installedAt = runCatching {
                        pm.getPackageInfo(pkg, 0).firstInstallTime
                    }.getOrDefault(0L)
                    val indexed = LabelIndexer.index(label, pinyinSource)
                    val usage = old[pkg]
                    if (usage != null) {
                        usage.copy(label = label, firstInstallTime = installedAt, index = indexed)
                    } else {
                        AppEntry(
                            packageName = pkg,
                            label = label,
                            firstInstallTime = installedAt,
                            index = indexed,
                        )
                    }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label }
                .toList()
            _apps.value = entries
            cache.save(entries)
        }
    }

    /** 包事件（spec F1）：ADDED/REPLACED/CHANGED → upsert；REMOVED → remove */
    fun onPackageEvent(action: String, pkg: String) {
        scope.launch {
            if (action == PackageEventReceiver.ACTION_REMOVE) remove(pkg) else upsert(pkg)
        }
    }

    /** 启动记录（spec F1 使用数据；M3 由结果点击接入） */
    fun recordLaunch(pkg: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            _apps.value = _apps.value.map {
                if (it.packageName == pkg) it.copy(launchCount = it.launchCount + 1, lastLaunchAt = now) else it
            }
            persist()
        }
    }

    private suspend fun upsert(pkg: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (pkg == excludePackage) return@runCatching
            pm.getLaunchIntentForPackage(pkg) ?: return@runCatching // 无桌面入口 → 不入列表
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val installedAt = runCatching {
                pm.getPackageInfo(pkg, 0).firstInstallTime
            }.getOrDefault(0L)
            val old = _apps.value.firstOrNull { it.packageName == pkg }
            val entry = AppEntry(
                packageName = pkg,
                label = label,
                firstInstallTime = installedAt,
                launchCount = old?.launchCount ?: 0,
                lastLaunchAt = old?.lastLaunchAt ?: 0L,
                index = LabelIndexer.index(label, pinyinSource),
            )
            _apps.value = _apps.value.filterNot { it.packageName == pkg } + entry
            persist()
        }
    }

    private suspend fun remove(pkg: String) = withContext(Dispatchers.IO) {
        _apps.value = _apps.value.filterNot { it.packageName == pkg }
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        cache.save(_apps.value)
    }

    companion object {
        private const val RESCAN_THROTTLE_MS = 30_000L
    }
}
