package com.cyrasafia.openappdialer.search

import com.cyrasafia.openappdialer.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** spec F2 排序：层级 → exact → 位置 → 使用次数 → 最近使用 → label → 包名（确定性） */
class SearchEngineTest {

    private val pinyin = DefaultPinyinSource()

    private fun entry(
        pkg: String,
        label: String,
        count: Int = 0,
        last: Long = 0L,
    ) = AppEntry(
        packageName = pkg,
        label = label,
        firstInstallTime = 0L,
        launchCount = count,
        lastLaunchAt = last,
        index = LabelIndexer.index(label, pinyin),
    )

    @Test
    fun `prefix beats substring across apps`() {
        // 369：360安全卫士 全拼前缀（369…）vs 1DM+ 子串（2369 含 369）
        val apps = listOf(entry("com.dm", "1DM+", count = 9, last = 99L), entry("com.qihu", "360安全卫士"))
        val result = SearchEngine.search(apps, "369")
        assertEquals(listOf("com.qihu", "com.dm"), result.map { it.entry.packageName })
    }

    @Test
    fun `usage count then recency tiebreak within same level`() {
        val apps = listOf(
            entry("a.wechat", "微信", count = 3, last = 500L),
            entry("b.wechat2", "微信", count = 5, last = 100L), // 次数多者胜
            entry("c.wechat3", "微信", count = 3, last = 900L), // 同次数，最近使用胜
        )
        val result = SearchEngine.search(apps, "934946")
        assertEquals(listOf("b.wechat2", "c.wechat3", "a.wechat"), result.map { it.entry.packageName })
    }

    @Test
    fun `label then package stable tiebreak`() {
        val apps = listOf(
            entry("z.pkg", "微信", count = 1, last = 100L),
            entry("a.pkg", "微信", count = 1, last = 100L),
        )
        val result = SearchEngine.search(apps, "934946")
        assertEquals(listOf("a.pkg", "z.pkg"), result.map { it.entry.packageName })
    }

    @Test
    fun `deterministic across runs`() {
        val apps = listOf(
            entry("com.dm", "1DM+", count = 2, last = 50L),
            entry("com.qihu", "360安全卫士"),
            entry("com.weixin", "微信", count = 9, last = 900L),
        )
        assertEquals(
            SearchEngine.search(apps, "369").map { it.entry.packageName },
            SearchEngine.search(apps, "369").map { it.entry.packageName },
        )
    }

    @Test
    fun `empty query yields empty result`() {
        assertEquals(emptyList<MatchedApp>(), SearchEngine.search(listOf(entry("a", "微信")), ""))
    }
}
