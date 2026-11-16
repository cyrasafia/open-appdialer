package com.cyrasafia.openappdialer.data

import com.cyrasafia.openappdialer.search.LabelIndex
import kotlinx.serialization.Serializable

/**
 * 应用条目（spec F1）。
 * index 为预计算匹配索引（spec F2，M2 引入，随缓存 schema v2 持久化）。
 */
@Serializable
data class AppEntry(
    val packageName: String,
    /** 启动 Activity 全类名（v3.7：同一包可有多个启动入口，如 com.android.contacts = 联系人+电话） */
    val activityName: String = "",
    val label: String,
    /** 首次安装时间 epoch ms（空态"新安装"判定依据，spec Q9：≤2h） */
    val firstInstallTime: Long,
    val launchCount: Int = 0,
    /** 最近一次从本应用启动的时间 epoch ms；0 = 从未使用 */
    val lastLaunchAt: Long = 0L,
    val index: LabelIndex = LabelIndex.EMPTY,
    /** 包名键串（spec v3.5 L4 包名段匹配） */
    val packageKeys: String = "",
)
