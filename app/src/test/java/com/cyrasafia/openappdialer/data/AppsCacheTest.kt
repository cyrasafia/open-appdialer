package com.cyrasafia.openappdialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** spec F1：缓存读写、损坏重建、schema 演进 */
class AppsCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        pkg: String = "com.x",
        label: String = "X",
        install: Long = 1_000L,
        count: Int = 0,
        last: Long = 0L,
    ) = AppEntry(pkg, label, install, count, last)

    @Test
    fun `missing file returns null`() {
        assertNull(AppsCache(tmp.newFile()).load())
    }

    @Test
    fun `roundtrip preserves entries and usage`() {
        val cache = AppsCache(tmp.newFile())
        val data = AppsCacheData(
            apps = listOf(
                entry("com.weixin", "微信", 1L, count = 3, last = 99L),
                entry("com.gmail", "Gmail", 2L),
            ),
        )
        cache.save(data)
        assertEquals(data.apps, cache.load()!!.apps)
    }

    @Test
    fun `corrupted file returns null then silently rebuilt`() {
        val file = tmp.newFile().apply { writeText("{ not json") }
        val cache = AppsCache(file)
        assertNull(cache.load())
        cache.save(AppsCacheData(apps = listOf(entry())))
        assertEquals(1, cache.load()!!.apps.size)
    }

    @Test
    fun `future schema version triggers rebuild`() {
        val file = tmp.newFile().apply {
            writeText("""{"schemaVersion":99,"apps":[],"futureField":1}""")
        }
        // schema 不符 → 视为无缓存（静默重建），避免旧结构缺字段
        assertNull(AppsCache(file).load())
    }
}
