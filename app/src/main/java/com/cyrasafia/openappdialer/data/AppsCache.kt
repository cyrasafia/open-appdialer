package com.cyrasafia.openappdialer.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppsCacheData(
    val schemaVersion: Int = AppsCache.SCHEMA_VERSION,
    val apps: List<AppEntry> = emptyList(),
)

/**
 * apps.json 磁盘缓存（spec F1）：
 * - 目的：冷启动不做包管理器查询/拼音解析，直接加载
 * - 损坏时静默返回 null 并由调用方重建
 * - 写入原子（tmp + rename），避免进程被杀留下半截文件
 */
class AppsCache(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppsCacheData? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<AppsCacheData>(file.readText()) }
            .getOrNull() // 损坏 → null → 重建
    }

    fun save(data: AppsCacheData) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(data))
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "atomic save failed for ${file.path}" }
            }
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "apps.json"
    }
}
