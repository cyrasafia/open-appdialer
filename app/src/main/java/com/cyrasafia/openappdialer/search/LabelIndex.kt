package com.cyrasafia.openappdialer.search

import kotlinx.serialization.Serializable

/**
 * 应用名预计算索引（spec F2）。
 * 安装/更新时对 label 计算一次，随 apps.json 持久化。
 */
@Serializable
data class LabelIndex(
    /** (全拼, 首字母) 键串变体：多音字乘积（封顶）× 分隔符折叠/删除两种处理 */
    val variants: List<KeyVariant> = emptyList(),
    /** 分隔符折叠为键 9 后的单元序列，供 L3 混合输入 DP 匹配 */
    val units: List<SearchUnit> = emptyList(),
) {
    @Serializable
    data class KeyVariant(val full: String, val initials: String)

    @Serializable
    data class SearchUnit(
        val kind: Int, // 0=字母数字串, 1=汉字, 2=特殊字符(键 9)
        val keyString: String = "", // kind 0：整串字符映射后的键串
        val readings: List<String> = emptyList(), // kind 1：归一化后的全部读音（常见读音在前）
    )

    companion object {
        val EMPTY = LabelIndex()
    }
}
