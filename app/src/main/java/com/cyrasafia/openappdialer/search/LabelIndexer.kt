package com.cyrasafia.openappdialer.search

/**
 * label → LabelIndex（spec F2 序列生成，v3.5 语义）。
 * - 全拼：拉丁串整串 + 汉字完整读音
 * - 首字母：拉丁串逐词首字符（驼峰/数字边界分词）+ 汉字逐字首字母
 * - 包名键串：包名全部字母数字的键映射（供 L4 包名段匹配）
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

    /** 包名键串（spec v3.5 L4）：全部字母数字的键映射，分隔符忽略 */
    fun packageKeys(pkg: String): String = buildString {
        for (c in pkg) {
            when {
                c.isDigit() -> append(KeyMap.keyOfDigit(c))
                c.lowercaseChar() in 'a'..'z' -> append(KeyMap.keyOfLetter(c.lowercaseChar()) ?: '9')
            }
        }
    }

    // ---- 解析（在原始大小写上分词，键映射用小写） ----

    private fun parseUnits(
        label: String,
        source: PinyinSource,
        foldSeparators: Boolean,
    ): List<LabelIndex.SearchUnit> {
        val units = ArrayList<LabelIndex.SearchUnit>()
        val runChars = StringBuilder() // 原始大小写，用于分词

        fun flushRun() {
            if (runChars.isNotEmpty()) {
                units += runUnit(runChars.toString())
                runChars.clear()
            }
        }

        for (c in label) {
            when {
                isChinese(c) -> {
                    flushRun()
                    val readings = source.readings(c)
                    if (readings.isEmpty()) {
                        units += SYMBOL_UNIT // 无读音的生僻 CJK 按特殊字符处理
                    } else {
                        units += LabelIndex.SearchUnit(kind = KIND_CHINESE, readings = readings)
                    }
                }

                isRunChar(c) -> runChars.append(c)

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

    /** 参与字母数字串的字符（保留原始大小写用于驼峰分词） */
    private fun isRunChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == 'ü' || c == 'Ü'

    /** 字母数字串单元：keyString=整串键（全拼），initialKeys=逐词首字符键（首字母，v3.5） */
    private fun runUnit(run: String): LabelIndex.SearchUnit {
        val keys = StringBuilder()
        val initialKeys = StringBuilder()
        var lastWasLowerLetter = false
        var lastWasDigit = false
        var wordStarted = true

        for (c in run) {
            val lc = c.lowercaseChar()
            val key = when {
                c.isDigit() -> KeyMap.keyOfDigit(c)
                lc == 'ü' -> '8'
                else -> KeyMap.keyOfLetter(lc) ?: '9'
            }
            keys.append(key)

            // 分词边界：串首、大写字母跟在小写后（驼峰）、数字与字母交界
            val isUpperLetter = c in 'A'..'Z'
            val isLetter = c in 'a'..'z' || c in 'A'..'Z' || c == 'ü' || c == 'Ü'
            val isDigit = c.isDigit()
            val boundary = (isUpperLetter && lastWasLowerLetter) ||
                (isLetter && lastWasDigit) ||
                (isDigit && !lastWasDigit)
            if (wordStarted || boundary) {
                initialKeys.append(key)
                wordStarted = false
            }
            lastWasLowerLetter = c in 'a'..'z'
            lastWasDigit = isDigit
        }
        return LabelIndex.SearchUnit(kind = KIND_RUN, keyString = keys.toString(), initialKeys = initialKeys.toString())
    }

    // ---- 变体生成 ----

    private fun generateVariants(units: List<LabelIndex.SearchUnit>): List<LabelIndex.KeyVariant> {
        var states = listOf("" to "") // (full, initials)
        for (u in units) {
            states = when (u.kind) {
                KIND_RUN -> states.map { (f, i) ->
                    f + u.keyString to i + u.initialKeys
                }

                KIND_CHINESE -> {
                    val next = ArrayList<Pair<String, String>>(states.size * u.readings.size)
                    for ((f, i) in states) {
                        for (r in u.readings) {
                            next += (f + KeyMap.keysOfPinyin(r)) to (i + KeyMap.keysOfPinyin(r.take(1)))
                        }
                    }
                    next
                }

                else -> states.map { (f, i) -> f + "9" to i } // 特殊字符计入全拼、不计入首字母
            }
            if (states.size > MAX_VARIANTS) states = states.take(MAX_VARIANTS) // 常见读音组合在前
        }
        return states.map { (f, i) -> LabelIndex.KeyVariant(full = f, initials = i) }.distinct()
    }
}
