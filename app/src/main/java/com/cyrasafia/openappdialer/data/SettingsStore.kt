package com.cyrasafia.openappdialer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主题模式（spec F5）：跟随系统 / 浅色 / 深色 */
enum class ThemeMode(val stored: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.stored == value } ?: SYSTEM
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 设置存储（spec F6）：当前仅主题 */
class SettingsStore(context: Context) {

    private val dataStore = context.settingsDataStore

    val theme: Flow<ThemeMode> = dataStore.data
        .map { prefs -> ThemeMode.fromStored(prefs[THEME_KEY]) }

    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_KEY] = mode.stored }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme")
    }
}
