package com.aghielaan22.label

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(val id: String, val name: String, val songs: List<Song>)

object PlaylistRepository {

    private var file: File? = null

    val playlists = mutableStateListOf<Playlist>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "playlists.json")
        file = f
        if (!f.exists()) return
        try {
            val array = JSONArray(f.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                playlists.add(
                    Playlist(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        songs = parseSongs(o.getJSONArray("songs"))
                    )
                )
            }
        } catch (e: Exception) {
            // Damaged file: start with no playlists
        }
    }

    fun get(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    fun create(name: String): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim(), emptyList())
        playlists.add(playlist)
        save()
        return playlist
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim()) }

    fun delete(id: String) {
        playlists.removeAll { it.id == id }
        save()
    }

    /** Returns false if the song was already in the playlist. */
    fun addSong(id: String, song: Song): Boolean {
        val playlist = get(id) ?: return false
        if (playlist.songs.any { it.id == song.id }) return false
        update(id) { it.copy(songs = it.songs + song) }
        return true
    }

    fun removeSong(id: String, songId: String) =
        update(id) { p -> p.copy(songs = p.songs.filterNot { it.id == songId }) }

    private fun update(id: String, change: (Playlist) -> Playlist) {
        val index = playlists.indexOfFirst { it.id == id }
        if (index < 0) return
        playlists[index] = change(playlists[index])
        save()
    }

    private fun save() {
        val array = JSONArray()
        playlists.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("songs", p.songs.toJsonArray())
                }
            )
        }
        file?.writeText(array.toString())
    }
    /** Merges playlists in by ID. Returns how many were added or changed. */
    fun importPlaylists(incoming: List<Playlist>): Int {
        var changes = 0
        incoming.forEach { playlist ->
            val index = playlists.indexOfFirst { it.id == playlist.id }
            if (index < 0) {
                playlists.add(playlist)
                changes++
            } else {
                val existing = playlists[index]
                val missing = playlist.songs.filter { s -> existing.songs.none { it.id == s.id } }
                if (missing.isNotEmpty()) {
                    playlists[index] = existing.copy(songs = existing.songs + missing)
                    changes++
                }
            }
        }
        if (changes > 0) save()
        return changes
    }
}