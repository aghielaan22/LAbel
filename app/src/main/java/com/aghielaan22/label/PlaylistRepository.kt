package com.aghielaan22.label

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(val id: String, val name: String, val songs: List<Song>)

object PlaylistRepository {

    private var file: AtomicFile? = null
    private val writes = Channel<List<Playlist>>(Channel.CONFLATED)

    val playlists = mutableStateListOf<Playlist>()

    fun init(context: Context) {
        if (file != null) return
        val target = AtomicFile(File(context.applicationContext.filesDir, "playlists.json"))
        file = target
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (snapshot in writes) {
                try {
                    write(target, snapshot)
                } catch (e: Exception) {
                    Log.w("PlaylistRepository", "Could not save playlists", e)
                }
            }
        }
        try {
            val array = JSONArray(target.openRead().bufferedReader().use { it.readText() })
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
        } catch (_: FileNotFoundException) {
            // A fresh installation has no playlists yet.
        } catch (e: Exception) {
            Log.w("PlaylistRepository", "Could not read saved playlists", e)
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

    fun addSong(id: String, song: Song): Boolean = addSongs(id, listOf(song)) > 0

    /** Adds songs in collection order, skipping tracks already in the playlist. */
    fun addSongs(id: String, songs: List<Song>): Int {
        val playlist = get(id) ?: return 0
        val known = playlist.songs.mapTo(HashSet()) { it.id }
        val newSongs = songs.filter { known.add(it.id) }
        if (newSongs.isEmpty()) return 0
        update(id) { it.copy(songs = it.songs + newSongs) }
        return newSongs.size
    }

    internal fun updateArtwork(id: String, covers: Map<String, String>): Int {
        val index = playlists.indexOfFirst { it.id == id }
        if (index < 0 || covers.isEmpty()) return 0

        val playlist = playlists[index]
        var changed = 0
        val songs = playlist.songs.map { song ->
            val cover = YouTubeArtworkKey.fromUrl(song.id)?.let(covers::get)
            if (cover == null || cover == song.thumbnailUrl) {
                song
            } else {
                changed++
                song.copy(thumbnailUrl = cover)
            }
        }
        if (changed > 0) {
            playlists[index] = playlist.copy(songs = songs)
            save()
        }
        return changed
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
        writes.trySend(playlists.toList())
    }

    private fun write(target: AtomicFile, playlists: List<Playlist>) {
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
        val data = array.toString().toByteArray(Charsets.UTF_8)
        val stream = target.startWrite()
        try {
            stream.write(data)
            target.finishWrite(stream)
        } catch (e: Exception) {
            target.failWrite(stream)
            throw e
        }
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
    fun createWithSongs(name: String, songs: List<Song>): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Imported playlist" }.take(100),
            songs = songs.distinctBy { it.id }
        )
        playlists.add(playlist)
        save()
        return playlist
    }

    fun moveSong(id: String, from: Int, to: Int) {
        update(id) { p ->
            if (from !in p.songs.indices || to !in p.songs.indices) {
                p
            } else {
                p.copy(songs = p.songs.toMutableList().apply { add(to, removeAt(from)) })
            }
        }
    }
}