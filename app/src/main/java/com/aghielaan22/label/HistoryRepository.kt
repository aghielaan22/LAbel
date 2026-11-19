package com.aghielaan22.label

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.File

object HistoryRepository {

    private const val MAX_ITEMS = 20
    private var file: File? = null

    val recentSongs = mutableStateListOf<Song>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "recent_songs.json")
        file = f
        if (!f.exists()) return
        try {
            recentSongs.addAll(parseSongs(f.readText()))
        } catch (e: Exception) {
            // Damaged file: start with an empty history
        }
    }

    fun add(song: Song) {
        recentSongs.removeAll { it.id == song.id }
        recentSongs.add(0, song)
        while (recentSongs.size > MAX_ITEMS) {
            recentSongs.removeAt(recentSongs.lastIndex)
        }
        file?.writeText(recentSongs.toList().toJsonString())
    }

    fun clear() {
        recentSongs.clear()
        file?.writeText("[]")
    }
}