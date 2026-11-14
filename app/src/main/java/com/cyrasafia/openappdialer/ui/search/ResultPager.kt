package com.cyrasafia.openappdialer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppIconCache

private const val ITEMS_PER_PAGE = 3
private const val MAX_PAGES = 2

/** 单页高度（图标 + 名称），所有状态恒定（spec v2.0：浮窗高度不随结果变化） */
val PAGE_HEIGHT = 124.dp

/**
 * 结果分页（spec F4/v2.0）：HorizontalPager，每页 1 行 3 个候选（图标上、名称下）、
 * 最多 2 页、圆点页标；输入变化回到第 1 页；匹配 >6 时第 2 页底部提示"还有 N 个"。
 */
@Composable
fun ResultPager(
    items: List<AppEntry>,
    resetKey: String,
    iconCache: AppIconCache,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount =
        if (items.isEmpty()) 1 else ((items.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE).coerceAtMost(MAX_PAGES)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // 输入变化（含清空）→ 回到第 1 页
    LaunchedEffect(resetKey) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(PAGE_HEIGHT),
        ) { page ->
            val pageItems = items.drop(page * ITEMS_PER_PAGE).take(ITEMS_PER_PAGE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pageItems.forEach { entry ->
                    ResultItem(
                        entry = entry,
                        iconCache = iconCache,
                        onLaunch = onLaunch,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // 圆点页标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val active = pagerState.currentPage == index
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 16.dp, height = 4.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ) {}
            }
        }
    }
}

/** 候选项（spec v2.0）：图标上、名称下，横排 3 列之一 */
@Composable
private fun ResultItem(
    entry: AppEntry,
    iconCache: AppIconCache,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, entry.packageName) {
        value = iconCache.icon(entry.packageName)
    }
    Surface(
        onClick = { onLaunch(entry) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.height(PAGE_HEIGHT),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val bmp = bitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {}
            }
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
