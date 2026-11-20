package com.cyrasafia.openappdialer.panel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.ui.search.AppAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 应用动作处理器（spec F4/v2.7/v3.1）：MainActivity 与 overlay 面板共用 */
object AppActionHandler {

    fun launchApp(context: Context, scope: CoroutineScope, entry: AppEntry, record: (String, String) -> Unit) {
        record(entry.packageName, entry.activityName)
        // v3.7：优先用条目的精确组件（同包多入口时 getLaunchIntentForPackage 只返回默认入口）
        val intent = if (entry.activityName.isNotEmpty()) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = android.content.ComponentName(entry.packageName, entry.activityName)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(entry.packageName)
        }
        try {
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                openDetails(context, entry.packageName)
            }
        } catch (e: Exception) {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            openDetails(context, entry.packageName)
        }
    }

    fun handle(context: Context, scope: CoroutineScope, entry: AppEntry, action: AppAction, iconProvider: suspend (String, String) -> android.graphics.Bitmap?) {
        when (action) {
            AppAction.PIN_SHORTCUT -> pinShortcut(context, scope, entry, iconProvider)
            AppAction.DETAILS -> {
                openDetails(context, entry.packageName)
                if (context is Activity) context.finish()
            }

            AppAction.UNINSTALL -> {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_DELETE,
                            Uri.fromParts("package", entry.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                if (context is Activity) context.finish()
            }
        }
    }

    private fun openDetails(context: Context, pkg: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun pinShortcut(
        context: Context,
        scope: CoroutineScope,
        entry: AppEntry,
        iconProvider: suspend (String, String) -> android.graphics.Bitmap?,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            Toast.makeText(context, R.string.pin_shortcut_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val bmp = iconProvider(entry.packageName, entry.activityName)
            // v3.7：优先精确组件，保证多入口包固定到正确入口
            val launch = if (entry.activityName.isNotEmpty()) {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = android.content.ComponentName(entry.packageName, entry.activityName)
                }
            } else {
                context.packageManager.getLaunchIntentForPackage(entry.packageName)
            }
            withContext(Dispatchers.Main) {
                if (launch == null) {
                    Toast.makeText(context, R.string.pin_shortcut_unsupported, Toast.LENGTH_LONG).show()
                    return@withContext
                }
                launch.action = Intent.ACTION_MAIN
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val info = android.content.pm.ShortcutInfo.Builder(context, "app-" + entry.packageName)
                    .setShortLabel(entry.label)
                    .setIntent(launch)
                    .apply { if (bmp != null) setIcon(android.graphics.drawable.Icon.createWithBitmap(bmp)) }
                    .build()
                runCatching { sm.requestPinShortcut(info, null) }
            }
        }
    }
}
