package com.aghielaan22.label

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LibraryRepository {

    private var file: File? = null

    // A "state list" tells Compose to redraw whenever it changes
    val likedSongs = mutableStateListOf<Song>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "liked_songs.json")
        file = f
        if (!f.exists()) return
        try {
            val array = JSONArray(f.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                likedSongs.add(
                    Song(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        artist = o.getString("artist"),
                        thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (e: Exception) {
            // If the file is damaged, just start with an empty library
        }
    }

    fun isLiked(song: Song): Boolean = likedSongs.any { it.id == song.id }

    fun toggleLike(song: Song) {
        val index = likedSongs.indexOfFirst { it.id == song.id }
        if (index >= 0) likedSongs.removeAt(index) else likedSongs.add(0, song)
        save()
    }

    private fun save() {
        val array = JSONArray()
        likedSongs.forEach { s ->
            array.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("artist", s.artist)
                    put("thumbnailUrl", s.thumbnailUrl)
                }
            )
        }
        file?.writeText(array.toString())
    }
    /** Adds songs that aren't already liked. Returns how many were new. */
    fun importSongs(songs: List<Song>): Int {
        var added = 0
        songs.forEach { song ->
            if (likedSongs.none { it.id == song.id }) {
                likedSongs.add(song)
                added++
            }
        }
        if (added > 0) save()
        return added
    }
}