package com.cyrasafia.openappdialer.data

import com.cyrasafia.openappdialer.data.AppOrdering.NEW_INSTALL_WINDOW_MS
import com.cyrasafia.openappdialer.data.AppOrdering.orderForEmptyState
import org.junit.Assert.assertEquals
import org.junit.Test

/** spec F4 / Q9：空态排序 = 新安装（≤2h，安装时间降序）> 最近使用降序 > 从未使用按 label */
class AppOrderingTest {

    private val now = 1_000_000_000L

    private fun entry(
        pkg: String,
        label: String = pkg,
        install: Long = 0L,
        count: Int = 0,
        last: Long = 0L,
    ) = AppEntry(pkg, label, install, count, last)

    @Test
    fun `fresh installs first, newest first, then recency`() {
        val apps = listOf(
            entry("old-used", "旧常用", install = now - 10 * 3_600_000L, count = 5, last = now - 1_000L),
            entry("fresh-2", "新装2", install = now - 10 * 60_000L),
            entry("fresh-1", "新装1", install = now - 60 * 60_000L),
        )
        assertEquals(
            listOf("fresh-2", "fresh-1", "old-used"),
            apps.orderForEmptyState(now).map { it.packageName },
        )
    }

    @Test
    fun `exactly 2h old is not fresh, 2h minus 1ms is`() {
        val apps = listOf(
            entry("edge-out", install = now - NEW_INSTALL_WINDOW_MS),
            entry("edge-in", install = now - NEW_INSTALL_WINDOW_MS + 1),
        )
        val ordered = apps.orderForEmptyState(now).map { it.packageName }
        assertEquals("edge-in", ordered.first())
        assertEquals("edge-out", ordered.last())
    }

    @Test
    fun `used by recency desc, never-used last by label asc`() {
        val apps = listOf(
            entry("never-b", label = "b"),
            entry("used-old", label = "u1", install = 1L, count = 1, last = now - 9_000L),
            entry("used-recent", label = "u2", install = 1L, count = 1, last = now - 1_000L),
            entry("never-a", label = "a"),
        )
        assertEquals(
            listOf("used-recent", "used-old", "never-a", "never-b"),
            apps.orderForEmptyState(now).map { it.packageName },
        )
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<AppEntry>(), emptyList<AppEntry>().orderForEmptyState(now))
    }

    @Test
    fun `label is stable tiebreak within same recency`() {
        val apps = listOf(
            entry("z", label = "zzz", install = 1L, last = 500L),
            entry("a", label = "aaa", install = 1L, last = 500L),
        )
        assertEquals(listOf("a", "z"), apps.orderForEmptyState(now).map { it.packageName })
    }
}
