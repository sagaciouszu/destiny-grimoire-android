package com.grimoire.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReadingSize(val label: String, val scale: Float) {
    SMALL("Small", 0.85f),
    DEFAULT("Default", 1.0f),
    LARGE("Large", 1.18f),
    EXTRA_LARGE("Extra large", 1.38f),
}

enum class LineSpacing(val label: String, val factor: Float) {
    COMPACT("Compact", 1.25f),
    DEFAULT("Default", 1.45f),
    RELAXED("Relaxed", 1.65f),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val readingSize: ReadingSize = ReadingSize.DEFAULT,
    val lineSpacing: LineSpacing = LineSpacing.DEFAULT,
) {
    val fontScale: Float get() = readingSize.scale
    val lineHeightFactor: Float get() = lineSpacing.factor
}

object SettingsStore {

    private val Context.dataStore by preferencesDataStore(name = "settings")

    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
    private val KEY_SIZE = stringPreferencesKey("reading_size")
    private val KEY_SPACING = stringPreferencesKey("line_spacing")

    fun settingsFlow(context: Context): Flow<AppSettings> =
        context.dataStore.data
            .catch { e ->
                if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
                else throw e
            }
            .map { prefs ->
                AppSettings(
                    themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                        ?: ThemeMode.SYSTEM,
                    dynamicColor = prefs[KEY_DYNAMIC] ?: true,
                    readingSize = prefs[KEY_SIZE]?.let { runCatching { ReadingSize.valueOf(it) }.getOrNull() }
                        ?: ReadingSize.DEFAULT,
                    lineSpacing = prefs[KEY_SPACING]?.let { runCatching { LineSpacing.valueOf(it) }.getOrNull() }
                        ?: LineSpacing.DEFAULT,
                )
            }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) =
        context.dataStore.edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) =
        context.dataStore.edit { it[KEY_DYNAMIC] = enabled }

    suspend fun setReadingSize(context: Context, size: ReadingSize) =
        context.dataStore.edit { it[KEY_SIZE] = size.name }

    suspend fun setLineSpacing(context: Context, spacing: LineSpacing) =
        context.dataStore.edit { it[KEY_SPACING] = spacing.name }
}
