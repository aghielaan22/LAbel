package com.aghielaan22.label

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

private val searchCategories = listOf(SearchFilter.Songs, SearchFilter.Artists, SearchFilter.Albums, SearchFilter.Videos)

internal class SearchTabState(
    initialQuery: String = "",
    initialFilter: SearchFilter = SearchFilter.All
) {
    var query by mutableStateOf(initialQuery)
        private set
    var filter by mutableStateOf(initialFilter)
    var request by mutableStateOf(SearchRequestState(initialQuery.trim()))
        private set

    fun updateQuery(value: String) {
        query = value
        val trimmed = value.trim()
        if (request.query != trimmed) request = SearchRequestState(trimmed)
    }

    companion object {
        val Saver = listSaver<SearchTabState, String>(
            save = { listOf(it.query, it.filter.name) },
            restore = { SearchTabState(it[0], SearchFilter.valueOf(it[1])) }
        )
    }
}

internal class SearchRequestState(val query: String) {
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var videos by mutableStateOf<List<Song>>(emptyList())
        private set
    var failed by mutableStateOf<Map<SearchFilter, String>>(emptyMap())
        private set
    var pending by mutableStateOf(if (query.isBlank()) emptySet() else searchCategories.toSet())
        private set

    fun prepareRetry() {
        pending = failed.keys.toSet()
        failed = emptyMap()
    }

    suspend fun loadPending() = coroutineScope {
        // Each result is published as soon as it arrives, with songs queued first.
        pending.forEach { category ->
            launch {
                try {
                    when (category) {
                        SearchFilter.Songs -> songs = runInterruptible(Dispatchers.IO) { MusicRepository.searchSongs(query) }
                        SearchFilter.Artists -> artists = runInterruptible(Dispatchers.IO) { MusicRepository.searchArtists(query) }
                        SearchFilter.Albums -> albums = runInterruptible(Dispatchers.IO) { MusicRepository.searchAlbums(query) }
                        SearchFilter.Videos -> videos = runInterruptible(Dispatchers.IO) { MusicRepository.searchVideos(query) }
                        SearchFilter.All -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = failed + (category to friendlyMessage(e))
                }
                // Cancellation leaves this category pending so it can resume on re-entry.
                pending = pending - category
            }
        }
    }

    fun hasResults(filter: SearchFilter): Boolean = when (filter) {
        SearchFilter.All -> listOf(songs, artists, albums, videos).any { it.isNotEmpty() }
        SearchFilter.Songs -> songs.isNotEmpty()
        SearchFilter.Artists -> artists.isNotEmpty()
        SearchFilter.Albums -> albums.isNotEmpty()
        SearchFilter.Videos -> videos.isNotEmpty()
    }
}