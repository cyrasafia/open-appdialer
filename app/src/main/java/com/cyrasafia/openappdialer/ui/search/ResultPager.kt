package com.cyrasafia.openappdialer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppIconCache
import com.cyrasafia.openappdialer.search.MatchedApp

private const val ITEMS_PER_PAGE = 3
private const val MAX_PAGES = 2

/**
 * 结果分页（spec F4）：HorizontalPager，每页 3 项、最多 2 页、圆点页标；
 * 输入变化回到第 1 页；匹配 >6 时第 2 页末行提示"还有 N 个"。
 */
@Composable
fun ResultPager(
    items: List<AppEntry>,
    totalMatches: Int,
    resetKey: String,
    iconCache: AppIconCache,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = if (items.isEmpty()) 1 else ((items.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE).coerceAtMost(MAX_PAGES)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // 输入变化（含清空）→ 回到第 1 页
    LaunchedEffect(resetKey) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val pageItems = items.drop(page * ITEMS_PER_PAGE).take(ITEMS_PER_PAGE)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pageItems.forEach { entry ->
                    ResultRow(entry = entry, iconCache = iconCache, onLaunch = onLaunch)
                }
                if (page == pageCount - 1 && totalMatches > items.size && items.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.more_matches_fmt, totalMatches - items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
        // 圆点页标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
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

/** 结果行（spec F4）：图标 + label，行高 ≥ 64dp；匹配高亮排期到 M4 */
@Composable
private fun ResultRow(
    entry: AppEntry,
    iconCache: AppIconCache,
    onLaunch: (AppEntry) -> Unit,
) {
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, entry.packageName) {
        value = iconCache.icon(entry.packageName)
    }
    Surface(
        onClick = { onLaunch(entry) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bmp = bitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {}
            }
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
