package com.aghielaan22.label

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class TrackQuery(val title: String, val artist: String)

object PlaylistImporter {

    /** Accepts YouTube and YouTube Music playlist links; returns a clean address or null. */
    fun normalizePlaylistUrl(input: String): String? {
        val uri = Uri.parse(input.trim())
        val host = uri.host?.lowercase() ?: return null
        if (!(host.endsWith("youtube.com") || host == "youtu.be")) return null
        val listId = uri.getQueryParameter("list") ?: return null
        if (listId.isBlank()) return null
        return "https://www.youtube.com/playlist?list=$listId"
    }

    /** Reads a CSV with a header row, or a text file with one song per line. */
    fun parseTracks(text: String): List<TrackQuery> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val titleIndex = header.indexOfFirst {
            it in setOf("track name", "track", "title", "song", "name", "track_name")
        }
        val artistIndex = header.indexOfFirst { it.startsWith("artist") }

        if (titleIndex >= 0) {
            return lines.drop(1).mapNotNull { line ->
                val cells = splitCsvLine(line)
                val title = cells.getOrNull(titleIndex)?.trim().orEmpty()
                if (title.isEmpty()) {
                    null
                } else {
                    val artist = if (artistIndex >= 0) {
                        cells.getOrNull(artistIndex).orEmpty()
                            .substringBefore(";").substringBefore(",").trim()
                    } else {
                        ""
                    }
                    TrackQuery(title, artist)
                }
            }
        }
        // No header: every line is a search on its own
        return lines.map { TrackQuery(it.trim(), "") }
    }

    /** Searches for each track in turn and returns the songs it matched. */
    suspend fun matchTracks(
        queries: List<TrackQuery>,
        shouldStop: () -> Boolean,
        onProgress: (done: Int, total: Int, matched: Int) -> Unit
    ): List<Song> {
        val found = mutableListOf<Song>()
        queries.forEachIndexed { index, query ->
            if (shouldStop()) return found

            val text = if (query.artist.isBlank()) query.title else "${query.title} ${query.artist}"
            val results = try {
                withContext(Dispatchers.IO) { MusicRepository.searchSongs(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            pickBest(query, results)?.let { found.add(it) }
            onProgress(index + 1, queries.size, found.size)
            delay(250) // be gentle with YouTube
        }
        return found
    }

    private fun pickBest(query: TrackQuery, results: List<Song>): Song? {
        if (results.isEmpty()) return null
        // A plain "Artist - Title" line: trust the search ranking
        if (query.artist.isBlank()) return results.first()

        val title = normalize(query.title)
        val artist = normalize(query.artist)
        val scored = results.map { song ->
            val songTitle = normalize(song.title)
            val songArtist = normalize(song.artist)
            var score = 0
            if (title.isNotEmpty() && (songTitle.contains(title) || title.contains(songTitle))) score += 2
            if (artist.isNotEmpty() && (songArtist.contains(artist) || artist.contains(songArtist))) score += 1
            song to score
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= 2) best.first else null
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    cells.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }
}