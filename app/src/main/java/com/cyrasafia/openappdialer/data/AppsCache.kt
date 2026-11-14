package com.cyrasafia.openappdialer.data

import com.cyrasafia.openappdialer.search.LabelIndex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * apps.json 磁盘缓存（spec F1，schema v3）：
 * - 目的：冷启动不做包管理器查询/拼音解析，直接加载
 * - 索引压缩为分隔符串（体积降 5-10 倍，解析提速）
 * - 损坏/schema 不符 → 静默返回 null 由调用方重建
 * - 写入原子（tmp + rename）
 * - 全部解析在调用方后台线程进行（绝不阻塞主线程/首帧）
 */

/** 磁盘 DTO（与领域模型 AppEntry 解耦，字段名压缩） */
@Serializable
data class CachedApp(
    val p: String, // packageName
    val l: String, // label
    val i: Long, // firstInstallTime
    val c: Int = 0, // launchCount
    val t: Long = 0L, // lastLaunchAt
    val vf: String = "", // variants：全拼键串，'\u0001' 分隔
    val vi: String = "", // variants：首字母键串，'\u0001' 分隔（与 vf 一一对应）
    val u: String = "", // units：'\u0001' 分隔（r=键串 / c=逗号分隔读音 / s=特殊字符）
)

@Serializable
private data class AppsCacheData(
    val schemaVersion: Int = AppsCache.SCHEMA_VERSION,
    val apps: List<CachedApp> = emptyList(),
)

/** LabelIndex ↔ 压缩串编解码（键串仅含 '2'..'9'、拼音仅 a-z，分隔符安全） */
object IndexCodec {

    private const val SEP = '\u0001'

    fun encode(index: LabelIndex): Triple<String, String, String> {
        val vf = index.variants.joinToString(SEP.toString()) { it.full }
        val vi = index.variants.joinToString(SEP.toString()) { it.initials }
        val u = index.units.joinToString(SEP.toString()) { unit ->
            when (unit.kind) {
                0 -> "r" + unit.keyString
                1 -> "c" + unit.readings.joinToString(",")
                else -> "s"
            }
        }
        return Triple(vf, vi, u)
    }

    fun decode(vf: String, vi: String, u: String): LabelIndex {
        val fulls = vf.split(SEP).filter { it.isNotEmpty() }
        val initials = vi.split(SEP).filter { it.isNotEmpty() }
        val variants = fulls.zip(initials) { f, i -> LabelIndex.KeyVariant(f, i) }
        val units = u.split(SEP).mapNotNull { s ->
            when (s.firstOrNull()) {
                'r' -> LabelIndex.SearchUnit(kind = 0, keyString = s.substring(1))
                'c' -> LabelIndex.SearchUnit(
                    kind = 1,
                    readings = s.substring(1).split(',').filter { it.isNotEmpty() },
                )

                's' -> LabelIndex.SearchUnit(kind = 2)
                else -> null
            }
        }
        return LabelIndex(variants = variants, units = units)
    }
}

class AppsCache(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 加载并解码；损坏或 schema 不符返回 null。调用方负责在后台线程执行 */
    fun load(): List<AppEntry>? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<AppsCacheData>(file.readText()) }
            .getOrNull() // 损坏 → null → 重建
            ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
            ?.apps?.map { it.toEntry() }
    }

    fun save(apps: List<AppEntry>) {
        runCatching {
            val data = AppsCacheData(apps = apps.map { it.toCached() })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(data))
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "atomic save failed for ${file.path}" }
            }
        }
    }

    private fun CachedApp.toEntry(): AppEntry = AppEntry(
        packageName = p,
        label = l,
        firstInstallTime = i,
        launchCount = c,
        lastLaunchAt = t,
        index = IndexCodec.decode(vf, vi, u),
    )

    private fun AppEntry.toCached(): CachedApp {
        val (vf, vi, u) = IndexCodec.encode(index)
        return CachedApp(p = packageName, l = label, i = firstInstallTime, c = launchCount, t = lastLaunchAt, vf = vf, vi = vi, u = u)
    }

    companion object {
        /** v3：索引压缩序列化（v2 为完整 JSON 结构，体积大、主线程解析慢） */
        const val SCHEMA_VERSION = 3
        const val FILE_NAME = "apps.json"
    }
}
