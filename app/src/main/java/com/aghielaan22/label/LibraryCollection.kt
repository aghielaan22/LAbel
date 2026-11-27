package com.aghielaan22.label

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CollectionSort(val label: String) {
    Original("Original order"), Title("Song title"), Artist("Artist")
}

@Composable
internal fun LibraryCollection(
    collectionId: String,
    songs: List<Song>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    onRemove: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("library_sort", Context.MODE_PRIVATE)
    }
    var sort by remember(collectionId, preferences) {
        val saved = preferences.getString(collectionId, null)
        mutableStateOf(CollectionSort.entries.firstOrNull { it.name == saved } ?: CollectionSort.Original)
    }
    var query by remember(collectionId) { mutableStateOf("") }
    var sortOpen by remember(collectionId) { mutableStateOf(false) }
    var selecting by remember(collectionId) { mutableStateOf(false) }
    var selectedIds by remember(collectionId) { mutableStateOf<Set<String>>(emptySet()) }
    var adding by remember(collectionId) { mutableStateOf(false) }
    val selectedSongs = remember(songs, selectedIds) {
        songs.filter { it.id in selectedIds }.distinctBy { it.id }
    }

    fun exitSelection() {
        selecting = false
        selectedIds = emptySet()
    }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    BackHandler(enabled = selecting, onBack = ::exitSelection)

    val visibleSongs by produceState(songs, songs, query.trim(), sort) {
        val search = query.trim()
        value = withContext(Dispatchers.Default) {
            val matches = if (search.isEmpty()) songs else {
                songs.filter { it.title.contains(search, ignoreCase = true) || it.artist.contains(search, ignoreCase = true) }
            }
            val unique = matches.distinctBy { it.id }
            when (sort) {
                CollectionSort.Original -> unique
                CollectionSort.Title -> unique.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Song::title))
                CollectionSort.Artist -> unique.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER, Song::artist)
                        .thenBy(String.CASE_INSENSITIVE_ORDER, Song::title)
                )
            }
        }
    }

    Column(modifier) {
        if (songs.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find in this collection") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Box {
                    TextButton(onClick = { sortOpen = true }) { Text(sort.label) }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        CollectionSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                sort = option
                                preferences.edit().putString(collectionId, option.name).apply()
                                sortOpen = false
                            })
                        }
                    }
                }
                TextButton(onClick = {
                    if (selecting) exitSelection() else selecting = true
                }) { Text(if (selecting) "Cancel selection" else "Select songs") }
            }
        }

        if (selecting) {
            TextButton(onClick = { adding = true }, enabled = selectedSongs.isNotEmpty()) {
                Text("Add to playlist (${selectedSongs.size})")
            }
        } else {
            PlayButtons(visibleSongs)
        }

        if (visibleSongs.isEmpty()) {
            Text(
                if (songs.isEmpty()) emptyMessage else "No matching songs.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(visibleSongs, key = { _, song -> song.id }) { index, song ->
                    if (selecting) {
                        ListItem(
                            modifier = Modifier.clickable { toggle(song.id) },
                            headlineContent = { Text(song.title, maxLines = 1) },
                            supportingContent = { Text(song.artist, maxLines = 1) },
                            leadingContent = {
                                Checkbox(checked = song.id in selectedIds, onCheckedChange = null)
                            }
                        )
                    } else {
                        SongRow(song, onRemoveFromPlaylist = onRemove?.let { remove -> { remove(song) } }) {
                            PlayerController.playQueue(visibleSongs, index)
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddToPlaylistDialog(
            songs = selectedSongs,
            onAdded = { exitSelection() },
            onDismiss = { adding = false }
        )
    }
}