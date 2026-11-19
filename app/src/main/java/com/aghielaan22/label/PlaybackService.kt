package com.aghielaan22.label

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.IOException

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val busy = applyEffects()
            // Check often while a fade is happening, and only occasionally otherwise
            handler.postDelayed(this, if (busy) 100L else 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        PlaybackSettings.init(this)
        AppSettings.init(this)

        // Turns a YouTube watch link into a real audio stream URL, right when needed
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory(),
            ResolvingDataSource.Resolver { dataSpec ->
                val host = dataSpec.uri.host.orEmpty()
                if (host.endsWith("youtube.com")) {
                    try {
                        val streamUrl = MusicRepository.getAudioUrl(dataSpec.uri.toString())
                        dataSpec.withUri(Uri.parse(streamUrl))
                    } catch (e: Exception) {
                        throw IOException("Could not resolve audio stream", e)
                    }
                } else {
                    dataSpec
                }
            }
        )

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        handler.post(ticker)
    }

    /**
     * Applies the sleep timer and the fades between songs by setting the volume.
     * Returns true while something is fading, so we know to check more often.
     */
    private fun applyEffects(): Boolean {
        val p = player ?: return false
        var volume = 1f
        var busy = false

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
        handler.removeCallbacks(ticker)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private companion object {
        const val SLEEP_FADE_MS = 10_000L
        const val END_FADE_MS = 3_000L
    }
}