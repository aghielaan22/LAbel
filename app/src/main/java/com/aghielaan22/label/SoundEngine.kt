package com.aghielaan22.label

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ln

data class EqPreset(val name: String, val gains: List<Float>)

object SoundEngine {

    private const val PREFS_NAME = "label_sound"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_GAINS = "eq_gains"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_LOUDNESS = "loudness_db"
    private const val KEY_SKIP_SILENCE = "skip_silence"

    /** The five sliders you see, in Hz. They're blended onto the phone's real bands. */
    val bandFrequencies = listOf(60, 230, 910, 3600, 14000)

    val presets = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass boost", listOf(6f, 4f, 1f, 0f, 0f)),
        EqPreset("Treble boost", listOf(0f, 0f, 0f, 3f, 6f)),
        EqPreset("Vocal", listOf(-2f, 0f, 3f, 4f, 1f)),
        EqPreset("Rock", listOf(4f, 2f, -1f, 3f, 4f)),
        EqPreset("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
        EqPreset("Electronic", listOf(5f, 3f, 0f, 2f, 4f)),
        EqPreset("Acoustic", listOf(3f, 2f, 1f, 2f, 3f))
    )

    private var prefs: SharedPreferences? = null
    private var equalizer: Equalizer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var sessionId = 0

    var eqEnabled by mutableStateOf(false)
        private set

    var gains by mutableStateOf(List(bandFrequencies.size) { 0f })
        private set

    var preset by mutableStateOf("Flat")
        private set

    var loudnessDb by mutableFloatStateOf(0f)
        private set

    var skipSilence by mutableStateOf(false)
        private set

    /** Turns false if this phone can't create an equalizer. */
    var available by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        eqEnabled = p.getBoolean(KEY_EQ_ENABLED, false)
        val saved = p.getString(KEY_GAINS, null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
        if (saved != null && saved.size == bandFrequencies.size) gains = saved
        preset = p.getString(KEY_PRESET, "Flat") ?: "Flat"
        loudnessDb = p.getFloat(KEY_LOUDNESS, 0f)
        skipSilence = p.getBoolean(KEY_SKIP_SILENCE, false)
    }

    // ---- Called by the playback service ----

    fun attach(newSessionId: Int) {
        if (newSessionId <= 0) return
        if (newSessionId == sessionId && equalizer != null) return
        release()
        sessionId = newSessionId

        equalizer = try {
            Equalizer(0, newSessionId).also { available = true }
        } catch (e: Exception) {
            available = false
            null
        }
        enhancer = try {
            LoudnessEnhancer(newSessionId)
        } catch (e: Exception) {
            null
        }
        applyEqualizer()
        applyLoudness()
    }

    fun release() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { enhancer?.release() } catch (e: Exception) { }
        equalizer = null
        enhancer = null
        sessionId = 0
    }

    // ---- Called by the screen ----

    fun updateEqEnabled(enabled: Boolean) {
        eqEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_EQ_ENABLED, enabled)?.apply()
        applyEqualizer()
    }

    fun updateGain(index: Int, db: Float) {
        gains = gains.toMutableList().also { it[index] = db }
        preset = "Custom"
        saveGains()
        applyEqualizer()
    }

    fun applyPreset(newPreset: EqPreset) {
        gains = newPreset.gains
        preset = newPreset.name
        saveGains()
        applyEqualizer()
    }

    fun updateLoudness(db: Float) {
        loudnessDb = db
        prefs?.edit()?.putFloat(KEY_LOUDNESS, db)?.apply()
        applyLoudness()
    }

    fun updateSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        prefs?.edit()?.putBoolean(KEY_SKIP_SILENCE, enabled)?.apply()
    }

    // ---- Internals ----

    private fun saveGains() {
        prefs?.edit()
            ?.putString(KEY_GAINS, gains.joinToString(","))
            ?.putString(KEY_PRESET, preset)
            ?.apply()
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        try {
            eq.enabled = eqEnabled
            if (!eqEnabled) return

            val range = eq.bandLevelRange // minimum and maximum, in millibels
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            for (band in 0 until eq.numberOfBands.toInt()) {
                val hz = eq.getCenterFreq(band.toShort()) / 1000f
                val level = (gainAtFrequency(hz) * 100).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(band.toShort(), level.toShort())
            }
        } catch (e: Exception) {
            // Some phones refuse; the sliders just won't change the sound
        }
    }

    private fun applyLoudness() {
        val e = enhancer ?: return
        try {
            e.setTargetGain((loudnessDb * 100).toInt())
            e.enabled = loudnessDb > 0f
        } catch (ex: Exception) {
        }
    }

    /** Blends the five slider gains onto any frequency, smoothly. */
    private fun gainAtFrequency(hz: Float): Float {
        val freqs = bandFrequencies
        if (hz <= freqs.first()) return gains.first()
        if (hz >= freqs.last()) return gains.last()
        for (i in 0 until freqs.size - 1) {
            if (hz <= freqs[i + 1]) {
                val low = ln(freqs[i].toFloat())
                val high = ln(freqs[i + 1].toFloat())
                val fraction = (ln(hz) - low) / (high - low)
                return gains[i] + (gains[i + 1] - gains[i]) * fraction
            }
        }
        return 0f
    }
}