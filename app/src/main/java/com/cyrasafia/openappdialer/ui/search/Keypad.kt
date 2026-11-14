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
            DigitKey('2', "ABC 1", onDigit)
            DigitKey('3', "DEF", onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DigitKey('4', "GHI", onDigit)
            DigitKey('5', "JKL", onDigit)
            DigitKey('6', "MNO", onDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DigitKey('7', "PQRS", onDigit)
            DigitKey('8', "TUV", onDigit)
            DigitKey('9', "0 WXYZ", onDigit)
        }
    }
}

/** 数字键：ACTION_DOWN 上屏 + 即时触觉（spec F3 核心需求：防丢字符） */
@Composable
private fun RowScope.DigitKey(digit: Char, letters: String, onDigit: (Char) -> Unit) {
    val view = LocalView.current
    KeySurface(
        modifier = Modifier
            .weight(1f)
            .pointerInput(digit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onDigit(digit)
                }
            }
            .semantics { contentDescription = "$digit, $letters" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = digit.toString(), style = MaterialTheme.typography.titleLarge)
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
