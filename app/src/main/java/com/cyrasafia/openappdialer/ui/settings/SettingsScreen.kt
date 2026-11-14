package com.cyrasafia.openappdialer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyrasafia.openappdialer.BuildConfig
import com.cyrasafia.openappdialer.R
import com.cyrasafia.openappdialer.data.ThemeMode

/**
 * 设置页（spec F6）：仅主题三选一 + 关于（版本/许可证/仓库）。
 * 入口：键盘左上键长按。
 */
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    onOpenShortcutPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 480.dp)
            .heightIn(max = 640.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = stringResource(R.string.theme_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            ThemeOption(
                label = stringResource(R.string.theme_system),
                selected = currentTheme == ThemeMode.SYSTEM,
                onClick = { onSelectTheme(ThemeMode.SYSTEM) },
            )
            ThemeOption(
                label = stringResource(R.string.theme_light),
                selected = currentTheme == ThemeMode.LIGHT,
                onClick = { onSelectTheme(ThemeMode.LIGHT) },
            )
            ThemeOption(
                label = stringResource(R.string.theme_dark),
                selected = currentTheme == ThemeMode.DARK,
                onClick = { onSelectTheme(ThemeMode.DARK) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // MIUI 私有"创建桌面快捷方式"权限入口（spec v2.8）：未授权时 requestPinShortcut 静默失败
            TextButton(onClick = onOpenShortcutPermission) {
                Text(stringResource(R.string.shortcut_permission_entry))
            }

            Text(
                text = stringResource(R.string.about_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            AboutRow(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME))
            AboutRow(stringResource(R.string.license_label))
            AboutRow(stringResource(R.string.repo_url))
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun AboutRow(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
