package com.aghielaan22.label

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    private const val KEY_ARTWORK_STYLE = "now_playing_artwork_style"
    private const val KEY_CACHE_LIMIT = "audio_cache_limit_mib"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_MUSIC_GLOW = "music_reactive_glow"
    private const val KEY_GLASS = "glass_enabled"
    private const val KEY_GLASS_TRANSPARENCY = "glass_transparency"
    private const val KEY_BATTERY_SAVER = "battery_saver"

    private var prefs: SharedPreferences? = null

    var themeMode by mutableStateOf(ThemeMode.System)
        private set

    var appTheme by mutableStateOf(AppTheme.Dynamic)
        private set

    var audioQuality by mutableStateOf(AudioQuality.High)
        private set

    var artworkStyle by mutableStateOf(ArtworkStyle.Cover)
        private set

    var audioCacheLimitMiB by mutableStateOf(256)
        private set

    var autoplay by mutableStateOf(true)
        private set

    var musicReactiveGlow by mutableStateOf(false)
        private set

    var glassEnabled by mutableStateOf(true)
        private set

    var batterySaver by mutableStateOf(false)
        private set

    val glassEffectsEnabled: Boolean
        get() = glassEnabled && !batterySaver

    /** 0 = heavily frosted, 1 = nearly clear. */
    var glassTransparency by mutableFloatStateOf(0.5f)
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
        val artwork = p.getString(KEY_ARTWORK_STYLE, null)
        artworkStyle = ArtworkStyle.entries.firstOrNull { it.name == artwork } ?: ArtworkStyle.Cover
        audioCacheLimitMiB = p.getInt(KEY_CACHE_LIMIT, 256).takeIf { it in audioCacheLimitsMiB } ?: 256
        autoplay = p.getBoolean(KEY_AUTOPLAY, true)
        musicReactiveGlow = p.getBoolean(KEY_MUSIC_GLOW, false)
        glassEnabled = p.getBoolean(KEY_GLASS, true)
        glassTransparency = p.getFloat(KEY_GLASS_TRANSPARENCY, 0.5f)
        batterySaver = p.getBoolean(KEY_BATTERY_SAVER, false)
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

    fun updateArtworkStyle(style: ArtworkStyle) {
        artworkStyle = style
        prefs?.edit()?.putString(KEY_ARTWORK_STYLE, style.name)?.apply()
    }

    fun updateAudioCacheLimit(limitMiB: Int) {
        require(limitMiB in audioCacheLimitsMiB)
        audioCacheLimitMiB = limitMiB
        prefs?.edit()?.putInt(KEY_CACHE_LIMIT, limitMiB)?.apply()
    }

    fun updateAutoplay(enabled: Boolean) {
        autoplay = enabled
        prefs?.edit()?.putBoolean(KEY_AUTOPLAY, enabled)?.apply()
    }

    fun updateMusicReactiveGlow(enabled: Boolean) {
        musicReactiveGlow = enabled
        prefs?.edit()?.putBoolean(KEY_MUSIC_GLOW, enabled)?.apply()
    }

    fun updateGlassEnabled(enabled: Boolean) {
        glassEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_GLASS, enabled)?.apply()
    }

    fun updateBatterySaver(enabled: Boolean) {
        batterySaver = enabled
        prefs?.edit()?.putBoolean(KEY_BATTERY_SAVER, enabled)?.apply()
    }

    fun updateGlassTransparency(value: Float) {
        glassTransparency = value.coerceIn(0f, 1f)
        prefs?.edit()?.putFloat(KEY_GLASS_TRANSPARENCY, glassTransparency)?.apply()
    }
}