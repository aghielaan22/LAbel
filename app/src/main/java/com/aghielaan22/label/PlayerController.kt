package com.aghielaan22.label

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

object PlayerController {

    private var controller: MediaController? = null
    private var connecting = false
    private var queue: List<Song> = emptyList()

    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isLoading = playbackState == Player.STATE_BUFFERING
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentSong = queue.firstOrNull { it.id == mediaItem?.mediaId }
            currentSong?.let { HistoryRepository.add(it) }
        }

        override fun onPlayerError(error: PlaybackException) {
            errorMessage = "Playback failed: ${error.errorCodeName}"
            // If one song can't play, skip on to the next one
            controller?.let {
                if (it.hasNextMediaItem()) {
                    it.seekToNextMediaItem()
                    it.prepare()
                    it.play()
                }
            }
        }
    }

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val appContext = context.applicationContext
        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            connecting = false
        }, ContextCompat.getMainExecutor(appContext))
    }

    /** Plays [songs] as a queue, starting at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        val c = controller ?: run {
            errorMessage = "Player is still starting, try again"
            return
        }
        errorMessage = null
        queue = songs
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
        currentSong = songs.getOrNull(startIndex)
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = (controller?.duration ?: 0L).coerceAtLeast(0L)

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(id) // the YouTube link; the service turns it into audio
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
}