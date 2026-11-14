package com.cyrasafia.openappdialer.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** 按压缩放动画（spec v2.4）：按压时轻微缩小，松开回弹 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
