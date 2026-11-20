package com.aghielaan22.label

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

enum class AudioQuality(val label: String) {
    High("High"),
    Standard("Standard"),
    DataSaver("Data saver")
}

object AppSettings {

    private const val PREFS_NAME = "label_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_QUALITY = "audio_quality"
    private const val KEY_AUTOPLAY = "autoplay"

    private var prefs: SharedPreferences? = null

    var themeMode by mutableStateOf(ThemeMode.System)
        private set

    var appTheme by mutableStateOf(AppTheme.Dynamic)
        private set

    var audioQuality by mutableStateOf(AudioQuality.High)
        private set

    var autoplay by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        themeMode = ThemeMode.entries.firstOrNull { it.name == p.getString(KEY_THEME, null) }
            ?: ThemeMode.System
        appTheme = AppTheme.entries.firstOrNull { it.name == p.getString(KEY_APP_THEME, null) }
            ?: AppTheme.Dynamic
        audioQuality = AudioQuality.entries.firstOrNull { it.name == p.getString(KEY_QUALITY, null) }
            ?: AudioQuality.High
        autoplay = p.getBoolean(KEY_AUTOPLAY, true)
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
    }

    fun updateAppTheme(theme: AppTheme) {
        appTheme = theme
        prefs?.edit()?.putString(KEY_APP_THEME, theme.name)?.apply()
    }

    fun updateAudioQuality(quality: AudioQuality) {
        audioQuality = quality
        prefs?.edit()?.putString(KEY_QUALITY, quality.name)?.apply()
    }

    fun updateAutoplay(enabled: Boolean) {
        autoplay = enabled
        prefs?.edit()?.putBoolean(KEY_AUTOPLAY, enabled)?.apply()
    }
}