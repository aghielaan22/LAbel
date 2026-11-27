package com.aghielaan22.label

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?.substringBeforeLast('.')

@Composable
fun ImportScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var link by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun importLink() {
        val url = PlaylistImporter.normalizePlaylistUrl(link)
        if (url == null) {
            message = "That doesn't look like a YouTube or YouTube Music playlist link."
            return
        }
        val fromMusic = Uri.parse(link.trim()).host.equals("music.youtube.com", ignoreCase = true)
        scope.launch {
            running = true
            stopRequested = false
            message = null
            progress = 0f
            progressText = "Loading the playlist..."
            try {
                val (name, songs) = withContext(Dispatchers.IO) { MusicRepository.importPlaylist(url) }
                if (songs.isEmpty()) {
                    message = "That playlist is empty or private."
                } else {
                    val initial = if (fromMusic) songs.map(PlaylistArtwork::useKnownCover) else songs
                    val playlist = PlaylistRepository.createWithSongs(name, initial)
                    val imported = "Imported \"$name\" with ${songs.size} songs."
                    message = imported
                    link = ""
                    if (fromMusic) {
                        val result = PlaylistArtwork.refresh(playlist.id, { stopRequested }) { done, total ->
                            progress = if (total > 0) done / total.toFloat() else 1f
                            progressText = "Checking artwork ($done/$total)"
                        }
                        message = "$imported ${result.summary()}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendlyMessage(e)
            } finally {
                running = false
                progressText = ""
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            running = true
            stopRequested = false
            message = null
            progress = 0f
            progressText = "Reading the file..."
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IOException("Couldn't open the file")
                }
                if (text.length > 5_000_000) {
                    message = "That file is too large."
                    return@launch
                }
                val queries = PlaylistImporter.parseTracks(text)
                if (queries.isEmpty()) {
                    message = "No songs found in that file."
                    return@launch
                }
                val found = PlaylistImporter.matchTracks(
                    queries = queries,
                    shouldStop = { stopRequested },
                    onProgress = { done, total, matched ->
                        progress = done / total.toFloat()
                        progressText = "Matched $matched of $total songs"
                    }
                )
                if (found.isEmpty()) {
                    message = "None of the songs could be matched."
                } else {
                    val name = displayName(context, uri) ?: "Imported playlist"
                    PlaylistRepository.createWithSongs(name, found)
                    val missed = queries.size - found.size
                    message = "Imported \"$name\" with ${found.size} songs" +
                            if (missed > 0 && !stopRequested) " ($missed couldn't be matched)." else "."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't read that file."
            } finally {
                running = false
                progressText = ""
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = "Import playlist", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsGroup("From a link") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Paste a YouTube or YouTube Music playlist link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Playlist link") },
                        singleLine = true,
                        enabled = !running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Button(
                        onClick = { importLink() },
                        enabled = !running && link.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Import") }
                }
            }

            SettingsGroup("From a file") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Choose a CSV (like a Spotify export, with Track Name and Artist columns) " +
                                "or a text file with one song per line. Each song is searched for one by one, " +
                                "so long lists take a few minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { fileLauncher.launch(arrayOf("*/*")) },
                            enabled = !running
                        ) { Text("Choose file") }
                        if (running && progressText.startsWith("Matched")) {
                            OutlinedButton(onClick = { stopRequested = true }) {
                                Text("Stop and keep matches")
                            }
                        }
                    }
                }
            }

            if (running) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(progressText, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    if (progressText.startsWith("Checking artwork")) {
                        Text(
                            "The playlist is saved. You can stop checking artwork at any time.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        OutlinedButton(onClick = { stopRequested = true }, enabled = !stopRequested) {
                            Text(if (stopRequested) "Stopping…" else "Stop artwork check")
                        }
                    }
                }
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}