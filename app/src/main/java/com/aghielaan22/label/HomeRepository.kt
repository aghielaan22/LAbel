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

    // Shelf title to search query. Add your own favorites here!
    private val shelfPool = listOf(
        "Trending now" to "trending songs",
        "Chill vibes" to "chill songs",
        "Workout" to "workout songs",
        "Throwback hits" to "throwback hits",
        "Focus" to "focus music",
        "Feel-good" to "feel good songs",
        "Late night" to "late night songs",
        "Road trip" to "road trip songs",
        "Acoustic" to "acoustic songs",
        "Party" to "party songs",
        "Hip-hop" to "hip hop hits",
        "Pop hits" to "pop hits",
        "R&B" to "r&b songs",
        "Rock classics" to "rock classics",
        "Indie" to "indie songs",
        "Dance" to "dance hits"
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
            // Shelves built from what you play and like
            val playedSeed = HistoryRepository.recentSongs.take(5).toList().randomOrNull()
            val likedSeed = LibraryRepository.likedSongs.toList()
                .filter { it.id != playedSeed?.id }
                .randomOrNull()

            playedSeed?.let { addSimilarShelf("Because you played ${it.title}", it) }
            likedSeed?.let { addSimilarShelf("Because you liked ${it.title}", it) }

            // "Trending now" every time, plus three random moods
            val chosen = listOf(shelfPool.first()) + shelfPool.drop(1).shuffled().take(3)
            for ((title, query) in chosen) {
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

    private suspend fun addSimilarShelf(title: String, seed: Song) {
        try {
            val similar = withContext(Dispatchers.IO) {
                MusicRepository.getRadio(seed)
            }.take(15)
            if (similar.isNotEmpty()) sections.add(HomeSection(title, similar))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Skip this shelf if it fails
        }
    }
}