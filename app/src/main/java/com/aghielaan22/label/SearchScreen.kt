package com.aghielaan22.label

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class SearchFilter(val label: String) {
    All("All"),
    Songs("Songs"),
    Artists("Artists"),
    Albums("Albums"),
    Videos("Videos")
}

/** Runs [block] off the main thread; returns null instead of failing. */
private suspend fun <T> loadQuietly(block: () -> T): T? =
    try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.All) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var videos by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchedQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(query) {
        val q = query
        if (q.isBlank()) {
            songs = emptyList()
            videos = emptyList()
            albums = emptyList()
            artists = emptyList()
            error = null
            searchedQuery = ""
            return@LaunchedEffect
        }
        delay(500)
        isLoading = true
        error = null
        songs = emptyList()
        videos = emptyList()
        albums = emptyList()
        artists = emptyList()
        try {
            // Songs first, so they appear while the rest is still loading
            try {
                songs = withContext(Dispatchers.IO) { MusicRepository.searchSongs(q) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendlyMessage(e)
            }
            artists = loadQuietly { MusicRepository.searchArtists(q) } ?: emptyList()
            albums = loadQuietly { MusicRepository.searchAlbums(q) } ?: emptyList()
            videos = loadQuietly { MusicRepository.searchVideos(q) } ?: emptyList()
            searchedQuery = q
        } finally {
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs or artists") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    SearchHistoryRepository.add(query)
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (query.isNotBlank()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        if (isLoading || PlayerController.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        (error ?: PlayerController.errorMessage)?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (query.isBlank()) {
            RecentSearches(onPick = { query = it })
        } else {
            val nothingFound = !isLoading && error == null && searchedQuery == query &&
                    songs.isEmpty() && videos.isEmpty() && albums.isEmpty() && artists.isEmpty()
            SearchResults(
                query = query,
                filter = filter,
                songs = songs,
                videos = videos,
                albums = albums,
                artists = artists,
                nothingFound = nothingFound,
                onShowAll = { filter = it }
            )
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    filter: SearchFilter,
    songs: List<Song>,
    videos: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    nothingFound: Boolean,
    onShowAll: (SearchFilter) -> Unit
) {
    if (nothingFound) {
        Text(
            text = "No results for \"$query\".",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    fun play(list: List<Song>, index: Int) {
        SearchHistoryRepository.add(query)
        if (AppSettings.autoplay) {
            // Start with just this song, and let autoplay add similar ones
            PlayerController.playQueue(listOf(list[index]), 0)
        } else {
            PlayerController.playQueue(list, index)
        }
    }

    LazyColumn {
        when (filter) {
            SearchFilter.All -> {
                // If the search exactly matches an artist, that artist is the top result
                val topArtist = artists.firstOrNull()
                    ?.takeIf { it.name.equals(query.trim(), ignoreCase = true) }
                val topSong = songs.firstOrNull() ?: videos.firstOrNull()

                if (topArtist != null) {
                    item(key = "top-header") { SectionHeader("Top result") }
                    item(key = "top") { TopArtistCard(topArtist) }
                } else if (topSong != null) {
                    val topIsSong = songs.isNotEmpty()
                    item(key = "top-header") { SectionHeader("Top result") }
                    item(key = "top") {
                        TopSongCard(song = topSong, kind = if (topIsSong) "Song" else "Video") {
                            if (topIsSong) play(songs, 0) else play(videos, 0)
                        }
                    }
                }

                if (songs.isNotEmpty()) {
                    item(key = "songs-header") { SectionHeader("Songs") }
                    itemsIndexed(songs.take(5), key = { _, s -> "song:${s.id}" }) { index, song ->
                        SongRow(song) { play(songs, index) }
                    }
                    if (songs.size > 5) {
                        item(key = "songs-more") {
                            TextButton(
                                onClick = { onShowAll(SearchFilter.Songs) },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) { Text("Show all songs") }
                        }
                    }
                }

                if (artists.isNotEmpty()) {
                    item(key = "artists-header") { SectionHeader("Artists") }
                    items(artists.take(3), key = { "artist:${it.name}" }) { ArtistRow(it) }
                    if (artists.size > 3) {
                        item(key = "artists-more") {
                            TextButton(
                                onClick = { onShowAll(SearchFilter.Artists) },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) { Text("Show all artists") }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item(key = "albums-header") { SectionHeader("Albums") }
                    item(key = "albums-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albums.take(10), key = { it.id }) { AlbumCard(it) }
                        }
                    }
                }

                if (videos.isNotEmpty()) {
                    item(key = "videos-header") { SectionHeader("Videos") }
                    itemsIndexed(videos.take(4), key = { _, v -> "video:${v.id}" }) { index, video ->
                        SongRow(video, wideArtwork = true) { play(videos, index) }
                    }
                    if (videos.size > 4) {
                        item(key = "videos-more") {
                            TextButton(
                                onClick = { onShowAll(SearchFilter.Videos) },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) { Text("Show all videos") }
                        }
                    }
                }
            }

            SearchFilter.Songs -> {
                itemsIndexed(songs, key = { _, s -> "song:${s.id}" }) { index, song ->
                    SongRow(song) { play(songs, index) }
                }
            }

            SearchFilter.Artists -> {
                items(artists, key = { "artist:${it.name}" }) { ArtistRow(it) }
            }

            SearchFilter.Albums -> {
                items(albums, key = { "album:${it.id}" }) { AlbumRow(it) }
            }

            SearchFilter.Videos -> {
                itemsIndexed(videos, key = { _, v -> "video:${v.id}" }) { index, video ->
                    SongRow(video, wideArtwork = true) { play(videos, index) }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopSongCard(song: Song, kind: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.artworkUrl(400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    text = "$kind · ${song.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopArtistCard(artist: Artist) {
    Card(
        onClick = { Navigator.open(DetailPage.ArtistPage(artist)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistAvatar(artist, 88.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentSearches(onPick: (String) -> Unit) {
    val recent = SearchHistoryRepository.recent

    if (recent.isEmpty()) {
        Text(
            text = "Search for songs or artists.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Recent searches", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { SearchHistoryRepository.clear() }) { Text("Clear") }
    }

    LazyColumn {
        items(recent.toList(), key = { it }) { item ->
            ListItem(
                modifier = Modifier.clickable { onPick(item) },
                leadingContent = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                headlineContent = { Text(item, maxLines = 1) },
                trailingContent = {
                    IconButton(onClick = { SearchHistoryRepository.remove(item) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove from history")
                    }
                }
            )
        }
    }
}