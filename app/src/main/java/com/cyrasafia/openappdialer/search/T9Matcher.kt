package com.cyrasafia.openappdialer.search

/**
 * 匹配结果（spec F2 匹配规则，v3.5）：
 * - L1=1 名称首字母（中文逐字首字母 + 英文逐词首字母）
 * - L2=2 名称全拼
 * - L3=3 混合输入前缀
 * - L4=4 包名
 * 每段内部：exact 完全匹配 > 前缀(position=0) > 子串(position>0)
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
     * 查询 Q 对单个应用的匹配（spec v3.5：名称首字母 > 全拼 > 混合 > 包名，段内 完全>前缀>子串）。
     * @param query 非空，且仅含 '2'..'9'（键盘只产生这些字符）
     */
    fun match(query: String, index: LabelIndex, packageKeys: String = ""): MatchResult? {
        require(query.isNotEmpty()) { "query must not be empty" }
        if (query.any { !KeyMap.isQueryKey(it) }) return null

        var best: MatchResult? = null

        fun consider(candidate: MatchResult) {
            val cur = best
            if (cur == null || compare(candidate, cur) < 0) best = candidate
        }

        // 段内通用：完全 > 前缀 > 子串
        fun considerString(keys: String, level: Int) {
            when {
                keys == query -> consider(MatchResult(level, exact = true, position = 0))
                keys.startsWith(query) -> consider(MatchResult(level, exact = false, position = 0))
                else -> keys.indexOf(query).takeIf { it >= 0 }?.let {
                    consider(MatchResult(level, exact = false, position = it))
                }
            }
        }

        for (v in index.variants) {
            considerString(v.initials, MatchResult.L1)
            considerString(v.full, MatchResult.L2)
        }

        matchL3(query, index.units)?.let { start ->
            consider(MatchResult(MatchResult.L3, exact = false, position = start))
        }

        if (packageKeys.isNotEmpty()) considerString(packageKeys, MatchResult.L4)

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
