package com.aghielaan22.label

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun AudioCacheSettings() {
    val scope = rememberCoroutineScope()
    val usage by PlaybackAudioCache.usage.collectAsState()
    val cached by PlaybackAudioCache.offlineTracks.collectAsState()
    val matchingQuality = remember(cached, AppSettings.audioQuality) {
        cached.count { it.quality == AppSettings.audioQuality }
    }
    var clearing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun changeCache(action: suspend () -> Boolean) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                if (!withContext(NonCancellable) { action() }) message = "Audio cache is unavailable."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Couldn't update the audio cache. Try again."
            } finally {
                busy = false
            }
        }
    }

    SettingsGroup("Audio cache") {
        val description = when (val status = usage) {
            null -> "Checking storage…"
            else -> if (status.available) {
                val used = status.bytes / (1024.0 * 1024.0)
                val count = if (matchingQuality == 1) "1 track" else "$matchingQuality tracks"
                "%.1f MiB used · %s ready offline at this quality".format(used, count)
            } else "Audio cache unavailable"
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ChoiceSetting(
            title = "Storage limit",
            description = "Older audio is removed first as the cache fills.",
            options = audioCacheLimitsMiB.map { it to if (it == 1024) "1 GiB" else "$it MiB" },
            selected = AppSettings.audioCacheLimitMiB,
            onSelect = { limit ->
                if (!busy && limit != AppSettings.audioCacheLimitMiB && usage?.available == true) {
                    if (limit > AppSettings.audioCacheLimitMiB || PlayerController.prepareForCacheEdit()) {
                        changeCache {
                            if (!PlaybackAudioCache.setLimit(limit)) false
                            else {
                                AppSettings.updateAudioCacheLimit(limit)
                                true
                            }
                        }
                    } else message = "Pause playback and leave Studio before changing the cache."
                }
            }
        )
        TextButton(
            onClick = { clearing = true },
            enabled = !busy && (usage?.bytes ?: 0) > 0,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(if (busy) "Updating…" else "Clear audio cache")
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear audio cache?") },
            text = { Text("LAbel will need to stream these tracks again before they can play offline.") },
            confirmButton = {
                TextButton(onClick = {
                    clearing = false
                    if (PlayerController.prepareForCacheEdit()) {
                        changeCache { PlaybackAudioCache.clear() }
                    } else message = "Pause playback and leave Studio before clearing the cache."
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } }
        )
    }
}