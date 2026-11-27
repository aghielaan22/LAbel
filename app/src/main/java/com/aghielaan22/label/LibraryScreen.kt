package com.aghielaan22.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private sealed interface LibraryPage {
    data object Overview : LibraryPage
    data object Liked : LibraryPage
    data class Smart(val rule: SmartPlaylist) : LibraryPage
    data class PlaylistPage(val id: String) : LibraryPage
}

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    var page by remember { mutableStateOf<LibraryPage>(LibraryPage.Overview) }

    when (val current = page) {
        LibraryPage.Overview -> LibraryOverview(
            modifier = modifier,
            onOpenLiked = { page = LibraryPage.Liked },
            onOpenSmart = { page = LibraryPage.Smart(it) },
            onOpenPlaylist = { page = LibraryPage.PlaylistPage(it) }
        )

        LibraryPage.Liked -> LikedSongsPage(
            modifier = modifier,
            onBack = { page = LibraryPage.Overview }
        )

        is LibraryPage.Smart -> SmartPlaylistScreen(
            rule = current.rule,
            modifier = modifier,
            onBack = { page = LibraryPage.Overview }
        )

        is LibraryPage.PlaylistPage -> PlaylistPageScreen(
            id = current.id,
            modifier = modifier,
            onBack = { page = LibraryPage.Overview }
        )
    }
}

@Composable
private fun LibraryOverview(
    modifier: Modifier,
    onOpenLiked: () -> Unit,
    onOpenSmart: (SmartPlaylist) -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        item {
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenLiked),
                leadingContent = {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = { Text("Liked songs") },
                supportingContent = {
                    Text(songCountText(LibraryRepository.likedSongs.size))
                }
            )
        }
        item {
            ListItem(
                modifier = Modifier.clickable { Navigator.open(DetailPage.StatsPage) },
                leadingContent = {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = { Text("Your stats") },
                supportingContent = { Text("Top artists, top songs and listening time") }
            )
        }
        items(SmartPlaylist.entries, key = { "smart:${it.name}" }) { rule ->
            ListItem(
                modifier = Modifier.clickable { onOpenSmart(rule) },
                headlineContent = { Text(rule.title) },
                supportingContent = { Text(rule.subtitle) }
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playlists", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { Navigator.open(DetailPage.ImportPage) }) { Text("Import") }
                    TextButton(onClick = { creating = true }) { Text("New playlist") }
                }            }
        }
        if (PlaylistRepository.playlists.isEmpty()) {
            item {
                Text(
                    text = "Create a playlist to organize your music.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        items(PlaylistRepository.playlists, key = { it.id }) { playlist ->
            ListItem(
                modifier = Modifier.clickable { onOpenPlaylist(playlist.id) },
                leadingContent = { PlaylistCover(playlist) },
                headlineContent = { Text(playlist.name, maxLines = 1) },
                supportingContent = { Text(songCountText(playlist.songs.size)) }
            )
        }
    }

    if (creating) {
        NamePlaylistDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = {
                PlaylistRepository.create(it)
                creating = false
            },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun LikedSongsPage(modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = modifier.fillMaxSize()) {
        PageHeader(title = "Liked songs", onBack = onBack)
        LibraryCollection(
            collectionId = "liked",
            songs = LibraryRepository.likedSongs.toList(),
            emptyMessage = "Songs you like will show up here.",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlaylistPageScreen(id: String, modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var artworkRunning by remember(id) { mutableStateOf(false) }
    var stopArtwork by remember(id) { mutableStateOf(false) }
    var artworkProgress by remember(id) { mutableStateOf<String?>(null) }
    var artworkSummary by remember(id) { mutableStateOf<String?>(null) }

    val playlist = PlaylistRepository.get(id)
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        PageHeader(title = playlist.name, onBack = onBack) {
            if (playlist.songs.size > 1) {
                TextButton(onClick = { reordering = !reordering }) {
                    Text(if (reordering) "Done" else "Reorder")
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Playlist options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Refresh artwork") },
                        enabled = playlist.songs.isNotEmpty() && !artworkRunning,
                        onClick = {
                            menuOpen = false
                            artworkRunning = true
                            stopArtwork = false
                            artworkSummary = null
                            scope.launch {
                                try {
                                    val result = PlaylistArtwork.refresh(id, { stopArtwork }) { done, total ->
                                        artworkProgress = "Checking artwork ($done/$total)"
                                    }
                                    artworkSummary = result.summary()
                                } finally {
                                    artworkRunning = false
                                    artworkProgress = null
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            renaming = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        }
                    )
                }
            }
        }

        if (artworkRunning) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    artworkProgress ?: "Checking artwork…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { stopArtwork = true }, enabled = !stopArtwork) {
                    Text(if (stopArtwork) "Stopping…" else "Stop")
                }
            }
        }
        artworkSummary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }
        if (reordering && playlist.songs.size > 1) {
            ReorderablePlaylistList(playlist)
        } else {
            LibraryCollection(
                collectionId = "playlist:$id",
                songs = playlist.songs,
                emptyMessage = "No songs yet. Use a song's menu to add it to this playlist.",
                modifier = Modifier.weight(1f),
                onRemove = { PlaylistRepository.removeSong(id, it.id) }
            )
        }
    }

    if (renaming) {
        NamePlaylistDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialName = playlist.name,
            onConfirm = {
                PlaylistRepository.rename(id, it)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"${playlist.name}\" will be deleted. Your songs aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    PlaylistRepository.delete(id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun PlayButtons(songs: List<Song>) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { PlayerController.playQueue(songs, 0) },
            enabled = songs.isNotEmpty()
        ) { Text("Play") }
        OutlinedButton(
            onClick = { PlayerController.playQueue(songs.shuffled(), 0) },
            enabled = songs.isNotEmpty()
        ) { Text("Shuffle") }
    }
}

@Composable
private fun PlaylistCover(playlist: Playlist, size: Dp = 52.dp) {
    val art = playlist.songs.firstOrNull()?.thumbnailUrl
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪", style = MaterialTheme.typography.titleLarge)
        }
    }
}