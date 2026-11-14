package com.cyrasafia.openappdialer.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 包事件转发（spec F1）：运行时注册（RECEIVER_NOT_EXPORTED），非 manifest 常驻。
 * REPLACING 场景下系统会连发 REMOVED+ADDED，用 EXTRA_REPLACING 过滤避免误删。
 */
class PackageEventReceiver(
    private val onEvent: (action: String, pkg: String) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> if (!replacing) onEvent(ACTION_UPSERT, pkg)
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED,
            -> onEvent(ACTION_UPSERT, pkg)
            Intent.ACTION_PACKAGE_REMOVED -> if (!replacing) onEvent(ACTION_REMOVE, pkg)
        }
    }

    companion object {
        const val ACTION_UPSERT = "upsert"
        const val ACTION_REMOVE = "remove"
    }
}
