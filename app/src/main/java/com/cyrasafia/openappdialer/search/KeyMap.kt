package com.cyrasafia.openappdialer.search

/**
 * T9 键位映射（spec F2 第 4 步）。
 * 键位字母表即查询字母表：'2'..'9'。
 * - 字母 → 所属键（2=ABC … 9=WXYZ，ü 归一化为 v → 键 8）
 * - 数字 2-9 → 自身键位
 * - 数字 1 → 键 2；数字 0 与特殊字符 → 键 9
 */
object KeyMap {

    private val LETTER_TO_KEY: Map<Char, Char> = buildMap {
        val layout = mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
            '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
        )
        for ((key, letters) in layout) {
            for (c in letters) put(c, key)
        }
        put('v', '8') // ü 归一化为 v
    }

    fun keyOfLetter(c: Char): Char? = LETTER_TO_KEY[c]

    /** 数字字符 → 键（'1'→'2'，'0'→'9'，'2'..'9'→自身） */
    fun keyOfDigit(d: Char): Char = when (d) {
        '1' -> '2'
        '0' -> '9'
        in '2'..'9' -> d
        else -> throw IllegalArgumentException("not a digit: $d")
    }

    fun isQueryKey(c: Char): Boolean = c in '2'..'9'

    /** 拼音归一化：小写、ü→v、丢弃非 a-z 字符（TinyPinyin 输出大写，可能含 '^' 等注音符号） */
    fun normalizePinyin(pinyin: String): String = buildString {
        for (c in pinyin) {
            when (val lc = c.lowercaseChar()) {
                'ü' -> append('v')
                in 'a'..'z' -> append(lc)
            }
        }
    }

    /** 归一化后的拼音串 → 键串 */
    fun keysOfPinyin(pinyin: String): String = buildString {
        for (c in pinyin) append(LETTER_TO_KEY[c] ?: '9')
    }
}
