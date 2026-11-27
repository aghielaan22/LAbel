package com.aghielaan22.label

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var widgetListener: Player.Listener? = null
    private var trackLoopPlayback: TrackLoopPlayback? = null
    private var trackSkipPlayback: TrackSkipPlayback? = null
    private val prefetcher by lazy { NextTrackPrefetcher(this) }
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val busy = applyEffects()
            player?.let(prefetcher::update)
            // Check often while a fade is happening, and only occasionally otherwise
            handler.postDelayed(this, if (busy) 100L else 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        PlaybackSettings.init(this)
        AppSettings.init(this)
        SoundEngine.init(this)

        val exoPlayer = ExoPlayer.Builder(this, MusicGlowRenderersFactory(this))            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackDataSource()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // The equalizer and loudness boost attach to the player's own audio
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                SoundEngine.attach(audioSessionId)
            }
        })
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            SoundEngine.attach(exoPlayer.audioSessionId)
        }

        player = exoPlayer
        trackLoopPlayback = TrackLoopPlayback(exoPlayer)
        trackSkipPlayback = TrackSkipPlayback(this, exoPlayer)
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        widgetListener = NowPlayingWidget.attach(this, exoPlayer)
        handler.post(ticker)
    }

    private fun playbackDataSource(): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
        PlaybackAudioCache.start(applicationContext)
        return DataSource.Factory { PlaybackAudioCache.dataSource(http, AppSettings.audioQuality) }
    }

    /**
     * Applies skip silence, the sleep timer and the fades between songs.
     * Returns true while something is fading, so we know to check more often.
     */
    private fun applyEffects(): Boolean {
        val p = player ?: return false
        var volume = 1f
        var busy = false

        if (p.skipSilenceEnabled != SoundEngine.skipSilence) {
            p.skipSilenceEnabled = SoundEngine.skipSilence
        }

        // Sleep timer: a countdown, or "end of song"
        if (SleepTimer.endAtMs > 0L) {
            val remaining = SleepTimer.endAtMs - SystemClock.elapsedRealtime()
            SleepTimer.updateRemaining(remaining)
            if (remaining <= 0L) {
                p.pause()
                SleepTimer.cancel()
            } else if (remaining < SLEEP_FADE_MS + 600) {
                busy = true
                volume = minOf(volume, (remaining / SLEEP_FADE_MS.toFloat()).coerceIn(0f, 1f))
            }
        } else if (SleepTimer.atEndOfSong &&
            p.playbackState == Player.STATE_READY &&
            p.duration != C.TIME_UNSET
        ) {
            val remaining = p.duration - p.currentPosition
            if (remaining <= 300L) {
                p.pause()
                SleepTimer.cancel()
            } else if (remaining < END_FADE_MS + 600) {
                busy = true
                volume = minOf(volume, (remaining / END_FADE_MS.toFloat()).coerceIn(0f, 1f))
            }
        }

        // Fade between songs: fade out at the end, fade in at the start
        val fadeMs = PlaybackSettings.fadeSeconds * 1000L
        if (fadeMs > 0L &&
            p.playbackState == Player.STATE_READY &&
            p.duration != C.TIME_UNSET &&
            p.duration > fadeMs * 2
        ) {
            val position = p.currentPosition
            val remaining = p.duration - position
            if (position < fadeMs + 600 || remaining < fadeMs + 600) busy = true
            val fadeIn = (position / fadeMs.toFloat()).coerceIn(0f, 1f)
            val fadeOut = (remaining / fadeMs.toFloat()).coerceIn(0f, 1f)
            volume = minOf(volume, fadeIn, fadeOut)
        }

        if (p.volume != volume) p.volume = volume
        return busy
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        widgetListener?.let { player?.removeListener(it) }
        widgetListener = null
        trackSkipPlayback?.close()
        trackSkipPlayback = null
        trackLoopPlayback?.close()
        trackLoopPlayback = null
        handler.removeCallbacks(ticker)
        prefetcher.close()
        SoundEngine.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        NowPlayingWidget.serviceClosed(this)
        super.onDestroy()
    }

    private companion object {
        const val SLEEP_FADE_MS = 10_000L
        const val END_FADE_MS = 3_000L
    }
}