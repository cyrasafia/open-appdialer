package com.cyrasafia.openappdialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 预置色板（靛蓝主色）；Material You 动态取色在 M3 主题实现时接入（spec F5）
private val LightColors = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF0B1C7A),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1EC),
)

@Composable
fun OpenAppDialerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
