package com.aghielaan22.label

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible

internal data class ArtworkRefreshResult(
    val updated: Int,
    val unmatched: Int,
    val stopped: Boolean,
    val error: String?
) {
    fun summary(): String {
        val count = when (updated) {
            0 -> "No covers changed."
            1 -> "Updated 1 cover."
            else -> "Updated $updated covers."
        }
        return buildString {
            append(count)
            if (unmatched > 0) append(" No exact cover match for ${songCountText(unmatched)}.")
            if (error != null) append(" $error")
            else if (stopped) append(" Artwork refresh stopped.")
        }
    }
}

internal object PlaylistArtwork {
    private val searchCovers = LruCache<String, String>(1000)

    /** Only YouTube Music song search calls this. Generic video thumbnails are never cached here. */
    fun rememberSearchCovers(songs: List<Song>) {
        for (song in songs) {
            val id = YouTubeArtworkKey.fromUrl(song.id) ?: continue
            val cover = song.thumbnailUrl?.takeIf { it.isNotBlank() } ?: continue
            searchCovers.put(id, cover)
        }
    }

    fun useKnownCover(song: Song): Song {
        val id = YouTubeArtworkKey.fromUrl(song.id) ?: return song
        val cover = searchCovers.get(id) ?: return song
        return if (song.thumbnailUrl == cover) song else song.copy(thumbnailUrl = cover)
    }

    suspend fun refresh(
        playlistId: String,
        shouldStop: () -> Boolean,
        onProgress: (done: Int, total: Int) -> Unit
    ): ArtworkRefreshResult {
        val songs = PlaylistRepository.get(playlistId)?.songs.orEmpty()
        val tracks = songs.mapNotNull { song -> YouTubeArtworkKey.fromUrl(song.id)?.let { it to song } }
            .distinctBy { it.first }
        val pending = LinkedHashMap<String, String>()
        var updated = 0
        var unmatched = 0
        var error: String? = null
        var stopped = false

        fun saveBatch() {
            if (pending.isEmpty()) return
            updated += PlaylistRepository.updateArtwork(playlistId, pending)
            pending.clear()
        }

        onProgress(0, tracks.size)
        try {
            for ((index, track) in tracks.withIndex()) {
                if (shouldStop()) {
                    stopped = true
                    break
                }
                val (id, song) = track
                var cover = searchCovers.get(id)
                val searched = cover == null
                if (searched) {
                    val artist = song.artist.removeSuffix(" - Topic")
                        .takeUnless { it.equals("Unknown artist", ignoreCase = true) }.orEmpty()
                    val query = listOf(song.title, artist).filter { it.isNotBlank() }.joinToString(" ")
                    runInterruptible(Dispatchers.IO) { MusicRepository.searchSongs(query) }
                    // Search indexed its results by video ID. A same-name recording is not a match.
                    cover = searchCovers.get(id)
                }
                if (cover != null) pending[id] = cover else unmatched++
                if (pending.size >= 12) saveBatch()
                onProgress(index + 1, tracks.size)
                if (searched && index < tracks.lastIndex && !shouldStop()) delay(300)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendlyMessage(e)
        } finally {
            saveBatch()
        }
        return ArtworkRefreshResult(updated, unmatched, stopped, error)
    }
}