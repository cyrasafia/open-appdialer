package com.cyrasafia.openappdialer

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppIconCache
import com.cyrasafia.openappdialer.data.AppRepository
import com.cyrasafia.openappdialer.data.AppsCache
import com.cyrasafia.openappdialer.data.PackageEventReceiver
import com.cyrasafia.openappdialer.data.SettingsStore
import com.cyrasafia.openappdialer.ui.search.AppAction
import com.cyrasafia.openappdialer.ui.search.SearchScreen
import com.cyrasafia.openappdialer.ui.search.SearchViewModel
import com.cyrasafia.openappdialer.ui.settings.SettingsScreen
import com.cyrasafia.openappdialer.ui.theme.OpenAppDialerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 浮窗形态主界面（spec F8）：dialog 主题 Activity，
 * 点外部/返回键直接关闭（不清空输入）。
 */
class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository by lazy {
        AppRepository(
            pm = packageManager,
            cache = AppsCache(File(filesDir, AppsCache.FILE_NAME)),
            scope = appScope,
            excludePackage = packageName,
        )
    }

    private val iconCache by lazy { AppIconCache(packageManager) }

    private val settings by lazy { SettingsStore(this) }

    private val searchViewModel: SearchViewModel by viewModels {
        viewModelFactory { initializer { SearchViewModel(repository) } }
    }

    private val packageReceiver = PackageEventReceiver { action, pkg ->
        repository.onPackageEvent(action, pkg)
    }

    private var showSettings by mutableStateOf(false)

    /** 长按菜单"添加桌面快捷方式"是否可用（spec v2.7：API 26+ 且启动器支持） */
    private val canPinShortcut: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 浮窗底部对齐（spec v2.0）：下缘距屏幕底约 16dp，略高于 dock/手势条区域
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.attributes = window.attributes.apply {
            y = (16 * resources.displayMetrics.density).toInt()
        }

        repository.ensureLoaded()

        setContent {
            val themeMode by settings.theme.collectAsState(initial = com.cyrasafia.openappdialer.data.ThemeMode.SYSTEM)

            // 设置页内返回 → 回搜索页；搜索页返回 → 关浮窗（默认 finish）
            BackHandler(enabled = showSettings) { showSettings = false }

            OpenAppDialerTheme(mode = themeMode) {
                if (showSettings) {
                    SettingsScreen(
                        currentTheme = themeMode,
                        onSelectTheme = { mode ->
                            appScope.launch { settings.setTheme(mode) }
                        },
                        onOpenShortcutPermission = { openPermissionEditor() },
                    )
                } else {
                    SearchScreen(
                        viewModel = searchViewModel,
                        iconCache = iconCache,
                        canPinShortcut = canPinShortcut,
                        onLaunch = ::launchApp,
                        onAppAction = ::handleAppAction,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    /** 启动应用（spec F4）：冻结列表 → 记录使用 → 启动 → finish；失败 toast + 应用详情兜底 */
    private fun launchApp(entry: AppEntry) {
        searchViewModel.onLaunching() // 先冻结，防止使用数据更新导致点击项跳位（spec v2.5）
        repository.recordLaunch(entry.packageName)
        val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
        try {
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            } else {
                openAppDetails(entry.packageName)
                finish()
            }
        } catch (e: Exception) {
            // 冻结/受限等异常场景（spec F4 验收项）
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            openAppDetails(entry.packageName)
            finish()
        }
    }

    /** 长按菜单动作（spec v2.7）：快捷方式 / 详情 / 卸载 */
    private fun handleAppAction(entry: AppEntry, action: AppAction) {
        when (action) {
            AppAction.PIN_SHORTCUT -> pinShortcut(entry)

            AppAction.DETAILS -> {
                openAppDetails(entry.packageName)
                finish()
            }

            AppAction.UNINSTALL -> {
                runCatching {
                    startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null)),
                    )
                }
                finish()
            }
        }
    }

    /** 添加桌面快捷方式（spec v2.7）：requestPinShortcut，图标取自内存缓存 */
    private fun pinShortcut(entry: AppEntry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            Toast.makeText(this, R.string.pin_shortcut_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        appScope.launch {
            val bmp = iconCache.icon(entry.packageName)
            val launch = packageManager.getLaunchIntentForPackage(entry.packageName)
            withContext(Dispatchers.Main) {
                if (launch == null) {
                    Toast.makeText(this@MainActivity, R.string.pin_shortcut_unsupported, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                launch.action = Intent.ACTION_MAIN
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val info = ShortcutInfo.Builder(this@MainActivity, "app-" + entry.packageName)
                    .setShortLabel(entry.label)
                    .setIntent(launch)
                    .apply { if (bmp != null) setIcon(Icon.createWithBitmap(bmp)) }
                    .build()
                sm.requestPinShortcut(info, null)
            }
        }
    }

    /** 打开权限管理（spec v2.8/v2.9）：MIUI 直达官方权限编辑页（dev.mi.com APP_PERM_EDITOR），失败回退应用详情页 */
    private fun openPermissionEditor() {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", packageName)
            putExtra("extra_type", 1) // “其他权限”分类（桌面快捷方式所在）
        }
        if (runCatching { startActivity(miuiIntent) }.isSuccess) return

        val legacyIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            putExtra("extra_pkgname", packageName)
        }
        if (runCatching { startActivity(legacyIntent) }.isSuccess) return

        openAppDetails(packageName) // 非 MIUI / 组件缺失兜底
    }

    private fun openAppDetails(pkg: String) {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null),
                ),
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addDataScheme("package")
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        repository.ensureLoaded() // resume 兜底（spec F1：防漏广播）
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
    }
}
