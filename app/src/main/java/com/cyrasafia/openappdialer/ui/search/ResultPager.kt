package com.cyrasafia.openappdialer.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.AppEntry
import com.cyrasafia.openappdialer.data.AppIconCache
import com.cyrasafia.openappdialer.ui.common.pressScale

private const val ITEMS_PER_PAGE = 3
private const val MAX_PAGES = 2

/** 单页高度（图标 + 名称单行），所有状态恒定（spec v2.0：浮窗高度不随结果变化） */
val PAGE_HEIGHT = 100.dp

/**
 * 结果分页（spec F4/v2.0）：HorizontalPager，每页 1 行 3 个候选（图标上、名称下）、
 * 最多 2 页、圆点页标；输入变化回到第 1 页；匹配 >6 时第 2 页底部提示"还有 N 个"。
 */
@Composable
fun ResultPager(
    items: List<AppEntry>,
    resetKey: String,
    iconCache: AppIconCache,
    canPinShortcut: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onAppAction: (AppEntry, AppAction) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                pageItems.forEach { entry ->
                    ResultItem(
                        entry = entry,
                        iconCache = iconCache,
                        canPinShortcut = canPinShortcut,
                        onLaunch = onLaunch,
                        onAppAction = onAppAction,
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

/** 候选项（spec v2.4/v2.7）：图标上、名称下（紧凑字体、单行省略），无背景色，按压缩放；长按弹出操作菜单 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultItem(
    entry: AppEntry,
    iconCache: AppIconCache,
    canPinShortcut: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onAppAction: (AppEntry, AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState(initialValue = null as android.graphics.Bitmap?, entry.packageName) {
        value = iconCache.icon(entry.packageName)
    }
    val interaction = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = modifier
            .pressScale(interaction)
            .height(PAGE_HEIGHT),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = { onLaunch(entry) },
                    onLongClick = { menuExpanded = true },
                ),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 6.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp), // 与系统启动器图标相当（spec v2.5）
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {}
                }
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            // 长按操作菜单（spec v2.7）：快捷方式 / 详情 / 卸载
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (canPinShortcut) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_pin_shortcut)) },
                        onClick = {
                            menuExpanded = false
                            onAppAction(entry, AppAction.PIN_SHORTCUT)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_app_details)) },
                    onClick = {
                        menuExpanded = false
                        onAppAction(entry, AppAction.DETAILS)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_uninstall)) },
                    onClick = {
                        menuExpanded = false
                        onAppAction(entry, AppAction.UNINSTALL)
                    },
                )
            }
        }
    }
}
