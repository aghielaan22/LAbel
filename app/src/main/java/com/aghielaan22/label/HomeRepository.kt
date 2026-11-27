package com.aghielaan22.label

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeSection(val title: String, val songs: List<Song>)

object HomeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    fun requestLoad(force: Boolean = false) {
        scope.launch { loadIfNeeded(force) }
    }

    private suspend fun loadIfNeeded(force: Boolean) {
        if (isLoading || (loaded && !force)) return
        isLoading = true
        sections.clear()
        try {
            // Shelves that learn from you
            addMadeForYou()
            addTopArtistShelf()

            // Shelves built from what you played and liked lately
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

    /** A mix of songs similar to your three favorites. */
    private suspend fun addMadeForYou() {
        val seeds = StatsRepository.topSeeds(3)
        if (seeds.isEmpty()) return

        val pool = mutableListOf<Song>()
        for (seed in seeds) {
            try {
                pool += withContext(Dispatchers.IO) { MusicRepository.getRadio(seed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Carry on with the other seeds
            }
        }
        val mix = pool
            .distinctBy { it.id }
            .filterNot { StatsRepository.isDisliked(it.id) }
            .shuffled()
            .take(20)
        if (mix.isNotEmpty()) sections.add(HomeSection("Made for you", mix))
    }

    private suspend fun addTopArtistShelf() {
        val artist = StatsRepository.topArtists(3).randomOrNull() ?: return
        try {
            val details = withContext(Dispatchers.IO) {
                MusicRepository.getArtistDetails(artist.name)
            }
            val songs = details.songs
                .filterNot { StatsRepository.isDisliked(it.id) }
                .take(15)
            if (songs.isNotEmpty()) sections.add(HomeSection("More from ${artist.name}", songs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Skip this shelf if it fails
        }
    }

    private suspend fun addSimilarShelf(title: String, seed: Song) {
        try {
            val similar = withContext(Dispatchers.IO) {
                MusicRepository.getRadio(seed)
            }.filterNot { StatsRepository.isDisliked(it.id) }.take(15)
            if (similar.isNotEmpty()) sections.add(HomeSection(title, similar))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Skip this shelf if it fails
        }
    }
}