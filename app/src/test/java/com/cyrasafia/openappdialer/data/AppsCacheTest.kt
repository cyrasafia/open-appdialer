package com.cyrasafia.openappdialer.data

import com.cyrasafia.openappdialer.search.DefaultPinyinSource
import com.cyrasafia.openappdialer.search.LabelIndex
import com.cyrasafia.openappdialer.search.LabelIndexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** spec F1：缓存读写、索引压缩编解码、损坏/schema 重建 */
class AppsCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pinyin = DefaultPinyinSource()

    private fun entry(
        pkg: String = "com.x",
        label: String = "微信",
        install: Long = 1_000L,
        count: Int = 3,
        last: Long = 99L,
    ) = AppEntry(
        packageName = pkg,
        label = label,
        firstInstallTime = install,
        launchCount = count,
        lastLaunchAt = last,
        index = LabelIndexer.index(label, pinyin),
        packageKeys = LabelIndexer.packageKeys(pkg),
    )

    @Test
    fun `missing file returns null`() {
        assertNull(AppsCache(tmp.newFile()).load())
    }

    @Test
    fun `roundtrip preserves entries usage and index`() {
        val cache = AppsCache(tmp.newFile())
        val apps = listOf(
            entry("com.weixin", "微信"),
            entry("com.t9", "T9启动器", count = 0, last = 0L),
            entry("com.dm", "1DM+", count = 1, last = 5L),
        )
        cache.save(apps)
        assertEquals(apps, cache.load())
    }

    @Test
    fun `empty index roundtrips`() {
        val cache = AppsCache(tmp.newFile())
        val apps = listOf(entry("com.plain", "plain").copy(index = LabelIndex.EMPTY))
        cache.save(apps)
        assertEquals(apps, cache.load())
    }

    @Test
    fun `corrupted file returns null then silently rebuilt`() {
        val file = tmp.newFile().apply { writeText("{ not json") }
        val cache = AppsCache(file)
        assertNull(cache.load())
        cache.save(listOf(entry()))
        assertEquals(1, cache.load()!!.size)
    }

    @Test
    fun `future schema version triggers rebuild`() {
        val file = tmp.newFile().apply {
            writeText("""{"schemaVersion":99,"apps":[],"futureField":1}""")
        }
        assertNull(AppsCache(file).load())
    }
}
