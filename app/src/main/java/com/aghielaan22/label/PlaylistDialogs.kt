package com.aghielaan22.label

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun songCountText(count: Int): String = if (count == 1) "1 song" else "$count songs"

@Composable
fun NamePlaylistDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = ""
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NamePlaylistDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                val playlist = PlaylistRepository.create(name)
                PlaylistRepository.addSong(playlist.id, song)
                Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { creating = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                TextButton(onClick = { creating = true }) { Text("+ New playlist") }
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(PlaylistRepository.playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable {
                                val added = PlaylistRepository.addSong(playlist.id, song)
                                val message = if (added) "Added to ${playlist.name}"
                                else "Already in ${playlist.name}"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(playlist.name, maxLines = 1) },
                            supportingContent = { Text(songCountText(playlist.songs.size)) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}