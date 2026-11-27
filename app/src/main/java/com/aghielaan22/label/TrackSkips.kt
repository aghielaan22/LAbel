package com.aghielaan22.label

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val MIN_SECTION_MS = 1_000L

internal data class SkipSection(val startMs: Long, val endMs: Long) {
    fun fits(durationMs: Long): Boolean =
        startMs >= 0 && endMs <= durationMs &&
                startMs <= endMs && endMs - startMs >= MIN_SECTION_MS
}

internal data class TrackSkipSettings(
    val enabled: Boolean = true,
    val sections: List<SkipSection> = emptyList()
)

internal object TrackSkips {
    private val revision = MutableStateFlow(0L)
    val changes = revision

    private fun preferences(context: Context) =
        context.getSharedPreferences("label_track_skips", Context.MODE_PRIVATE)

    fun load(context: Context, songId: String): TrackSkipSettings {
        val raw = preferences(context).getString(songId, null) ?: return TrackSkipSettings()
        return runCatching {
            val json = JSONObject(raw)
            val entries = json.getJSONArray("sections")
            val sections = buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.getJSONObject(index)
                    add(SkipSection(item.getLong("startMs"), item.getLong("endMs")))
                }
            }
            TrackSkipSettings(json.optBoolean("enabled", true), sections)
        }.getOrElse { TrackSkipSettings() }
    }

    fun save(context: Context, songId: String, settings: TrackSkipSettings) {
        val sections = JSONArray()
        settings.sections.forEach {
            sections.put(
                JSONObject().put("startMs", it.startMs).put("endMs", it.endMs)
            )
        }
        val json = JSONObject().put("enabled", settings.enabled).put("sections", sections)
        preferences(context).edit().putString(songId, json.toString()).apply()
        revision.update { it + 1 }
    }

    fun normalize(sections: List<SkipSection>, durationMs: Long): List<SkipSection> {
        val merged = mutableListOf<SkipSection>()
        for (section in sections.filter { it.fits(durationMs) }.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && section.startMs <= last.endMs) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, section.endMs))
            } else {
                merged.add(section)
            }
        }
        val keptMs = durationMs - merged.sumOf { it.endMs - it.startMs }
        return if (keptMs >= MIN_SECTION_MS) merged else emptyList()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class TrackSkipPlayback(
    context: Context,
    private val player: ExoPlayer
) {
    private data class Schedule(
        val entryId: String,
        val itemIndex: Int,
        val sections: List<SkipSection>
    )

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messages = mutableListOf<PlayerMessage>()
    private var installed: Schedule? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED
                )
            ) sync()
        }
    }

    init {
        player.addListener(listener)
        scope.launch {
            combine(
                TrackSkips.changes,
                TrackLoops.active,
                snapshotFlow { StudioController.active }
            ) { _, _, _ -> Unit }.collect { sync() }
        }
    }

    private fun suspended(): Boolean =
        StudioController.active || TrackLoops.active.value != null

    private fun desiredSchedule(): Schedule? {
        if (suspended() || !player.isCurrentMediaItemSeekable || player.duration <= 0) return null
        val item = player.currentMediaItem ?: return null
        val songId = item.localConfiguration?.uri?.toString() ?: return null
        val saved = TrackSkips.load(app, songId)
        if (!saved.enabled) return null

        val sections = TrackSkips.normalize(saved.sections, player.duration)
        if (sections.isEmpty()) return null
        return Schedule(item.mediaId, player.currentMediaItemIndex, sections)
    }

    private fun sync() {
        val desired = desiredSchedule()
        if (desired != installed) {
            cancelMessages()
            installed = desired
            if (desired != null) {
                try {
                    for (section in desired.sections) {
                        messages += player.createMessage { _, _ ->
                            skipIfInside(desired, section, earlyToleranceMs = 40L)
                        }.setLooper(player.applicationLooper)
                            .setPosition(desired.itemIndex, section.startMs)
                            .setDeleteAfterDelivery(false)
                            .send()
                    }
                } catch (e: RuntimeException) {
                    Log.w("TrackSkipPlayback", "Could not schedule skip sections", e)
                    cancelMessages()
                }
            }
        }

        val schedule = installed ?: return
        val position = player.currentPosition
        schedule.sections.firstOrNull {
            position >= it.startMs && position < it.endMs
        }?.let { skipIfInside(schedule, it) }
    }

    private fun skipIfInside(
        schedule: Schedule,
        section: SkipSection,
        earlyToleranceMs: Long = 0L
    ) {
        if (installed !== schedule || suspended() || !player.isPlaying ||
            player.currentMediaItem?.mediaId != schedule.entryId ||
            player.duration < section.endMs
        ) return

        val position = player.currentPosition
        if (position >= section.startMs - earlyToleranceMs && position < section.endMs) {
            player.seekTo(section.endMs)
        }
    }

    private fun cancelMessages() {
        messages.forEach { it.cancel() }
        messages.clear()
    }

    fun close() {
        player.removeListener(listener)
        scope.cancel()
        cancelMessages()
        installed = null
    }
}

@Composable
@kotlin.OptIn(ExperimentalMaterial3Api::class)
internal fun TrackSkipDialog(song: Song, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val duration = PlayerController.skipEndLimit(song.id)

    if (duration < 2 * MIN_SECTION_MS) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Skip sections") },
            text = {
                Text(
                    if (StudioController.active) "Leave Studio to edit skip sections."
                    else "Wait for a seekable song to load, then open this editor again."
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
        return
    }

    var saved by remember(song.id) { mutableStateOf(TrackSkips.load(app, song.id)) }
    var draft by remember(song.id, duration) {
        val start = PlayerController.position().coerceIn(0L, duration - MIN_SECTION_MS)
        mutableStateOf(SkipSection(start, (start + 5_000L).coerceAtMost(duration)))
    }
    var message by remember(song.id) { mutableStateOf<String?>(null) }

    fun save(settings: TrackSkipSettings) {
        TrackSkips.save(app, song.id, settings)
        saved = settings
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip sections") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skip saved sections", Modifier.weight(1f))
                    Switch(
                        checked = saved.enabled,
                        onCheckedChange = {
                            save(saved.copy(enabled = it))
                            message = null
                        }
                    )
                }

                Text("From \${skipTime(draft.startMs)} to \${skipTime(draft.endMs)}")
                RangeSlider(
                    value = draft.startMs.toFloat()..draft.endMs.toFloat(),
                    valueRange = 0f..duration.toFloat(),
                    onValueChange = {
                        draft = SkipSection(
                            it.start.toLong().coerceIn(0L, duration),
                            it.endInclusive.toLong().coerceIn(0L, duration)
                        )
                        message = null
                    }
                )

                Row {
                    TextButton(onClick = {
                        draft = draft.copy(
                            startMs = PlayerController.position().coerceIn(0L, draft.endMs)
                        )
                        message = null
                    }) { Text("Set start here") }
                    TextButton(onClick = {
                        draft = draft.copy(
                            endMs = PlayerController.position().coerceIn(draft.startMs, duration)
                        )
                        message = null
                    }) { Text("Set end here") }
                }

                TextButton(enabled = draft.fits(duration), onClick = {
                    val sections = TrackSkips.normalize(saved.sections + draft, duration)
                    if (sections.isEmpty()) {
                        message = "Leave at least one second of the song playable."
                    } else {
                        save(saved.copy(sections = sections))
                        message = "Skip section saved. Overlapping sections are combined."
                    }
                }) { Text("Add skip section") }

                saved.sections.forEachIndexed { index, section ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "\${skipTime(section.startMs)} – \${skipTime(section.endMs)}",
                            Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            save(saved.copy(sections = saved.sections.filterIndexed { i, _ -> i != index }))
                            message = null
                        }) { Text("Remove") }
                    }
                }

                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun skipTime(ms: Long): String =
    String.format(Locale.ROOT, "%d:%02d.%d", ms / 60_000, (ms / 1_000) % 60, (ms / 100) % 10)