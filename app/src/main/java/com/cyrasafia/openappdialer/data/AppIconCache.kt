package com.cyrasafia.openappdialer.data

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标内存 LRU（spec F1：磁盘缓存为 backlog）。
 * 后台线程解码，Compose 侧 produceState 异步消费；首屏先渲染文字，图标后补。
 */
class AppIconCache(private val pm: PackageManager) {

    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {}

    suspend fun icon(pkg: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(pkg) ?: runCatching {
            pm.getApplicationIcon(pkg).toBitmap(width = SIZE_PX, height = SIZE_PX)
        }.getOrNull()?.also { cache.put(pkg, it) }
    }

    companion object {
        private const val MAX_ENTRIES = 128
        private const val SIZE_PX = 128
    }
}
