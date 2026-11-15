package com.cyrasafia.openappdialer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.ThemeMode
import com.cyrasafia.openappdialer.panel.AppActionHandler
import com.cyrasafia.openappdialer.panel.PanelService
import com.cyrasafia.openappdialer.ui.search.SearchScreen
import com.cyrasafia.openappdialer.ui.search.SearchViewModel
import com.cyrasafia.openappdialer.ui.settings.SettingsScreen
import com.cyrasafia.openappdialer.ui.theme.OpenAppDialerTheme
import com.cyrasafia.openappdialer.data.PackageEventReceiver
import kotlinx.coroutines.launch

/**
 * 浮窗 Activity（spec F8/F9）：通知唤起入口 + 设置 + overlay 授权引导。
 * dock 图标走 PanelLaunchActivity 跳板（spec F10）。
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as OpenAppDialerApp

    private val searchViewModel: SearchViewModel by viewModels {
        viewModelFactory { initializer { SearchViewModel(app.repository) } }
    }

    private val packageReceiver = PackageEventReceiver { action, pkg ->
        app.repository.onPackageEvent(action, pkg)
    }

    private var showSettings by mutableStateOf(false)
    private var showOverlayGuide by mutableStateOf(false)

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

        resolveIntent(intent)
        setupEntry()

        setContent {
            val themeMode by app.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)

            // 设置页内返回 → 回搜索页；搜索页返回 → 关浮窗（默认 finish）
            BackHandler(enabled = showSettings) { showSettings = false }

            OpenAppDialerTheme(mode = themeMode) {
                // overlay 授权引导（spec F10）
                if (showOverlayGuide && !Settings.canDrawOverlays(this)) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showOverlayGuide = false },
                        title = { Text(getString(R.string.overlay_guide_title)) },
                        text = { Text(getString(R.string.overlay_guide_text)) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { requestOverlayPermission() }) {
                                Text(getString(R.string.overlay_guide_action))
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showOverlayGuide = false }) {
                                Text(getString(android.R.string.cancel))
                            }
                        },
                    )
                }

                if (showSettings) {
                    SettingsScreen(
                        currentTheme = themeMode,
                        onSelectTheme = { mode ->
                            app.appScope.launch { app.settings.setTheme(mode) }
                        },
                    )
                } else {
                    SearchScreen(
                        viewModel = searchViewModel,
                        iconCache = app.iconCache,
                        canPinShortcut = canPinShortcut,
                        onLaunch = { entry ->
                            searchViewModel.onLaunching()
                            AppActionHandler.launchApp(this, app.appScope, entry) { pkg ->
                                app.repository.recordLaunch(pkg)
                            }
                            finish()
                        },
                        onAppAction = { entry, action ->
                            AppActionHandler.handle(this, app.appScope, entry, action) { pkg ->
                                app.iconCache.icon(pkg)
                            }
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent?) {
        showOverlayGuide = intent?.getBooleanExtra(EXTRA_REQUEST_OVERLAY, false) ?: false
    }

    /** 入口策略（F9/F10）：已授权 overlay → 确保常驻服务（不重弹面板）；否则走应用内通知 */
    private fun setupEntry() {
        if (Settings.canDrawOverlays(this)) {
            PanelService.start(this, show = false)
            return // FGS 常驻通知即唤起入口，不再自建通知
        }
        if (showOverlayGuide) {
            // 授权引导由跳板触发：先请求通知权限（引导弹窗需要），授权页跳转见 Compose 层入口
        }
        ensureNotification()
        if (showOverlayGuide) {
            Toast.makeText(this, R.string.overlay_guide_toast, Toast.LENGTH_LONG).show()
        }
    }

    /** 通知唤起（spec F9）：常驻 LOW 通知，点击拉起浮窗（不走桌面转场） */
    private fun ensureNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIF)
            return
        }
        postSearchNotification()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIF &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            postSearchNotification()
        }
    }

    private fun postSearchNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setSound(null, null) }
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.search_hint))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(NOTIF_ID, notification)
    }

    /** overlay 授权引导（spec F10）：跳系统"显示在其他应用上层"开关页 */
    private fun requestOverlayPermission() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", packageName, null),
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
        app.repository.ensureLoaded() // resume 兜底（spec F1：防漏广播）

        // 从授权页返回：已授权则确保常驻（不弹面板，避免覆盖当前界面）
        if (Settings.canDrawOverlays(this)) {
            PanelService.start(this, show = false)
            if (showOverlayGuide) finish()
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
    }

    companion object {
        const val EXTRA_REQUEST_OVERLAY = "request_overlay"
        private const val REQUEST_NOTIF = 1001
        private const val NOTIF_CHANNEL_ID = "search"
        private const val NOTIF_ID = 1001
    }
}
