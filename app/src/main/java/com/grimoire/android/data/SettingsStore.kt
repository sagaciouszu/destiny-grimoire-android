package com.grimoire.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
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

enum class ReadingTheme { DEFAULT, SEPIA, AMBER }

enum class FontFamily { SANS_SERIF, SERIF, MONOSPACE }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val readingTheme: ReadingTheme = ReadingTheme.DEFAULT,
    val fontFamily: FontFamily = FontFamily.SANS_SERIF,
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
    private val KEY_READING_THEME = stringPreferencesKey("reading_theme")
    private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
    private val KEY_SIZE = stringPreferencesKey("reading_size")
    private val KEY_SPACING = stringPreferencesKey("line_spacing")
    private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks")

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
                    dynamicColor = prefs[KEY_DYNAMIC] ?: false,
                    readingTheme = prefs[KEY_READING_THEME]?.let { runCatching { ReadingTheme.valueOf(it) }.getOrNull() }
                        ?: ReadingTheme.DEFAULT,
                    fontFamily = prefs[KEY_FONT_FAMILY]?.let { runCatching { FontFamily.valueOf(it) }.getOrNull() }
                        ?: FontFamily.SANS_SERIF,
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

    suspend fun setReadingTheme(context: Context, theme: ReadingTheme) =
        context.dataStore.edit { it[KEY_READING_THEME] = theme.name }

    suspend fun setFontFamily(context: Context, family: FontFamily) =
        context.dataStore.edit { it[KEY_FONT_FAMILY] = family.name }

    suspend fun setReadingSize(context: Context, size: ReadingSize) =
        context.dataStore.edit { it[KEY_SIZE] = size.name }

    suspend fun setLineSpacing(context: Context, spacing: LineSpacing) =
        context.dataStore.edit { it[KEY_SPACING] = spacing.name }

    suspend fun loadBookmarks(context: Context): Set<String> {
        return runCatching {
            val prefs = context.dataStore.data.first()
            val json = prefs[KEY_BOOKMARKS] ?: "[]"
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set.toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun saveBookmarks(context: Context, bookmarks: Set<String>) {
        val arr = JSONArray()
        for (key in bookmarks) {
            arr.put(key)
        }
        context.dataStore.edit { it[KEY_BOOKMARKS] = arr.toString() }
    }
}
