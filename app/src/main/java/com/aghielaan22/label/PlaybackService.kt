package com.aghielaan22.label

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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

    override fun onCreate() {
        super.onCreate()

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

        val player = ExoPlayer.Builder(this)
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

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}