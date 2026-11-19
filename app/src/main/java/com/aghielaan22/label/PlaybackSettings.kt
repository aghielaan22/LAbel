package com.aghielaan22.label

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object PlaybackSettings {

    private const val PREFS_NAME = "label_settings"
    private const val KEY_FADE_SECONDS = "fade_seconds"

    private var prefs: SharedPreferences? = null

    /** 0 means fades are off. */
    var fadeSeconds by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        fadeSeconds = p.getInt(KEY_FADE_SECONDS, 0)
    }

    fun updateFadeSeconds(seconds: Int) {
        fadeSeconds = seconds
        prefs?.edit()?.putInt(KEY_FADE_SECONDS, seconds)?.apply()
    }
}