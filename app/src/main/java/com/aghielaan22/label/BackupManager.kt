package com.aghielaan22.label

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ParsedBackup(
    val liked: List<Song>,
    val playlists: List<Playlist>,
    val theme: ThemeMode?,
    val audioQuality: AudioQuality?,
    val fadeSeconds: Int?
)

data class RestoreResult(val likedAdded: Int, val playlistsChanged: Int)

object BackupManager {

    private const val FORMAT_VERSION = 1
    private const val MAX_CHARS = 10_000_000

    fun createBackupJson(): String {
        val playlists = JSONArray()
        PlaylistRepository.playlists.forEach { p ->
            playlists.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("songs", p.songs.toJsonArray())
                }
            )
        }

        val root = JSONObject().apply {
            put("app", "LAbel")
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("liked", LibraryRepository.likedSongs.toList().toJsonArray())
            put("playlists", playlists)
            put(
                "settings",
                JSONObject().apply {
                    put("theme", AppSettings.themeMode.name)
                    put("audioQuality", AppSettings.audioQuality.name)
                    put("fadeSeconds", PlaybackSettings.fadeSeconds)
                }
            )
        }
        return root.toString(2)
    }

    /**
     * Reads and checks a backup without changing anything.
     * Throws IllegalArgumentException with a friendly message if it can't be used.
     */
    fun parse(json: String): ParsedBackup {
        if (json.length > MAX_CHARS) {
            throw IllegalArgumentException("That file is too large to be a LAbel backup.")
        }
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "LAbel") {
                throw IllegalArgumentException("This isn't a LAbel backup file.")
            }
            if (root.optInt("formatVersion", 0) !in 1..FORMAT_VERSION) {
                throw IllegalArgumentException(
                    "This backup comes from a different version of LAbel and can't be read."
                )
            }

            val playlists = mutableListOf<Playlist>()
            val playlistArray = root.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until playlistArray.length()) {
                val o = playlistArray.getJSONObject(i)
                playlists.add(
                    Playlist(
                        id = o.getString("id"),
                        name = o.getString("name").trim().ifEmpty { "Playlist" }.take(100),
                        songs = cleanSongs(o.optJSONArray("songs"))
                    )
                )
            }

            val settings = root.optJSONObject("settings")
            ParsedBackup(
                liked = cleanSongs(root.optJSONArray("liked")),
                playlists = playlists,
                theme = ThemeMode.entries.firstOrNull { it.name == settings?.optString("theme") },
                audioQuality = AudioQuality.entries.firstOrNull {
                    it.name == settings?.optString("audioQuality")
                },
                fadeSeconds = settings?.optInt("fadeSeconds", -1)?.takeIf { it in listOf(0, 2, 4, 6) }
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file isn't a valid LAbel backup.")
        }
    }

    /** Merges a checked backup into the library. Nothing is deleted. */
    fun restore(backup: ParsedBackup): RestoreResult {
        val likedAdded = LibraryRepository.importSongs(backup.liked)
        val playlistsChanged = PlaylistRepository.importPlaylists(backup.playlists)
        backup.theme?.let { AppSettings.updateThemeMode(it) }
        backup.audioQuality?.let { AppSettings.updateAudioQuality(it) }
        backup.fadeSeconds?.let { PlaybackSettings.updateFadeSeconds(it) }
        return RestoreResult(likedAdded, playlistsChanged)
    }

    // Only keep songs that point to YouTube, and images served over HTTPS
    private fun cleanSongs(array: JSONArray?): List<Song> {
        if (array == null) return emptyList()
        return parseSongs(array)
            .filter { isYouTubeUrl(it.id) }
            .map { it.copy(thumbnailUrl = it.thumbnailUrl?.takeIf { url -> url.startsWith("https://") }) }
            .distinctBy { it.id }
    }

    private fun isYouTubeUrl(url: String): Boolean =
        url.startsWith("https://www.youtube.com/") ||
                url.startsWith("https://music.youtube.com/") ||
                url.startsWith("https://youtube.com/")
}