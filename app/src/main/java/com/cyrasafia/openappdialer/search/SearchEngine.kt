package com.cyrasafia.openappdialer.search

import com.cyrasafia.openappdialer.data.AppEntry

data class MatchedApp(val entry: AppEntry, val match: MatchResult)

/**
 * 搜索入口（spec F2 排序，确定性输出）：
 * 层级 → exact → 匹配位置 → 启动次数 → 最近使用 → label → 包名（全序，无不稳定因素）
 */
object SearchEngine {

    fun search(entries: List<AppEntry>, query: String): List<MatchedApp> {
        if (query.isEmpty()) return emptyList()
        return entries.asSequence()
            .mapNotNull { entry ->
                T9Matcher.match(query, entry.index, entry.packageKeys)?.let { MatchedApp(entry, it) }
            }
            .sortedWith(
                compareBy<MatchedApp> { it.match.level }
                    .thenByDescending { it.match.exact }
                    .thenBy { it.match.position }
                    .thenByDescending { it.entry.launchCount }
                    .thenByDescending { it.entry.lastLaunchAt }
                    .thenBy { it.entry.label }
                    .thenBy { it.entry.packageName },
            )
            .toList()
    }
}
