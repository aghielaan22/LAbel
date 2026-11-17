package com.aghielaan22.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumScreen(album: Album, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var tracks by remember(album.id) { mutableStateOf<List<Song>?>(null) }
    var error by remember(album.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(album.id) {
        try {
            tracks = withContext(Dispatchers.IO) { MusicRepository.getAlbumTracks(album.id) }
                .map { it.copy(thumbnailUrl = it.thumbnailUrl ?: album.thumbnailUrl) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load this album"
        }
    }

    val loadedTracks = tracks
    val loadError = error

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = album.title, onBack = onBack)

        LazyColumn {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = album.artworkUrl(600),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    if (album.artist.isNotBlank()) {
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                Navigator.open(
                                    DetailPage.ArtistPage(
                                        Artist(name = primaryArtistName(album.artist))
                                    )
                                )
                            }
                        )
                    }
                }
            }

            when {
                loadError != null -> item {
                    Text(
                        text = loadError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                loadedTracks == null -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }

                else -> {
                    item { PlayButtons(loadedTracks) }
                    itemsIndexed(loadedTracks, key = { _, s -> s.id }) { index, song ->
                        SongRow(song) { PlayerController.playQueue(loadedTracks, index) }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var details by remember(artist.name) { mutableStateOf<ArtistDetails?>(null) }
    var error by remember(artist.name) { mutableStateOf<String?>(null) }

    LaunchedEffect(artist.name) {
        try {
            details = withContext(Dispatchers.IO) { MusicRepository.getArtistDetails(artist.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load this artist"
        }
    }

    val loaded = details
    val loadError = error

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = artist.name, onBack = onBack)

        LazyColumn {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtistAvatar(artist, 120.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                loadError != null -> item {
                    Text(
                        text = loadError,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                loaded == null -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }

                loaded.songs.isEmpty() && loaded.albums.isEmpty() -> item {
                    Text(
                        text = "Nothing found for this artist.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                else -> {
                    if (loaded.songs.isNotEmpty()) {
                        val songs = loaded.songs.take(15)
                        item { PlayButtons(songs) }
                        item { SectionHeader("Top songs") }
                        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                            SongRow(song) { PlayerController.playQueue(songs, index) }
                        }
                    }
                    if (loaded.albums.isNotEmpty()) {
                        item { SectionHeader("Albums") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(loaded.albums, key = { it.id }) { album ->
                                    AlbumCard(album)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}