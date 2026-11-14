package com.cyrasafia.openappdialer.data

import kotlinx.serialization.Serializable

/**
 * 应用条目（spec F1）。
 * M2 将新增预计算匹配序列字段（全拼/首字母数字串），schema 随缓存版本演进。
 */
@Serializable
data class AppEntry(
    val packageName: String,
    val label: String,
    /** 首次安装时间 epoch ms（空态"新安装"判定依据，spec Q9：≤2h） */
    val firstInstallTime: Long,
    val launchCount: Int = 0,
    /** 最近一次从本应用启动的时间 epoch ms；0 = 从未使用 */
    val lastLaunchAt: Long = 0L,
)
