package com.cyrasafia.openappdialer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppIconCache

/** 结果区总高 = 单页高度 + 页标行，各状态一致（spec v2.0：浮窗高度恒定） */
private val RESULT_AREA_HEIGHT = PAGE_HEIGHT + 12.dp

/**
 * 主搜索界面（spec F3/F4/v2.0）：结果分页 + 键盘。
 * 不回显输入数字（v2.0），结果区高度恒定。
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    iconCache: AppIconCache,
    onLaunch: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier
            .widthIn(max = 480.dp)
            .heightIn(max = 640.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 结果区：空态 / 结果 / 无结果 / 主动清空 高度一致
            Box(modifier = Modifier.height(RESULT_AREA_HEIGHT)) {
                when (val state = uiState) {
                    is SearchUiState.Blank -> Unit // 主动清空后：空白（spec v2.4）

                    is SearchUiState.Results -> {
                        if (state.items.isEmpty()) {
                            NoResults(
                                onClear = viewModel::onClear,
                                modifier = Modifier.height(RESULT_AREA_HEIGHT),
                            )
                        } else {
                            ResultPager(
                                items = state.items.map { it.entry },
                                resetKey = state.query,
                                iconCache = iconCache,
                                onLaunch = onLaunch,
                            )
                        }
                    }

                    is SearchUiState.Empty -> ResultPager(
                        items = state.items,
                        resetKey = "",
                        iconCache = iconCache,
                        onLaunch = onLaunch,
                    )
                }
            }

            Keypad(
                onDigit = viewModel::onDigit,
                onClear = viewModel::onClear,
                onSettings = onOpenSettings,
            )
        }
    }
}

/** 无结果态（spec F4）：提示 + 清空按钮 */
@Composable
private fun NoResults(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
