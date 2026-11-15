package com.cyrasafia.openappdialer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppOrdering.orderForEmptyState
import com.cyrasafia.openappdialer.data.AppRepository
import com.cyrasafia.openappdialer.search.MatchResult
import com.cyrasafia.openappdialer.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * 搜索状态机（spec F4）：
 * - 空输入 → 空态列表（新安装 > 最近使用降序），取前 6（2 页）
 * - 有输入 → SearchEngine 实时匹配，最多展示 6 个 + 总匹配数（"还有 N 个"）
 */
class SearchViewModel(private val repository: AppRepository) : ViewModel() {

    /** 查询串：仅含 '2'..'9'（键盘唯一输入来源） */
    val query = MutableStateFlow("")
    val queryState: StateFlow<String> = query.asStateFlow()

    /** 主动清空标记（spec v2.4）：清空后候选区显示空白，不恢复默认列表 */
    private val cleared = MutableStateFlow(false)

    /** 启动冻结（spec v2.5）：点击候选后冻结列表，防止 recordLaunch 触发重排导致点击项跳位 */
    private val frozen = MutableStateFlow(false)

    private var lastState: SearchUiState = SearchUiState.Blank

    val uiState: StateFlow<SearchUiState> =
        combine(query, cleared, repository.apps, frozen) { q, wasCleared, apps, isFrozen ->
                if (isFrozen) return@combine lastState
                when {
                    q.isNotEmpty() -> {
                        val matched = SearchEngine.search(apps, q)
                        SearchUiState.Results(
                            query = q,
                            items = matched.take(MAX_SHOWN),
                            totalMatches = matched.size,
                            bestLevel = matched.firstOrNull()?.match?.level,
                        )
                    }

                    wasCleared -> SearchUiState.Blank

                    else -> SearchUiState.Empty(
                        items = apps.orderForEmptyState(System.currentTimeMillis()).take(MAX_SHOWN),
                    )
                }.also { lastState = it }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Blank)

    /** 数字键上屏（spec F3：ACTION_DOWN 触发，防丢字符） */
    fun onDigit(digit: Char) {
        if (digit in '2'..'9' && query.value.length < MAX_QUERY_LEN) {
            cleared.value = false
            query.value += digit
        }
    }

    /** 左上键短按：清空全部输入，候选区置空（spec v2.4） */
    fun onClear() {
        query.value = ""
        cleared.value = true
    }

    /** 面板每次唤起重置为全新会话（spec v3.2）：清空输入、恢复空态默认列表 */
    fun resetSession() {
        query.value = ""
        cleared.value = false
    }

    /** 点击候选启动前调用（spec v2.5）：冻结列表防止跳位 */
    fun onLaunching() {
        frozen.value = true
    }

    companion object {
        const val MAX_SHOWN = 6 // 每页 3 × 最多 2 页（spec F4）
        private const val MAX_QUERY_LEN = 24
    }
}

sealed interface SearchUiState {
    /** 主动清空后的空白态（spec v2.4） */
    data object Blank : SearchUiState

    /** 空输入：常用/新装应用 */
    data class Empty(val items: List<AppEntry>) : SearchUiState

    /** 有输入的匹配结果；items 为空即无结果态 */
    data class Results(
        val query: String,
        val items: List<com.cyrasafia.openappdialer.search.MatchedApp>,
        val totalMatches: Int,
        val bestLevel: Int?,
    ) : SearchUiState
}
