package com.cyrasafia.openappdialer.panel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cyrasafia.openappdialer.MainActivity
import com.cyrasafia.openappdialer.OpenAppDialerApp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.ThemeMode
import com.cyrasafia.openappdialer.ui.search.SearchScreen
import com.cyrasafia.openappdialer.ui.search.SearchViewModel
import com.cyrasafia.openappdialer.ui.theme.OpenAppDialerTheme

/**
 * overlay 面板服务（spec F10）：
 * - FGS 常驻（specialUse），常驻通知即唤起入口（与 F9 同 id 复用）
 * - dock 跳板/通知点击 → ACTION_SHOW → 显示 overlay Compose 面板
 * - 点面板外关闭；启动应用后收起、服务常驻
 */
class PanelService : LifecycleService() {

    private val app: OpenAppDialerApp get() = application as OpenAppDialerApp
    private val owner = ServiceLifecycleOwner()
    private var panelView: FrameLayout? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingHide = Runnable { hidePanel() }

    /** 切任务/后台任务管理/息屏时自动收起面板（spec v3.6：Home 与最近任务触发系统广播，聊天气泡同款机制） */
    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS, Intent.ACTION_SCREEN_OFF -> hidePanel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "service onCreate")
        runCatching {
            owner.performRestore(null)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            startForeground(NOTIF_ID, buildNotification())
        }.onFailure { Log.e(TAG, "onCreate failed", it) }
        ContextCompat.registerReceiver(
            this,
            systemEventsReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand action=${intent?.action} overlay=${Settings.canDrawOverlays(this)}")
        // 仅 ACTION_SHOW 弹面板；ACTION_ENSURE 只确保常驻（MainActivity 打开时不得重弹面板，spec F10）
        if (intent?.action == ACTION_SHOW && Settings.canDrawOverlays(this)) {
            runCatching { showPanel() }.onFailure { Log.e(TAG, "showPanel failed", it) }
        }
        return START_STICKY // 常驻（spec v3.1 用户决策）
    }

    private fun showPanel() {
        if (panelView != null) return
        searchViewModel.resetSession() // 每次唤起均为全新会话（spec v3.2）
        val wm = getSystemService(WindowManager::class.java) ?: return

        val density = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (16 * density).toInt() // 与 F8 浮窗一致
        }

        val root = object : FrameLayout(this) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hidePanel()
                    return true
                }
                return super.dispatchTouchEvent(event)
            }

            // 即时收起（spec v3.6 快速路径 + v3.9 防抖）：切应用/回桌面的瞬间触发；
            // 150ms 防抖吸收弹层/OEM 的瞬时焦点抖动，真离开时仍近乎即时
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus) {
                    this@PanelService.mainHandler.postDelayed(this@PanelService.pendingHide, 150)
                } else {
                    this@PanelService.mainHandler.removeCallbacks(this@PanelService.pendingHide)
                }
            }
        }.apply {
            // ViewTree owners 必须设在窗口根视图上：WindowRecomposer 在 rootView 上查找（本 bug 实测修复）
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }

        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val themeMode by app.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
                OpenAppDialerTheme(mode = themeMode) {
                    SearchScreen(
                        viewModel = searchViewModel,
                        iconCache = app.iconCache,
                        canPinShortcut = canPinShortcut(),
                        onLaunch = { entry ->
                            AppActionHandler.launchApp(this@PanelService, app.appScope, entry) { pkg, act ->
                                app.repository.recordLaunch(pkg, act)
                            }
                            hidePanel()
                        },
                        onAppAction = { entry, action ->
                            AppActionHandler.handle(
                                this@PanelService, app.appScope, entry, action,
                            ) { pkg, act -> app.iconCache.icon(pkg, act) }
                            if (action != com.cyrasafia.openappdialer.ui.search.AppAction.PIN_SHORTCUT) hidePanel()
                        },
                        onOpenSettings = {
                            runCatching {
                                startActivity(
                                    Intent(this@PanelService, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true),
                                )
                            }
                            hidePanel()
                        },
                    )
                }
            }
        }
        root.addView(compose)
        wm.addView(root, lp)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        panelView = root
    }

    fun hidePanel() {
        mainHandler.removeCallbacks(pendingHide)
        val view = panelView ?: return
        panelView = null
        owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        runCatching { getSystemService(WindowManager::class.java)?.removeView(view) }
    }

    private val searchViewModel: SearchViewModel by lazy { SearchViewModel(app.repository) }

    private fun canPinShortcut(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            getSystemService(android.content.pm.ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setSound(null, null) },
            )
        }
        val showPanel = PendingIntent.getService(
            this, 0,
            Intent(this, PanelService::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.search_hint))
            .setContentIntent(showPanel)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(systemEventsReceiver) }
        hidePanel()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.cyrasafia.openappdialer.panel.SHOW"
        const val ACTION_ENSURE = "com.cyrasafia.openappdialer.panel.ENSURE"
        private const val TAG = "OAD/PanelService"

        /** @param show true=弹面板（dock/通知点击）；false=仅确保常驻（MainActivity 打开时） */
        @JvmStatic
        fun start(context: android.content.Context, show: Boolean = true) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PanelService::class.java).setAction(if (show) ACTION_SHOW else ACTION_ENSURE),
            )
        }

        private const val NOTIF_CHANNEL_ID = "search"
        private const val NOTIF_ID = 1001
    }
}
