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

    val uiState: StateFlow<SearchUiState> =
        combine(query, repository.apps) { q, apps ->
                when {
                    q.isEmpty() -> SearchUiState.Empty(
                        items = apps.orderForEmptyState(System.currentTimeMillis()).take(MAX_SHOWN),
                    )

                    else -> {
                        val matched = SearchEngine.search(apps, q)
                        SearchUiState.Results(
                            query = q,
                            items = matched.take(MAX_SHOWN),
                            totalMatches = matched.size,
                            bestLevel = matched.firstOrNull()?.match?.level,
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Empty(emptyList()))

    /** 数字键上屏（spec F3：ACTION_DOWN 触发，防丢字符） */
    fun onDigit(digit: Char) {
        if (digit in '2'..'9' && query.value.length < MAX_QUERY_LEN) {
            query.value += digit
        }
    }

    /** 左上键短按：清空全部输入（spec v1.2） */
    fun onClear() {
        query.value = ""
    }

    companion object {
        const val MAX_SHOWN = 6 // 每页 3 × 最多 2 页（spec F4）
        private const val MAX_QUERY_LEN = 24
    }
}

sealed interface SearchUiState {
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
