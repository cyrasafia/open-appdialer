package com.cyrasafia.openappdialer.ui.search

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.ui.common.pressScale

private val KEY_HEIGHT = 68.dp

/**
 * T9 键盘（spec F3/v2.4）：3×3，无第四行，键面无背景色，按压有涟漪 + 缩放动画。
 * 数字键在 ACTION_DOWN 即上屏（手指移出再抬起也不丢字符），按下即触觉反馈、无声；
 * 左上键短按清空全部输入、长按进入设置。
 */
@Composable
fun Keypad(
    onDigit: (Char) -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            UtilityKey(onClear = onClear, onSettings = onSettings)
            DigitKey(emits = '2', face = "12", letters = "ABC", onDigit = onDigit)
            DigitKey(emits = '3', face = "3", letters = "DEF", onDigit = onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DigitKey(emits = '4', face = "4", letters = "GHI", onDigit = onDigit)
            DigitKey(emits = '5', face = "5", letters = "JKL", onDigit = onDigit)
            DigitKey(emits = '6', face = "6", letters = "MNO", onDigit = onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DigitKey(emits = '7', face = "7", letters = "PQRS", onDigit = onDigit)
            DigitKey(emits = '8', face = "8", letters = "TUV", onDigit = onDigit)
            DigitKey(emits = '9', face = "90", letters = "WXYZ", onDigit = onDigit)
        }
    }
}

/**
 * 数字键：ACTION_DOWN 上屏 + 即时触觉（spec F3 核心需求：防丢字符）。
 * face 为键面展示：合并键并排显示（"12" = 数字 1 与 2 共键，"90" = 0 与 9 共键），
 * emits 为上屏的键位符号（T9 查询字母表 '2'..'9'）。
 * 涟漪由手动 InteractionSource 驱动（DOWN → Press；UP/取消 → Release/Cancel）。
 */
@Composable
private fun RowScope.DigitKey(emits: Char, face: String, letters: String, onDigit: (Char) -> Unit) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }

    PressableKey(
        interactionSource = interaction,
        modifier = Modifier
            .weight(1f)
            .pressScale(interaction)
            .pointerInput(emits) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interaction.tryEmit(press) // 受限协程域内只能用非挂起 API
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDigit(emits) // 上屏在 DOWN 完成，后续手势不影响
                    val up = waitForUpOrCancellation()
                    interaction.tryEmit(
                        if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                }
            }
            .semantics { contentDescription = "$face, $letters" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (face.length == 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = face[0].toString(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = face[1].toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            } else {
                Text(text = face, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = letters,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 左上键（拨号盘 1 位）：与其他键同款上下布局——上为清空、下为设置；短按清空全部，长按进设置 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.UtilityKey(onClear: () -> Unit, onSettings: () -> Unit) {
    val clearDesc = stringResource(R.string.key_clear)
    val interaction = remember { MutableInteractionSource() }

    PressableKey(
        interactionSource = interaction,
        modifier = Modifier
            .weight(1f)
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClear,
                onLongClick = onSettings,
                interactionSource = interaction,
                indication = null, // 涟漪统一由 PressableKey 的 indication 提供
            )
            .semantics { contentDescription = clearDesc },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 键容器：无背景色，仅圆角裁剪 + 涟漪（spec v2.4） */
@Composable
private fun PressableKey(
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(KEY_HEIGHT)
            .clip(MaterialTheme.shapes.large)
            .indication(interactionSource, ripple()),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
