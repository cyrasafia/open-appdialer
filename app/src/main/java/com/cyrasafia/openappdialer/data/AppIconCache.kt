package com.cyrasafia.openappdialer.data

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标内存 LRU（spec F1：磁盘缓存为 backlog）。
 * v3.8：按 包名+Activity 键缓存——多入口包的各入口可能有自己的 Activity 级图标
 * （如 com.android.contacts：联系人用应用图标、电话用拨号图标），getActivityIcon 自动回退应用图标。
 */
class AppIconCache(private val pm: PackageManager) {

    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {}

    suspend fun icon(pkg: String, activity: String = ""): Bitmap? = withContext(Dispatchers.IO) {
        val key = if (activity.isEmpty()) pkg else "$pkg\n$activity"
        cache.get(key) ?: runCatching {
            val drawable = if (activity.isEmpty()) {
                pm.getApplicationIcon(pkg)
            } else {
                pm.getActivityIcon(ComponentName(pkg, activity))
            }
            drawable.toBitmap(width = SIZE_PX, height = SIZE_PX)
        }.getOrNull()?.also { cache.put(key, it) }
    }

    companion object {
        private const val MAX_ENTRIES = 128
        private const val SIZE_PX = 128
    }
}
