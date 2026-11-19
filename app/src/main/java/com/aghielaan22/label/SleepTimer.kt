package com.aghielaan22.label

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SleepTimer {

    /** When a timed sleep timer ends (in SystemClock time), or 0 if there isn't one. */
    var endAtMs by mutableLongStateOf(0L)
        private set

    var atEndOfSong by mutableStateOf(false)
        private set

    /** Which preset is running, so the chip can look selected. */
    var activeMinutes by mutableIntStateOf(0)
        private set

    var remainingSeconds by mutableIntStateOf(0)
        private set

    val isActive: Boolean get() = endAtMs > 0L || atEndOfSong

    fun startMinutes(minutes: Int) {
        atEndOfSong = false
        activeMinutes = minutes
        endAtMs = SystemClock.elapsedRealtime() + minutes * 60_000L
        remainingSeconds = minutes * 60
    }

    fun startEndOfSong() {
        endAtMs = 0L
        activeMinutes = 0
        atEndOfSong = true
    }

    fun cancel() {
        endAtMs = 0L
        activeMinutes = 0
        atEndOfSong = false
        remainingSeconds = 0
    }

    fun updateRemaining(remainingMs: Long) {
        val seconds = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
        if (seconds != remainingSeconds) remainingSeconds = seconds
    }
}