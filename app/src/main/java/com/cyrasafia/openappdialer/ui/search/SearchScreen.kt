package com.cyrasafia.openappdialer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppIconCache

/**
 * 主搜索界面（spec F3/F4）：查询栏 + 分页结果 + 键盘。
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    iconCache: AppIconCache,
    onLaunch: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.queryState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier
            .widthIn(max = 420.dp)
            .heightIn(max = 640.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QueryBar(query = query, totalMatches = (uiState as? SearchUiState.Results)?.totalMatches)

            when (val state = uiState) {
                is SearchUiState.Results -> {
                    if (state.items.isEmpty()) {
                        NoResults(
                            onClear = viewModel::onClear,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        )
                    } else {
                        ResultPager(
                            items = state.items.map { it.entry },
                            totalMatches = state.totalMatches,
                            resetKey = state.query,
                            iconCache = iconCache,
                            onLaunch = onLaunch,
                        )
                    }
                }

                is SearchUiState.Empty -> ResultPager(
                    items = state.items,
                    totalMatches = state.items.size,
                    resetKey = "",
                    iconCache = iconCache,
                    onLaunch = onLaunch,
                )
            }

            Spacer(Modifier.height(0.dp))
            Keypad(
                onDigit = viewModel::onDigit,
                onClear = viewModel::onClear,
                onSettings = onOpenSettings,
            )
        }
    }
}

/** 查询展示栏：数字串大字展示（非 EditText，绝不唤起系统输入法） */
@Composable
private fun QueryBar(query: String, totalMatches: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = query,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            if (totalMatches != null) {
                Text(
                    text = stringResource(R.string.match_count_fmt, totalMatches),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 无结果态（spec F4）：提示 + 清空按钮 */
@Composable
private fun NoResults(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.clear))
        }
    }
}
