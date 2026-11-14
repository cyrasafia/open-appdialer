package com.cyrasafia.openappdialer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cyrasafia.openappdialer.data.ThemeMode

// 预置色板（靛蓝主色，spec F5：12+ 默认 Material You 动态取色，低版本回退）
private val LightColors = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F1FA),
    surfaceContainer = Color(0xFFEEEBF6),
    surfaceContainerHigh = Color(0xFFE8E6F0),
    surfaceContainerHighest = Color(0xFFE2E0EA),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF46464F),
    outlineVariant = Color(0xFFC7C5D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF0B1C7A),
    surfaceContainerLow = Color(0xFF1A1B22),
    surfaceContainer = Color(0xFF1F2027),
    surfaceContainerHigh = Color(0xFF24262D),
    surfaceContainerHighest = Color(0xFF2A2C34),
    onSurface = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outlineVariant = Color(0xFF47464F),
)

/** 主题（spec F5）：跟随系统 / 手动覆盖；Android 12+ 动态取色 */
@Composable
fun OpenAppDialerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
