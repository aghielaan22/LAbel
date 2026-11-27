package com.aghielaan22.label

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

data class SongStat(
    val song: Song,
    val plays: Int,
    val skips: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
)

data class ArtistStat(
    val name: String,
    val plays: Int,
    val listenedMs: Long,
    val thumbnailUrl: String?
)

object StatsRepository {

    private const val MAX_SONGS = 1500
    private const val KEEP_SONGS = 1200

    private var file: AtomicFile? = null
    private val stats = LinkedHashMap<String, SongStat>()
    private val writes = Channel<List<SongStat>>(Channel.CONFLATED)

    /** Goes up on every change, so any screen that reads stats refreshes. */
    var revision by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (file != null) return
        val target = AtomicFile(File(context.applicationContext.filesDir, "stats.json"))
        file = target
        ListeningHistory.init(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (snapshot in writes) {
                try {
                    writeStats(target, snapshot)
                } catch (e: Exception) {
                    Log.w("StatsRepository", "Could not save stats", e)
                }
            }
        }
        try {
            val array = JSONArray(target.openRead().bufferedReader().use { it.readText() })
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val song = Song(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotEmpty() }
                )
                stats[song.id] = SongStat(
                    song = song,
                    plays = o.optInt("plays"),
                    skips = o.optInt("skips"),
                    listenedMs = o.optLong("listenedMs"),
                    lastPlayedAt = o.optLong("lastPlayedAt")
                )
            }
            revision++
        } catch (_: FileNotFoundException) {
            // Fresh installs start with no lifetime stats.
        } catch (e: Exception) {
            Log.w("StatsRepository", "Could not read stats", e)
            stats.clear()
        }
    }

    // ---- Recording ----

    fun recordPlay(song: Song) {
        val now = System.currentTimeMillis()
        update(song) { it.copy(plays = it.plays + 1, lastPlayedAt = now) }
        ListeningHistory.record(song, now)
    }

    fun recordSkip(song: Song) = update(song) { it.copy(skips = it.skips + 1) }

    fun addListenTime(song: Song, ms: Long) =
        update(song) { it.copy(listenedMs = it.listenedMs + ms) }

    fun reset() {
        ListeningHistory.clear(System.currentTimeMillis())
        stats.clear()
        revision++
        save()
    }

    // ---- Questions the app can ask ----

    internal fun lastPlayedDates(): Map<String, Long> =
        snapshot().filter { it.lastPlayedAt > 0 }.associate { it.song.id to it.lastPlayedAt }

    fun totalListenedMs(): Long = snapshot().sumOf { it.listenedMs }

    fun totalPlays(): Int = snapshot().sumOf { it.plays }

    fun distinctArtists(): Int =
        snapshot().filter { it.plays > 0 }
            .map { primaryArtistName(it.song.artist).lowercase() }
            .toSet().size

    fun topSongs(limit: Int): List<SongStat> =
        snapshot().filter { it.plays > 0 }
            .sortedWith(compareByDescending<SongStat> { it.plays }.thenByDescending { it.listenedMs })
            .take(limit)

    fun topArtists(limit: Int): List<ArtistStat> =
        snapshot().filter { it.plays > 0 }
            .groupBy { primaryArtistName(it.song.artist).lowercase() }
            .map { (_, group) ->
                val best = group.maxByOrNull { it.plays }!!
                ArtistStat(
                    name = primaryArtistName(best.song.artist),
                    plays = group.sumOf { it.plays },
                    listenedMs = group.sumOf { it.listenedMs },
                    thumbnailUrl = best.song.thumbnailUrl
                )
            }
            .sortedByDescending { it.plays }
            .take(limit)

    /** Songs you clearly like: played often, and not skipped more than played. */
    fun topSeeds(limit: Int): List<Song> =
        snapshot().filter { it.plays > 0 && it.skips <= it.plays }
            .sortedByDescending { it.plays * 3.0 + it.listenedMs / 60_000.0 }
            .take(limit)
            .map { it.song }

    /** True for songs you keep skipping and never finish. */
    fun isDisliked(id: String): Boolean {
        val s = stats[id] ?: return false
        return s.skips >= 2 && s.plays == 0
    }

    // ---- Internals ----

    private fun snapshot(): List<SongStat> {
        revision // reading this makes screens refresh when stats change
        return stats.values.toList()
    }

    private fun update(song: Song, change: (SongStat) -> SongStat) {
        val existing = stats[song.id] ?: SongStat(song, 0, 0, 0L, 0L)
        stats[song.id] = change(existing)

        if (stats.size > MAX_SONGS) {
            val keep = stats.values
                .sortedWith(compareByDescending<SongStat> { it.plays }.thenByDescending { it.lastPlayedAt })
                .take(KEEP_SONGS)
            stats.clear()
            keep.forEach { stats[it.song.id] = it }
        }
        revision++
        save()
    }

    private fun save() {
        if (file != null) writes.trySend(stats.values.toList())
    }

    private fun writeStats(target: AtomicFile, snapshot: List<SongStat>) {
        val array = JSONArray()
        snapshot.forEach { stat ->
            array.put(
                JSONObject().apply {
                    put("id", stat.song.id)
                    put("title", stat.song.title)
                    put("artist", stat.song.artist)
                    put("thumbnailUrl", stat.song.thumbnailUrl)
                    put("plays", stat.plays)
                    put("skips", stat.skips)
                    put("listenedMs", stat.listenedMs)
                    put("lastPlayedAt", stat.lastPlayedAt)
                }
            )
        }

        val bytes = array.toString().toByteArray(Charsets.UTF_8)
        val stream = target.startWrite()
        try {
            stream.write(bytes)
            target.finishWrite(stream)
        } catch (e: Exception) {
            target.failWrite(stream)
            throw e
        }
    }
}