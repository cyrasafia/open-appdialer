package com.cyrasafia.openappdialer.panel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.cyrasafia.openappdialer.MainActivity

/**
 * dock/桌面图标入口（spec F10）：NoDisplay 跳板，无窗口 → 无 starting window → 无垫色闪屏。
 * 已授权 overlay → 显示常驻面板；未授权 → 进 MainActivity 授权引导。
 */
class PanelLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("OAD/Trampoline", "launch, overlay=${Settings.canDrawOverlays(this)}")
        if (Settings.canDrawOverlays(this)) {
            PanelService.start(this)
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_REQUEST_OVERLAY, true),
            )
        }
        finish() // NoDisplay 必须立即结束
    }

}
