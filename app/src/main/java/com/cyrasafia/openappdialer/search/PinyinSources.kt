package com.cyrasafia.openappdialer.search

/** 拼音读音来源（纯 Kotlin 接口，便于测试替换） */
fun interface PinyinSource {
    /** 该汉字的全部读音（KeyMap.normalizePinyin 归一化后）；非汉字/无读音返回空 */
    fun readings(c: Char): List<String>
}

/**
 * TinyPinyin（基础单读音）+ 自建多音字补充表（spec F2：多音字不消歧、全部读音保留）。
 * 常见读音（TinyPinyin 默认）排在首位，多音字乘积封顶时优先保留常见组合。
 */
class DefaultPinyinSource : PinyinSource {

    override fun readings(c: Char): List<String> {
        if (!com.github.promeg.pinyinhelper.Pinyin.isChinese(c)) return emptyList()
        val base = KeyMap.normalizePinyin(com.github.promeg.pinyinhelper.Pinyin.toPinyin(c))
        val extra = PolyphoneTable[c].orEmpty()
        return (listOf(base) + extra).filter { it.isNotEmpty() }.distinct()
    }
}

/**
 * 常见多音字补充表（仅收录字母不同的读音；声调不同字母相同的不再重复）。
 * 自建表，覆盖 app 名场景的高频多音字。
 */
object PolyphoneTable {

    val readings: Map<Char, List<String>> = mapOf(
        '行' to listOf("xing", "hang", "heng"),
        '重' to listOf("zhong", "chong"),
        '长' to listOf("chang", "zhang"),
        '乐' to listOf("le", "yue"),
        '都' to listOf("du", "dou"),
        '传' to listOf("chuan", "zhuan"),
        '参' to listOf("can", "shen", "cen", "san"),
        '藏' to listOf("zang", "cang"),
        '差' to listOf("cha", "ci", "chai", "cuo"),
        '大' to listOf("da", "dai"),
        '得' to listOf("de", "dei"),
        '地' to listOf("de", "di"),
        '的' to listOf("de", "di"),
        '调' to listOf("diao", "tiao"),
        '度' to listOf("du", "duo"),
        '恶' to listOf("e", "wu"),
        '还' to listOf("hai", "huan"),
        '会' to listOf("hui", "kuai"),
        '觉' to listOf("jue", "jiao"),
        '卡' to listOf("ka", "qia"),
        '壳' to listOf("ke", "qiao"),
        '了' to listOf("le", "liao"),
        '脉' to listOf("mai", "mo"),
        '没' to listOf("mei", "mo"),
        '模' to listOf("mo", "mu"),
        '胖' to listOf("pang", "pan"),
        '朴' to listOf("pu", "piao", "po"),
        '强' to listOf("qiang", "jiang"),
        '券' to listOf("quan", "juan"),
        '塞' to listOf("sai", "se"),
        '折' to listOf("zhe", "she"),
        '省' to listOf("sheng", "xing"),
        '数' to listOf("shu", "shuo"),
        '宿' to listOf("su", "xiu"),
        '拓' to listOf("tuo", "ta"),
        '系' to listOf("xi", "ji"),
        '吓' to listOf("xia", "he"),
        '纤' to listOf("xian", "qian"),
        '血' to listOf("xue", "xie"),
        '攒' to listOf("zan", "cuan"),
        '曾' to listOf("zeng", "ceng"),
        '爪' to listOf("zhao", "zhua"),
        '着' to listOf("zhe", "zhao", "zhuo"),
        '提' to listOf("ti", "di"),
        '剥' to listOf("bo", "bao"),
        '泊' to listOf("bo", "po"),
        '曝' to listOf("pu", "bao"),
        '朝' to listOf("chao", "zhao"),
        '称' to listOf("cheng", "chen"),
        '乘' to listOf("cheng", "sheng"),
        '盛' to listOf("cheng", "sheng"),
        '仇' to listOf("chou", "qiu"),
        '臭' to listOf("chou", "xiu"),
        '畜' to listOf("chu", "xu"),
        '石' to listOf("shi", "dan"),
        '弹' to listOf("dan", "tan"),
        '叨' to listOf("dao", "tao"),
        '囤' to listOf("dun", "tun"),
        '露' to listOf("lu", "lou"),
        '落' to listOf("luo", "la", "lao"),
        '咽' to listOf("yan", "ye"),
        '叶' to listOf("ye", "xie"),
        '薄' to listOf("bao", "bo"),
        '削' to listOf("xiao", "xue"),
        '车' to listOf("che", "ju"),
        '单' to listOf("dan", "shan", "chan"),
        '解' to listOf("jie", "xie", "hai"),
        '说' to listOf("shuo", "shui"),
        '巷' to listOf("xiang", "hang"),
        '刹' to listOf("cha", "sha"),
        '似' to listOf("si", "shi"),
        '番' to listOf("fan", "pan", "bo"),
        '龟' to listOf("gui", "qiu"),
        '率' to listOf("lv", "shuai"),
        '绿' to listOf("lv", "lu"),
    )

    operator fun get(c: Char): List<String>? = readings[c]
}
