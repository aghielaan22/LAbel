package com.aghielaan22.label

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    var queueSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isLoading = playbackState == Player.STATE_BUFFERING
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = controller?.currentMediaItemIndex ?: 0
            currentIndex = index
            currentSong = if (mediaItem == null) null else queueSongs.getOrNull(index)
            currentSong?.let { HistoryRepository.add(it) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleEnabled = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlayerController.repeatMode = repeatMode
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
            val c = future.get()
            c.addListener(listener)
            shuffleEnabled = c.shuffleModeEnabled
            repeatMode = c.repeatMode
            controller = c
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
        queueSongs = songs
        currentIndex = startIndex
        currentSong = songs.getOrNull(startIndex)
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /** Jumps to the song at [index] in the current queue. */
    fun playQueueIndex(index: Int) {
        controller?.let {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in queueSongs.indices) return
        queueSongs = queueSongs.toMutableList().apply { removeAt(index) }
        c.removeMediaItem(index)
        currentIndex = c.currentMediaItemIndex
    }

    /** Puts [song] right after the one that's playing. */
    fun playNext(song: Song) {
        val c = controller
        if (c == null || queueSongs.isEmpty()) {
            playQueue(listOf(song), 0)   // nothing queued yet, so just start it
            return
        }
        val insertAt = c.currentMediaItemIndex + 1
        queueSongs = queueSongs.toMutableList().apply {
            add(insertAt.coerceAtMost(size), song)
        }
        c.addMediaItem(insertAt, song.toMediaItem())
    }

    /** Puts [song] at the end of the queue. */
    fun addToQueue(song: Song) {
        val c = controller
        if (c == null || queueSongs.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        queueSongs = queueSongs + song
        c.addMediaItem(song.toMediaItem())
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Cycles repeat: off, then all songs, then one song, then off again. */
    fun cycleRepeatMode() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
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
            .setUri(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl(800)?.let { Uri.parse(it) })
                    .build()
            )
            .build()
}