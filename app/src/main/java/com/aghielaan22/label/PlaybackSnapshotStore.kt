package com.aghielaan22.label

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class PlaybackSnapshot(
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int
)

internal class PlaybackSnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "playback-session.json"))
    private val writes = Channel<PlaybackSnapshot>(Channel.CONFLATED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (snapshot in writes) write(snapshot)
        }
    }

    suspend fun read(): PlaybackSnapshot? = withContext(Dispatchers.IO) { readSnapshot() }

    @Synchronized
    private fun readSnapshot(): PlaybackSnapshot? {
        return try {
            val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            val songs = parseSongs(json.getJSONArray("songs"))
            if (songs.isEmpty()) return null
            PlaybackSnapshot(
                songs,
                json.optInt("index").coerceIn(songs.indices),
                json.optLong("positionMs").coerceAtLeast(0),
                json.optBoolean("shuffle"),
                json.optInt("repeat")
            )
        } catch (_: FileNotFoundException) {
            null
        } catch (e: Exception) {
            Log.w("PlaybackSnapshot", "Could not restore playback", e)
            null
        }
    }

    fun enqueue(snapshot: PlaybackSnapshot) {
        writes.trySend(snapshot)
    }

    @Synchronized
    private fun write(snapshot: PlaybackSnapshot) {
        try {
            val data = JSONObject()
                .put("songs", snapshot.songs.toJsonArray())
                .put("index", snapshot.index)
                .put("positionMs", snapshot.positionMs)
                .put("shuffle", snapshot.shuffle)
                .put("repeat", snapshot.repeatMode)
                .toString().toByteArray(Charsets.UTF_8)
            val stream = file.startWrite()
            try {
                stream.write(data)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        } catch (e: Exception) {
            Log.w("PlaybackSnapshot", "Could not save playback", e)
        }
    }
}