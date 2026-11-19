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
    private const val KEY_QUALITY = "audio_quality"

    private var prefs: SharedPreferences? = null

    var themeMode by mutableStateOf(ThemeMode.System)
        private set

    var audioQuality by mutableStateOf(AudioQuality.High)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        themeMode = ThemeMode.entries.firstOrNull { it.name == p.getString(KEY_THEME, null) }
            ?: ThemeMode.System
        audioQuality = AudioQuality.entries.firstOrNull { it.name == p.getString(KEY_QUALITY, null) }
            ?: AudioQuality.High
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
    }

    fun updateAudioQuality(quality: AudioQuality) {
        audioQuality = quality
        prefs?.edit()?.putString(KEY_QUALITY, quality.name)?.apply()
    }
}