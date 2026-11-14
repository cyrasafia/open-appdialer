package com.cyrasafia.openappdialer.search

/**
 * label → LabelIndex（spec F2 序列生成）。
 * 归一化 → 解析为单元（字母数字串/汉字/特殊字符）→ 生成键串变体（全拼/首字母 × 多音字 × 分隔符两种处理）。
 */
object LabelIndexer {

    const val MAX_VARIANTS = 32

    private const val KIND_RUN = 0
    private const val KIND_CHINESE = 1
    private const val KIND_SYMBOL = 2

    private val SYMBOL_UNIT = LabelIndex.SearchUnit(kind = KIND_SYMBOL)

    fun index(label: String, source: PinyinSource): LabelIndex {
        val folded = parseUnits(label, source, foldSeparators = true)
        val dropped = parseUnits(label, source, foldSeparators = false)
        val variants = (generateVariants(folded) + generateVariants(dropped))
            .filter { it.full.isNotEmpty() }
            .distinct()
            .take(MAX_VARIANTS)
        return LabelIndex(variants = variants, units = folded)
    }

    // ---- 解析 ----

    private fun parseUnits(
        label: String,
        source: PinyinSource,
        foldSeparators: Boolean,
    ): List<LabelIndex.SearchUnit> {
        val units = ArrayList<LabelIndex.SearchUnit>()
        val run = StringBuilder()

        fun flushRun() {
            if (run.isNotEmpty()) {
                units += runUnit(run.toString())
                run.clear()
            }
        }

        for (c in label.lowercase()) {
            when {
                isChinese(c) -> {
                    flushRun()
                    val readings = source.readings(c)
                    if (readings.isEmpty()) {
                        units += SYMBOL_UNIT // 有读音数据的 CJK 之外（生僻字等）按特殊字符处理
                    } else {
                        units += LabelIndex.SearchUnit(kind = KIND_CHINESE, readings = readings)
                    }
                }

                isRunChar(c) -> run.append(c)

                else -> {
                    flushRun()
                    if (foldSeparators) units += SYMBOL_UNIT
                }
            }
        }
        flushRun()
        return units
    }

    private fun isChinese(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF'

    /** 参与字母数字串的字符：a-z（含 ü→v 归一）与数字 */
    private fun isRunChar(c: Char): Boolean =
        c in 'a'..'z' || c == 'ü' || c in '0'..'9'

    private fun runUnit(run: String): LabelIndex.SearchUnit {
        val keys = buildString {
            for (c in run) {
                when {
                    c == 'ü' -> append('8') // v → 键 8
                    c.isDigit() -> append(KeyMap.keyOfDigit(c))
                    else -> append(KeyMap.keyOfLetter(c) ?: '9')
                }
            }
        }
        return LabelIndex.SearchUnit(kind = KIND_RUN, keyString = keys)
    }

    // ---- 变体生成 ----

    private fun generateVariants(units: List<LabelIndex.SearchUnit>): List<LabelIndex.KeyVariant> {
        var states = listOf("" to "") // (full, initials)
        for (u in units) {
            states = when (u.kind) {
                KIND_RUN -> states.map { (f, i) -> f + u.keyString to i + u.keyString }

                KIND_CHINESE -> {
                    val next = ArrayList<Pair<String, String>>(states.size * u.readings.size)
                    for ((f, i) in states) {
                        for (r in u.readings) {
                            next += (f + KeyMap.keysOfPinyin(r)) to (i + KeyMap.keysOfPinyin(r.take(1)))
                        }
                    }
                    next
                }

                else -> states.map { (f, i) -> f + "9" to i + "9" }
            }
            if (states.size > MAX_VARIANTS) states = states.take(MAX_VARIANTS)
        }
        return states.map { (f, i) -> LabelIndex.KeyVariant(full = f, initials = i) }.distinct()
    }
}
