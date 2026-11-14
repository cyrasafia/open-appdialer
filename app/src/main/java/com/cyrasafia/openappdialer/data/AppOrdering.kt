package com.cyrasafia.openappdialer.data

/**
 * 空态排序（spec F4 / Q9）：
 * 1. 新安装优先：firstInstallTime 距 now ≤ 2h，组内按安装时间降序
 * 2. 其余按最近使用时间降序（从未使用 lastLaunchAt=0 自然垫底）
 * 3. 同层内 label 字典序稳定兜底
 */
object AppOrdering {

    /** spec Q9：新安装窗口 2 小时（"刚装好-来找"场景） */
    const val NEW_INSTALL_WINDOW_MS: Long = 2 * 60 * 60 * 1000L

    fun List<AppEntry>.orderForEmptyState(now: Long): List<AppEntry> {
        val (fresh, rest) = partition { age ->
            val delta = now - age.firstInstallTime
            delta in 0 until NEW_INSTALL_WINDOW_MS
        }
        val freshSorted = fresh.sortedWith(
            compareByDescending<AppEntry> { it.firstInstallTime }.thenBy { it.label },
        )
        val restSorted = rest.sortedWith(
            compareByDescending<AppEntry> { it.lastLaunchAt }
                .thenBy { it.label },
        )
        return freshSorted + restSorted
    }
}
