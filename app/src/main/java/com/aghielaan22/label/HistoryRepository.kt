package com.aghielaan22.label

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import android.util.AtomicFile
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

object HistoryRepository {

    private const val MAX_ITEMS = 20
    private var file: AtomicFile? = null
    private val pending = Channel<List<Song>>(Channel.CONFLATED)

    val recentSongs = mutableStateListOf<Song>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "recent_songs.json")
        val history = AtomicFile(f)
        file = history
        try {
            recentSongs.addAll(parseSongs(history.openRead().bufferedReader().use { it.readText() }))
        } catch (_: Exception) {
            // Start empty if history is missing or damaged.
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (songs in pending) {
                try {
                    val stream = history.startWrite()
                    try {
                        stream.write(songs.toJsonString().toByteArray(Charsets.UTF_8))
                        history.finishWrite(stream)
                    } catch (e: Exception) {
                        history.failWrite(stream)
                        throw e
                    }
                } catch (e: Exception) {
                    Log.w("HistoryRepository", "Could not save listening history", e)
                }
            }
        }
    }

    fun add(song: Song) {
        recentSongs.removeAll { it.id == song.id }
        recentSongs.add(0, song)
        while (recentSongs.size > MAX_ITEMS) {
            recentSongs.removeAt(recentSongs.lastIndex)
        }
        pending.trySend(recentSongs.toList())
    }

    fun clear() {
        recentSongs.clear()
        pending.trySend(emptyList())
    }
}