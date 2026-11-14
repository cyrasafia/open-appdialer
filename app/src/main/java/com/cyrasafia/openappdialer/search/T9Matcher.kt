package com.cyrasafia.openappdialer.search

/**
 * 匹配结果（spec F2 匹配规则）：
 * - level：L1=1 全拼前缀/全文 < L2=2 首字母前缀/全文 < L3=3 混合前缀 < L4=4 子串
 * - exact：查询与某序列完整相等
 * - position：匹配起始位置（L1/L2/L4 为键串下标；L3 为起始单元下标）
 */
data class MatchResult(val level: Int, val exact: Boolean, val position: Int) {
    companion object {
        const val L1 = 1
        const val L2 = 2
        const val L3 = 3
        const val L4 = 4
    }
}

object T9Matcher {

    /**
     * 查询 Q 对单个应用索引的匹配。
     * @param query 非空，且仅含 '2'..'9'（键盘只产生这些字符）
     */
    fun match(query: String, index: LabelIndex): MatchResult? {
        require(query.isNotEmpty()) { "query must not be empty" }
        if (query.any { !KeyMap.isQueryKey(it) }) return null

        var best: MatchResult? = null

        fun consider(candidate: MatchResult) {
            val cur = best
            if (cur == null || compare(candidate, cur) < 0) best = candidate
        }

        for (v in index.variants) {
            if (v.full == query) {
                consider(MatchResult(MatchResult.L1, exact = true, position = 0))
            } else if (v.full.startsWith(query)) {
                consider(MatchResult(MatchResult.L1, exact = false, position = 0))
            }
            if (v.initials == query) {
                consider(MatchResult(MatchResult.L2, exact = true, position = 0))
            } else if (v.initials.startsWith(query)) {
                consider(MatchResult(MatchResult.L2, exact = false, position = 0))
            }
            v.full.indexOf(query).takeIf { it >= 0 }?.let {
                consider(MatchResult(MatchResult.L4, exact = false, position = it))
            }
            v.initials.indexOf(query).takeIf { it >= 0 }?.let {
                consider(MatchResult(MatchResult.L4, exact = false, position = it))
            }
        }

        matchL3(query, index.units)?.let { start ->
            consider(MatchResult(MatchResult.L3, exact = false, position = start))
        }

        return best
    }

    /** 层级 asc → exact 优先 → position asc */
    private fun compare(a: MatchResult, b: MatchResult): Int {
        if (a.level != b.level) return a.level - b.level
        if (a.exact != b.exact) return if (a.exact) -1 else 1
        return a.position - b.position
    }

    /**
     * L3 混合输入（spec F2）：逐音节自由组合"全拼/首字母"，从任一单元边界开始连续消费整个查询。
     * 字母数字串可消费任意非空前缀；汉字消费某读音全拼或其首字母；特殊字符消费键 9。
     * @return 最小起始单元下标；不匹配返回 null
     */
    private fun matchL3(query: String, units: List<LabelIndex.SearchUnit>): Int? {
        for (start in units.indices) {
            if (canConsume(units, start, query)) return start
        }
        return null
    }

    private fun canConsume(units: List<LabelIndex.SearchUnit>, unitIndex: Int, query: String): Boolean {
        if (query.isEmpty()) return true
        val u = units.getOrNull(unitIndex) ?: return false
        return when (u.kind) {
            0 -> {
                val ks = u.keyString
                for (len in 1..ks.length) {
                    val head = ks.substring(0, len)
                    if (query.startsWith(head) && canConsume(units, unitIndex + 1, query.substring(head.length))) {
                        return true
                    }
                }
                false
            }

            1 -> u.readings.any { r ->
                val fullKeys = KeyMap.keysOfPinyin(r)
                val initKey = fullKeys.take(1)
                (query.startsWith(fullKeys) && canConsume(units, unitIndex + 1, query.substring(fullKeys.length))) ||
                    (query.startsWith(initKey) && canConsume(units, unitIndex + 1, query.substring(initKey.length)))
            }

            else -> query.startsWith("9") && canConsume(units, unitIndex + 1, query.substring(1))
        }
    }
}
