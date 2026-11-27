package com.aghielaan22.label

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ShareCardEditor(song: Song, excerpt: List<String> = emptyList(), onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var format by remember(song.id) { mutableStateOf(ShareCardFormat.Square) }
    var showExcerpt by remember(song.id, excerpt) { mutableStateOf(excerpt.isNotEmpty()) }
    var cover by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    var loaded by remember(song.id) { mutableStateOf(false) }
    var sharing by remember(song.id) { mutableStateOf(false) }
    var shareError by remember(song.id) { mutableStateOf(false) }
    val lines = if (showExcerpt) excerpt else emptyList()

    LaunchedEffect(song) {
        cover = loadCardCover(app, song)
        loaded = true
    }

    var preview by remember(song, format, lines, loaded, cover) { mutableStateOf<ImageBitmap?>(null) }
    var previewFailed by remember(song, format, lines, loaded, cover) { mutableStateOf(false) }
    LaunchedEffect(song, format, lines, loaded, cover) {
        if (loaded) {
            try {
                preview = withContext(Dispatchers.Default) {
                    renderSongCard(song, format, lines, cover, width = 432).asImageBitmap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                previewFailed = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!sharing) onDismiss() },
        title = { Text("Share card") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareCardFormat.entries.forEach { option ->
                        FilterChip(
                            selected = format == option,
                            onClick = {
                                format = option
                                shareError = false
                            },
                            enabled = !sharing,
                            label = { Text(option.label) }
                        )
                    }
                }
                if (excerpt.isNotEmpty()) {
                    FilterChip(
                        selected = showExcerpt,
                        onClick = {
                            showExcerpt = !showExcerpt
                            shareError = false
                        },
                        enabled = !sharing,
                        label = { Text("Include lyrics") }
                    )
                }
                Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
                    val image = preview
                    when {
                        image != null -> Image(
                            image, contentDescription = "${format.label} preview for ${song.title}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                        )
                        previewFailed -> Text("Couldn't render the preview. Try switching formats.")
                        else -> CircularProgressIndicator()
                    }
                }
                if (shareError) {
                    Text("Couldn't share the card. Try again.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = preview != null && !sharing,
                onClick = {
                    val selectedFormat = format
                    val selectedLines = lines
                    val artwork = cover
                    sharing = true
                    shareError = false
                    scope.launch {
                        try {
                            val intent = songCardIntent(app, song, selectedFormat, selectedLines, artwork)
                            context.startActivity(Intent.createChooser(intent, "Share card"))
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            shareError = true
                        } finally {
                            sharing = false
                        }
                    }
                }
            ) { Text(if (sharing) "Preparing…" else "Share PNG") }
        },
        dismissButton = { TextButton(enabled = !sharing, onClick = onDismiss) { Text("Cancel") } }
    )
}