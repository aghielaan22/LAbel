package com.aghielaan22.label

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches what's playing and decides what counts as a real listen:
 * a song over 30 seconds, heard for half its length or 4 minutes, whichever is less.
 * Leaving a song within 10 seconds counts as a skip.
 */
object ListenTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    private var song: Song? = null
    private var startedAtSeconds = 0L
    private var listenedMs = 0L
    private var counted = false
    private var lastTick = 0L

    fun start() {
        if (started) return
        started = true
        lastTick = SystemClock.elapsedRealtime()
        scope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTick
        lastTick = now

        val current = song ?: return
        if (!PlayerController.isPlaying) return
        listenedMs += elapsed.coerceAtMost(2500L)

        if (!counted) {
            val duration = PlayerController.duration()
            if (duration > 30_000L) {
                val threshold = minOf(duration / 2, 4 * 60_000L)
                if (listenedMs >= threshold) {
                    counted = true
                    StatsRepository.recordPlay(current)
                    ListenBrainz.submitListen(current, startedAtSeconds)
                }
            }
        }
    }

    /** Called whenever the playing song changes (or playback ends, with null). */
    fun onSongChanged(next: Song?) {
        finish()
        song = next
        listenedMs = 0L
        counted = false
        startedAtSeconds = System.currentTimeMillis() / 1000
        lastTick = SystemClock.elapsedRealtime()
        if (next != null) ListenBrainz.submitNowPlaying(next)
    }

    private fun finish() {
        val previous = song ?: return
        if (listenedMs > 0L) StatsRepository.addListenTime(previous, listenedMs)
        if (!counted && listenedMs < 10_000L) StatsRepository.recordSkip(previous)
    }
}