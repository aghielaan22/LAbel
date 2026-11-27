package com.aghielaan22.label

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

internal const val MIN_LOOP_MS = 1_000L
internal const val LOOP_END_MARGIN_MS = 200L

internal data class TrackLoop(val name: String, val startMs: Long, val endMs: Long) {
    fun fits(limit: Long): Boolean =
        startMs >= 0 && endMs <= limit && endMs >= startMs && endMs - startMs >= MIN_LOOP_MS
}

internal class ActiveTrackLoop(
    val songId: String,
    val entryId: String,
    val range: TrackLoop
)

internal object TrackLoops {
    private val current = MutableStateFlow<ActiveTrackLoop?>(null)
    val active = current.asStateFlow()

    fun start(loop: ActiveTrackLoop) {
        current.value = loop
    }

    fun stop() {
        current.value = null
    }

    fun stop(loop: ActiveTrackLoop) {
        current.compareAndSet(loop, null)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("label_track_loops", Context.MODE_PRIVATE)

    fun load(context: Context, songId: String): TrackLoop? {
        val saved = preferences(context).getString(songId, null) ?: return null
        return runCatching {
            val json = JSONObject(saved)
            TrackLoop(json.getString("name"), json.getLong("start"), json.getLong("end"))
        }.getOrNull()
    }

    fun save(context: Context, songId: String, loop: TrackLoop) {
        val json = JSONObject()
            .put("name", loop.name)
            .put("start", loop.startMs)
            .put("end", loop.endMs)
        preferences(context).edit().putString(songId, json.toString()).apply()
    }

    fun remove(context: Context, songId: String) {
        preferences(context).edit().remove(songId).apply()
    }
}

@OptIn(UnstableApi::class)
internal class TrackLoopPlayback(private val player: ExoPlayer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var message: PlayerMessage? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val loop = TrackLoops.active.value ?: return
            if (mediaItem?.mediaId != loop.entryId || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                TrackLoops.stop(loop)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val loop = TrackLoops.active.value ?: return
            if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                newPosition.mediaItem?.mediaId == loop.entryId &&
                newPosition.positionMs !in loop.range.startMs until loop.range.endMs
            ) {
                TrackLoops.stop(loop)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) return
            val loop = TrackLoops.active.value ?: return
            if (isCurrent(loop) && player.currentPosition >= loop.range.endMs) {
                player.seekTo(loop.range.startMs)
            }
        }
    }

    init {
        player.addListener(listener)
        scope.launch {
            TrackLoops.active.collect { loop ->
                message?.cancel()
                message = null
                if (loop != null) arm(loop)
            }
        }
    }

    private fun isCurrent(loop: ActiveTrackLoop): Boolean =
        TrackLoops.active.value === loop &&
                player.currentMediaItem?.mediaId == loop.entryId &&
                !StudioController.active

    private fun arm(loop: ActiveTrackLoop) {
        val duration = player.duration
        if (!isCurrent(loop) || !player.isCurrentMediaItemSeekable ||
            duration <= 0 || !loop.range.fits(duration - LOOP_END_MARGIN_MS)
        ) {
            TrackLoops.stop(loop)
            return
        }

        try {
            message = player.createMessage { _, _ ->
                if (isCurrent(loop) && player.playWhenReady &&
                    player.currentPosition >= loop.range.endMs - LOOP_END_MARGIN_MS
                ) {
                    player.seekTo(loop.range.startMs)
                }
            }.setLooper(player.applicationLooper)
                .setPosition(player.currentMediaItemIndex, loop.range.endMs)
                .setDeleteAfterDelivery(false)
                .send()

            player.seekTo(loop.range.startMs)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        } catch (e: RuntimeException) {
            Log.w("TrackLoopPlayback", "Could not start loop", e)
            TrackLoops.stop(loop)
        }
    }

    fun close() {
        scope.cancel()
        player.removeListener(listener)
        message?.cancel()
        message = null
        TrackLoops.stop()
    }
}

@Composable
internal fun ActiveLoopButton(songId: String?) {
    val active by TrackLoops.active.collectAsState()
    val loop = active ?: return
    if (loop.songId != songId) return

    TextButton(onClick = { TrackLoops.stop(loop) }) {
        Text("Turn loop off")
    }
}

@androidx.compose.runtime.Composable
@kotlin.OptIn(ExperimentalMaterial3Api::class)
internal fun TrackLoopDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val limit = PlayerController.loopEndLimit(song.id)
    if (limit < MIN_LOOP_MS) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Loop a section") },
            text = {
                Text(
                    when {
                        StudioController.active -> "Leave Studio to loop a section."
                        PlayerController.isLoading -> "Wait for the song to finish buffering."
                        else -> "Open a seekable track in Now Playing to set a loop."
                    }
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
        return
    }

    var saved by remember(song.id) { mutableStateOf(TrackLoops.load(context, song.id)) }
    val initialStart = remember(song.id, limit) {
        PlayerController.position().coerceIn(0L, limit - MIN_LOOP_MS)
    }
    var loop by remember(song.id, limit) {
        mutableStateOf(
            saved?.takeIf { it.fits(limit) }
                ?: TrackLoop("", initialStart, (initialStart + 8_000L).coerceAtMost(limit))
        )
    }
    var message by remember(song.id) { mutableStateOf<String?>(null) }
    val valid = loop.fits(limit)

    fun namedLoop(): TrackLoop = loop.copy(name = loop.name.trim().ifEmpty { "Loop" })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loop a section") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = loop.name,
                    onValueChange = { loop = loop.copy(name = it.take(60)) },
                    label = { Text("Loop name") },
                    singleLine = true
                )
                Text("A ${loopTime(loop.startMs)}   •   B ${loopTime(loop.endMs)}")
                RangeSlider(
                    value = loop.startMs.toFloat()..loop.endMs.toFloat(),
                    valueRange = 0f..limit.toFloat(),
                    onValueChange = {
                        loop = loop.copy(
                            startMs = it.start.toLong().coerceIn(0L, limit),
                            endMs = it.endInclusive.toLong().coerceIn(0L, limit)
                        )
                    }
                )
                Row {
                    TextButton(onClick = {
                        loop = loop.copy(startMs = PlayerController.position().coerceIn(0L, loop.endMs))
                    }) { Text("Set A here") }
                    TextButton(onClick = {
                        loop = loop.copy(endMs = PlayerController.position().coerceIn(loop.startMs, limit))
                    }) { Text("Set B here") }
                }
                if (!valid) Text("Choose a section at least one second long.")
                TextButton(enabled = valid, onClick = {
                    message = if (PlayerController.startTrackLoop(song.id, namedLoop())) {
                        "Loop active"
                    } else {
                        "This track is no longer ready to loop."
                    }
                }) { Text("Preview loop") }
                if (saved != null) {
                    TextButton(onClick = {
                        TrackLoops.remove(context, song.id)
                        saved = null
                        message = "Saved loop removed"
                    }) { Text("Remove saved loop") }
                }
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                TrackLoops.save(context, song.id, namedLoop())
                onDismiss()
            }) { Text("Save loop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun loopTime(ms: Long): String =
    String.format(Locale.ROOT, "%d:%02d.%d", ms / 60_000, (ms / 1_000) % 60, (ms / 100) % 10)