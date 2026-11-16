package com.aghielaan22.label

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HomeSection(val title: String, val songs: List<Song>)

object HomeRepository {

    // Shelf title to search query. Change these to whatever you like!
    private val shelves = listOf(
        "Trending now" to "trending songs",
        "Chill vibes" to "chill songs",
        "Workout" to "workout songs",
        "Throwback hits" to "throwback hits"
    )

    val sections = mutableStateListOf<HomeSection>()

    var isLoading by mutableStateOf(false)
        private set

    private var loaded = false

    suspend fun loadIfNeeded(force: Boolean = false) {
        if (isLoading || (loaded && !force)) return
        isLoading = true
        sections.clear()
        try {
            for ((title, query) in shelves) {
                try {
                    val songs = withContext(Dispatchers.IO) {
                        MusicRepository.searchSongs(query)
                    }.distinctBy { it.id }.take(15)
                    if (songs.isNotEmpty()) sections.add(HomeSection(title, songs))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Skip a shelf that fails; the others can still load
                }
            }
            loaded = sections.isNotEmpty()
        } finally {
            isLoading = false
        }
    }
}