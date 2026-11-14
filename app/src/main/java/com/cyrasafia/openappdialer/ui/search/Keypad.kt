package com.cyrasafia.openappdialer.ui.search

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.R

private val KEY_HEIGHT = 68.dp

/**
 * T9 键盘（spec F3）：3×3，无第四行。
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
 */
@Composable
private fun RowScope.DigitKey(emits: Char, face: String, letters: String, onDigit: (Char) -> Unit) {
    val view = LocalView.current
    KeySurface(
        modifier = Modifier
            .weight(1f)
            .pointerInput(emits) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDigit(emits)
                }
            }
            .semantics { contentDescription = "$face, $letters" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (face.length == 2) {
                // 并排双数字（1 和 2、9 和 0）
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

/** 左上键（拨号盘 1 位）：短按清空全部，长按进设置 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.UtilityKey(onClear: () -> Unit, onSettings: () -> Unit) {
    val clearDesc = stringResource(R.string.key_clear)
    KeySurface(
        modifier = Modifier
            .weight(1f)
            .combinedClickable(
                onClick = onClear,
                onLongClick = onSettings,
            )
            .semantics { contentDescription = clearDesc },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun KeySurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.height(KEY_HEIGHT),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
