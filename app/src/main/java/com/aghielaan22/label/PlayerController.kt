package com.aghielaan22.label

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object PlayerController {
    data class QueueRow(val index: Int, val key: String, val song: Song)

    class RemovedTrack internal constructor(
        internal val song: Song,
        internal val mediaId: String,
        internal val previous: String?,
        internal val following: String?,
        internal val index: Int,
        internal val generation: Int
    )

    private var controller: MediaController? = null
    private var connecting = false
    private var connectionReady: CompletableDeferred<Boolean>? = null
    private var appContext: Context? = null
    private var snapshotStore: PlaybackSnapshotStore? = null
    private var lastSaved: PlaybackSnapshot? = null
    private var restoredItem: String? = null
    private var queueGeneration = 0
    private var recoveryJob: Job? = null
    private var retriedItem: String? = null
    private var failedItem: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var radioJob: Job? = null
    private var radioSeedId: String? = null

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
    var playbackFailure by mutableStateOf<String?>(null)
        private set
    var isRetrying by mutableStateOf(false)
        private set
    var canSkipFailure by mutableStateOf(false)
        private set
    var queueRows by mutableStateOf<List<QueueRow>>(emptyList())
        private set
    var pendingRemoval by mutableStateOf<RemovedTrack?>(null)
        private set
    var positionRevision by mutableIntStateOf(0)
        private set
    var upNextIndices by mutableStateOf<List<Int>>(emptyList())
        private set

    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    /** True for a guest in a studio: the host controls playback. */
    @set:JvmName("setControlsLockedInternal")
    var controlsLocked by mutableStateOf(false)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing && restoredItem != null) {
                restoredItem = null
                announceSong()
            }
            StudioController.hostChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isLoading = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                clearFailure()
                StudioController.onPlayerReady()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = controller?.currentMediaItemIndex ?: 0
            currentIndex = index
            currentSong = mediaItem?.let { it.toQueueSong() ?: queueSongs.getOrNull(index) }
            if (mediaItem?.mediaId != failedItem) clearFailure()
            if (restoredItem == null || mediaItem?.mediaId != restoredItem) {
                restoredItem = null
                announceSong()
            }
            StudioController.hostChanged()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            positionRevision++
            if (reason == Player.DISCONTINUITY_REASON_SEEK) StudioController.hostChanged()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleEnabled = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlayerController.repeatMode = repeatMode
            StudioController.hostChanged()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) rebuildQueueRows(player)
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                )
            ) updateQueueOrder(player)
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                )
            ) savePlayback()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val item = c.currentMediaItem ?: return
            val position = c.currentPosition.coerceAtLeast(0)
            failedItem = item.mediaId
            val wasPlayingAtError = c.playWhenReady
            if (controlsLocked) {
                StudioController.onGuestPlaybackError()
                return
            }
            val httpCode = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
            val expired = httpCode == 403 || httpCode == 410
            if (expired) item.toQueueSong()?.let { MusicRepository.invalidateAudioUrl(it.id) }
            val transient = expired || httpCode in setOf(408, 500, 502, 503, 504) ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

            if (transient && NetworkMonitor.isOnline && wasPlayingAtError && retriedItem != item.mediaId) {
                retriedItem = item.mediaId
                isRetrying = true
                recoveryJob = scope.launch {
                    delay(550)
                    isRetrying = false
                    if (controller === c && c.currentMediaItem?.mediaId == item.mediaId &&
                        c.playerError != null && !controlsLocked && c.playWhenReady
                    ) {
                        c.seekTo(position)
                        c.prepare()
                    } else if (c.currentMediaItem?.mediaId == item.mediaId && c.playerError != null && !controlsLocked) {
                        playbackFailure = friendlyPlaybackMessage(error)
                    }
                }
            } else {
                isRetrying = false
                playbackFailure = friendlyPlaybackMessage(error)
            }
            updateQueueOrder(c)
        }
    }

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val ready = CompletableDeferred<Boolean>()
        connectionReady = ready
        val app = context.applicationContext
        appContext = app
        val store = snapshotStore ?: PlaybackSnapshotStore(app).also {
            snapshotStore = it
            scope.launch {
                while (true) {
                    delay(5000)
                    if (isPlaying) savePlayback()
                }
            }
        }
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller !== disconnected) return
                    controller = null
                    isPlaying = false
                    isLoading = false
                    clearFailure()
                    playbackFailure = "Player disconnected."
                }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            val c = try {
                future.get()
            } catch (e: Exception) {
                Log.w("PlayerController", "Could not connect to playback", e)
                playbackFailure = "Couldn't connect to the player."
                ready.complete(false)
                return@addListener
            }
            controller = c
            clearFailure()
            c.addListener(listener)
            ListenTracker.start()
            shuffleEnabled = c.shuffleModeEnabled
            repeatMode = c.repeatMode
            isPlaying = c.isPlaying
            isLoading = c.playbackState == Player.STATE_BUFFERING
            rebuildQueueRows(c)
            updateQueueOrder(c)
            scope.launch {
                try {
                    restoreSavedPlayback(c, store)
                } finally {
                    ready.complete(controller === c)
                }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private suspend fun restoreSavedPlayback(c: MediaController, store: PlaybackSnapshotStore) {
        if (c.mediaItemCount != 0 || StudioController.active) return
        val generation = queueGeneration
        val saved = store.read()
        if (controller !== c || c.mediaItemCount != 0 || generation != queueGeneration ||
            StudioController.active
        ) return
        if (saved == null) {
            appContext?.let(NowPlayingWidget::clearStaleQueue)
            return
        }
        queueSongs = saved.songs
        currentIndex = saved.index
        currentSong = saved.songs[saved.index]
        val items = saved.songs.map { it.toMediaItem() }
        restoredItem = items[saved.index].mediaId
        c.playWhenReady = false
        c.shuffleModeEnabled = saved.shuffle
        c.repeatMode = saved.repeatMode.takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL }
            ?: Player.REPEAT_MODE_OFF
        c.setMediaItems(items, saved.index, saved.positionMs)
        updateQueueOrder(c)
    }

    internal suspend fun runWidgetAction(context: Context, action: WidgetAction) {
        if (action == WidgetAction.Pause && controller == null) return

        if (controller == null) {
            HistoryRepository.init(context)
            StatsRepository.init(context)
            ListenBrainz.init(context)
            StudioController.init(context)
            NetworkMonitor.start(context)
            connect(context)
        }
        val ready = withTimeoutOrNull(4_500L) { connectionReady?.await() }
        val c = controller ?: return
        if (ready != true || controlsLocked || c.currentMediaItem == null) return
        when (action) {
            WidgetAction.Play -> {
                if (c.playerError != null) {
                    retryPlayback()
                } else {
                    if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    c.play()
                }
            }
            WidgetAction.Pause -> c.pause()
            WidgetAction.Next -> if (c.hasNextMediaItem()) next()
        }
    }

    private fun announceSong() {
        ListenTracker.onSongChanged(currentSong)
        currentSong?.let { song ->
            HistoryRepository.add(song)
            if (AppSettings.autoplay && !controlsLocked && currentIndex >= queueSongs.lastIndex) {
                loadRadio(song)
            }
        }
    }

    private fun rebuildQueueRows(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) return
        val rows = (0 until player.mediaItemCount).mapNotNull { index ->
            val item = player.getMediaItemAt(index)
            val song = item.toQueueSong() ?: queueSongs.getOrNull(index) ?: return@mapNotNull null
            QueueRow(index, item.mediaId, song)
        }
        if (queueSongs.size != rows.size || queueSongs.indices.any { queueSongs[it] != rows[it].song }) {
            queueSongs = rows.map { it.song }
        }
        queueRows = rows
        currentIndex = player.currentMediaItemIndex
        currentSong = queueSongs.getOrNull(currentIndex)
    }

    private fun updateQueueOrder(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) return
        val timeline = player.currentTimeline
        val current = player.currentMediaItemIndex
        canSkipFailure = player.nextMediaItemIndex != C.INDEX_UNSET && player.nextMediaItemIndex != current
        if (current !in 0 until timeline.windowCount || player.repeatMode == Player.REPEAT_MODE_ONE) {
            upNextIndices = emptyList()
            return
        }
        upNextIndices = buildList {
            var index = current
            repeat(timeline.windowCount - 1) {
                index = timeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
                if (index == C.INDEX_UNSET || index == current) return@buildList
                add(index)
            }
        }
    }

    fun savePlayback() {
        val c = controller ?: return
        val store = snapshotStore ?: return
        if (StudioController.active || currentIndex !in queueSongs.indices ||
            c.mediaItemCount != queueSongs.size
        ) return
        val snapshot = PlaybackSnapshot(
            queueSongs, c.currentMediaItemIndex, c.currentPosition.coerceAtLeast(0),
            c.shuffleModeEnabled, c.repeatMode
        )
        if (snapshot != lastSaved) {
            lastSaved = snapshot
            store.enqueue(snapshot)
        }
    }

    private fun clearFailure() {
        recoveryJob?.cancel()
        recoveryJob = null
        isRetrying = false
        playbackFailure = null
        failedItem = null
        retriedItem = null
    }

    fun retryPlayback() {
        if (blocked()) return
        val c = controller ?: run {
            appContext?.let { connect(it) }
            return
        }
        clearFailure()
        currentSong?.let { MusicRepository.invalidateAudioUrl(it.id) }
        c.prepare()
        c.play()
    }

    fun skipFailedTrack() {
        if (blocked()) return
        val c = controller ?: return
        if (!canSkipFailure) return
        clearFailure()
        c.seekToNextMediaItem()
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    // ---- Everyday controls ----

    /** Plays [songs] as a queue, starting at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (blocked()) return
        val c = controller ?: run {
            errorMessage = "Player is still starting, try again"
            return
        }
        if (startIndex !in songs.indices) return
        clearFailure()
        restoredItem = null
        queueGeneration++
        pendingRemoval = null
        errorMessage = null
        radioJob?.cancel()
        radioSeedId = null
        queueSongs = songs
        currentIndex = startIndex
        currentSong = songs.getOrNull(startIndex)
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
        StudioController.hostChanged()
    }

    /**
     * Fetches songs similar to [seed] and adds them to the end of the queue,
     * as long as the queue still ends with [seed] when they arrive.
     */
    private fun loadRadio(seed: Song) {
        if (radioSeedId == seed.id) return
        radioSeedId = seed.id
        radioJob?.cancel()
        radioJob = scope.launch {
            val similar = try {
                withContext(Dispatchers.IO) { MusicRepository.getRadio(seed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                radioSeedId = null // allow another try later
                return@launch
            }

            val c = controller ?: return@launch
            if (queueSongs.lastOrNull()?.id != seed.id) return@launch // the user moved on

            val alreadyQueued = queueSongs.map { it.id }.toSet()
            val fresh = similar
                .filter { it.id !in alreadyQueued && !StatsRepository.isDisliked(it.id) }
                .take(30)
            if (fresh.isEmpty()) return@launch

            queueSongs = queueSongs + fresh
            c.addMediaItems(fresh.map { it.toMediaItem() })
            StudioController.hostChanged()
        }
    }

    /** Jumps to the song at [index] in the current queue. */
    fun playQueueIndex(index: Int) {
        if (blocked()) return
        controller?.let {
            if (index !in 0 until it.mediaItemCount) return
            it.seekToDefaultPosition(index)
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
        }
    }

    fun removeFromQueue(row: QueueRow): Boolean {
        if (blocked()) return false
        val c = controller ?: return false
        val index = row.index
        val song = queueSongs.getOrNull(index) ?: return false
        if (index >= c.mediaItemCount || c.getMediaItemAt(index).mediaId != row.key) return false
        val removed = RemovedTrack(
            song, c.getMediaItemAt(index).mediaId,
            if (index > 0) c.getMediaItemAt(index - 1).mediaId else null,
            if (index + 1 < c.mediaItemCount) c.getMediaItemAt(index + 1).mediaId else null,
            index, queueGeneration
        )
        queueSongs = queueSongs.toMutableList().apply { removeAt(index) }
        c.removeMediaItem(index)
        currentIndex = c.currentMediaItemIndex
        if (queueSongs.isEmpty() && !StudioController.active) {
            val empty = PlaybackSnapshot(emptyList(), 0, 0, c.shuffleModeEnabled, c.repeatMode)
            lastSaved = empty
            snapshotStore?.enqueue(empty)
        }
        StudioController.hostChanged()
        pendingRemoval = removed
        return true
    }

    fun finishQueueRemoval(removed: RemovedTrack, undo: Boolean) {
        if (pendingRemoval !== removed) return
        pendingRemoval = null
        if (!undo || removed.generation != queueGeneration || blocked()) return
        val c = controller ?: return
        val indices = 0 until c.mediaItemCount
        val following = indices.firstOrNull { c.getMediaItemAt(it).mediaId == removed.following }
        val previous = indices.firstOrNull { c.getMediaItemAt(it).mediaId == removed.previous }
        val at = following ?: previous?.plus(1) ?: removed.index.coerceAtMost(queueSongs.size)
        queueSongs = queueSongs.toMutableList().apply { add(at, removed.song) }
        c.addMediaItem(at, removed.song.toMediaItem(removed.mediaId))
        StudioController.hostChanged()
    }

    /** Moves a song within the queue. */
    fun moveQueueItem(from: Int, to: Int) {
        if (blocked()) return
        val c = controller ?: return
        if (from !in queueSongs.indices || to !in queueSongs.indices || from == to) return
        queueSongs = queueSongs.toMutableList().apply { add(to, removeAt(from)) }
        c.moveMediaItem(from, to)
        currentIndex = c.currentMediaItemIndex
        StudioController.hostChanged()
    }

    /** Puts [song] right after the one that's playing. (A studio guest suggests it instead.) */
    fun playNext(song: Song) {
        if (controlsLocked) {
            StudioController.suggestSong(song)
            return
        }
        val c = controller
        if (c == null || queueSongs.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        val insertAt = c.currentMediaItemIndex + 1
        queueSongs = queueSongs.toMutableList().apply {
            add(insertAt.coerceAtMost(size), song)
        }
        c.addMediaItem(insertAt, song.toMediaItem())
        StudioController.hostChanged()
    }

    /** Puts [song] at the end of the queue. (A studio guest suggests it instead.) */
    fun addToQueue(song: Song) {
        if (controlsLocked) {
            StudioController.suggestSong(song)
            return
        }
        val c = controller
        if (c == null || queueSongs.isEmpty()) {
            playQueue(listOf(song), 0)
            return
        }
        queueSongs = queueSongs + song
        c.addMediaItem(song.toMediaItem())
        StudioController.hostChanged()
    }

    fun toggleShuffle() {
        if (StudioController.active) {
            StudioController.showNotice("Shuffle is off while you're in a studio.")
            return
        }
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Cycles repeat: off, then all songs, then one song, then off again. */
    fun cycleRepeatMode() {
        if (blocked()) return
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun togglePlayPause() {
        if (blocked()) return
        controller?.let {
            if (it.playWhenReady && playbackFailure == null) {
                it.pause()
            } else if (it.playerError != null) {
                retryPlayback()
            } else {
                if (it.playbackState == Player.STATE_IDLE) it.prepare()
                it.play()
            }
        }
    }

    fun next() {
        if (blocked()) return
        if (playbackFailure != null && canSkipFailure) skipFailedTrack()
        else controller?.seekToNextMediaItem()
    }

    fun previous() {
        if (blocked()) return
        controller?.seekToPrevious()
    }

    internal fun prepareForCacheEdit(): Boolean {
        if (StudioController.active) return false
        val c = controller ?: return true
        if (c.playWhenReady || c.playbackState == Player.STATE_BUFFERING) return false
        if (c.playbackState != Player.STATE_IDLE) c.stop()
        return true
    }

    internal fun skipEndLimit(songId: String): Long {
        val player = controller ?: return 0L
        if (StudioController.active || currentSong?.id != songId ||
            !player.isCurrentMediaItemSeekable || player.playerError != null
        ) return 0L
        return player.duration.coerceAtLeast(0L)
    }
    internal fun loopEndLimit(songId: String): Long {
        val player = controller ?: return 0L
        val duration = player.duration
        if (StudioController.active || currentSong?.id != songId ||
            !player.isCurrentMediaItemSeekable || player.playerError != null || duration <= 0
        ) return 0L
        return (duration - LOOP_END_MARGIN_MS).coerceAtLeast(0L)
    }

    internal fun startTrackLoop(songId: String, range: TrackLoop): Boolean {
        if (!range.fits(loopEndLimit(songId))) return false
        val entryId = controller?.currentMediaItem?.mediaId ?: return false
        TrackLoops.start(ActiveTrackLoop(songId, entryId, range))
        return true
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = (controller?.duration ?: 0L).coerceAtLeast(0L)

    fun seekTo(positionMs: Long) {
        if (blocked()) return
        controller?.seekTo(positionMs)
    }

    fun queueHash(): Int = queueSongs.joinToString("|") { it.id }.hashCode()

    // ---- Studio ----

    fun setControlsLocked(locked: Boolean) {
        controlsLocked = locked
        appContext?.let { NowPlayingWidget.updateLock(it, locked) }
    }

    fun forceShuffleOff() {
        controller?.shuffleModeEnabled = false
    }

    /** Studio only: loads the host's queue on a guest's player, bypassing the lock. */
    fun remotePlayQueue(songs: List<Song>, index: Int, positionMs: Long, playWhenReady: Boolean) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        radioJob?.cancel()
        radioSeedId = null
        queueGeneration++
        pendingRemoval = null
        restoredItem = null
        clearFailure()
        val i = index.coerceIn(0, songs.lastIndex)
        queueSongs = songs
        currentIndex = i
        currentSong = songs[i]
        c.setMediaItems(songs.map { it.toMediaItem() }, i, positionMs.coerceAtLeast(0L))
        c.prepare()
        c.playWhenReady = playWhenReady
    }

    internal fun remoteAlign(
        index: Int,
        positionMs: Long,
        playing: Boolean,
        repeat: Int,
        seek: Boolean,
        recover: Boolean
    ): Boolean {
        val c = controller ?: return false
        if (index !in 0 until c.mediaItemCount) return false
        if (seek) c.seekTo(index, positionMs.coerceAtLeast(0L))
        if (recover && c.playbackState == Player.STATE_IDLE && (playing || c.playerError != null)) {
            queueSongs.getOrNull(index)?.let { MusicRepository.invalidateAudioUrl(it.id) }
            c.prepare()
        }
        if (c.playWhenReady != playing) c.playWhenReady = playing
        if (c.repeatMode != repeat) c.repeatMode = repeat
        return c.playerError == null
    }

    internal fun remoteClearQueue() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        c.pause()
        clearFailure()
        restoredItem = null
        queueSongs = emptyList()
        c.clearMediaItems()
    }

    fun remoteSetRepeat(mode: Int) {
        val c = controller ?: return
        if (c.repeatMode != mode) c.repeatMode = mode
    }

    private fun blocked(): Boolean {
        if (!controlsLocked) return false
        StudioController.showNotice("In a studio, the host controls playback.")
        return true
    }

    private fun MediaItem.toQueueSong(): Song? {
        val meta = mediaMetadata
        val url = meta.extras?.getString("label.songUrl") ?: mediaId.takeIf { it.startsWith("https://") }
        ?: return null
        return Song(
            url, meta.title?.toString().orEmpty(), meta.artist?.toString().orEmpty(),
            meta.extras?.getString("label.coverUrl") ?: meta.artworkUri?.toString()
        )
    }

    private fun Song.toMediaItem(entryId: String = UUID.randomUUID().toString()): MediaItem =
        MediaItem.Builder()
            .setMediaId(entryId)
            .setUri(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl(800)?.let { Uri.parse(it) })
                    .setExtras(Bundle().apply {
                        putString("label.songUrl", id)
                        putString("label.coverUrl", thumbnailUrl)
                    })
                    .build()
            )
            .build()
}