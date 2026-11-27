package com.aghielaan22.label

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RankedListen(val song: Song, val plays: Long)

internal data class YearReview(
    val year: Int,
    val availableYears: IntRange,
    val from: LocalDate,
    val through: LocalDate,
    val plays: Long,
    val trackCount: Int,
    val artistCount: Int,
    val topTrack: RankedListen?,
    val topArtist: String?
) {
    val coverage: String
        get() = "${from.format(reviewDateFormatter)} – ${through.format(reviewDateFormatter)}"
}

private val reviewDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

internal object ListeningHistory {
    private val writes = Channel<(SQLiteDatabase) -> Unit>(Channel.UNLIMITED)
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    @Volatile private var database: ListeningDatabase? = null

    @Synchronized
    fun init(context: Context) {
        if (database != null) return
        val helper = ListeningDatabase(context.applicationContext, System.currentTimeMillis())
        database = helper
        writes.trySend { } // Create the tracking start timestamp even before the first play.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (write in writes) {
                try {
                    write(helper.writableDatabase)
                    changes.value++
                } catch (e: Exception) {
                    Log.w("ListeningHistory", "Could not write listening history", e)
                }
            }
        }
    }

    fun record(song: Song, playedAt: Long) {
        writes.trySend { db ->
            db.inTransaction {
                insertWithOnConflict(
                    "tracks", null, ContentValues().apply {
                        put("id", song.id)
                        put("title", song.title)
                        put("artist", song.artist)
                        put("artwork", song.thumbnailUrl)
                    }, SQLiteDatabase.CONFLICT_REPLACE
                ).also { check(it != -1L) }
                insertOrThrow("plays", null, ContentValues().apply {
                    put("track_id", song.id)
                    put("played_at", playedAt)
                })
            }
        }
    }

    fun clear(resetAt: Long) {
        writes.trySend { db ->
            db.inTransaction {
                delete("plays", null, null)
                delete("tracks", null, null)
                execSQL("UPDATE coverage SET started_at = ?", arrayOf<Any>(resetAt))
            }
        }
    }

    suspend fun heavyRotation(now: Long = System.currentTimeMillis()): List<Song> =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val firstDay = localDate(now, zone).withDayOfMonth(1)
            ranked(readDatabase(), firstDay.startMillis(zone), now, limit = 50).map { it.song }
        }

    suspend fun forgottenFavorites(
        liked: List<Song>,
        legacyDates: Map<String, Long>,
        now: Long = System.currentTimeMillis()
    ): List<Song> = withContext(Dispatchers.IO) {
        if (liked.isEmpty()) return@withContext emptyList()
        val ids = liked.mapTo(HashSet()) { it.id }
        val recorded = HashMap<String, Long>()
        readDatabase().rawQuery(
            "SELECT track_id, MAX(played_at) FROM plays GROUP BY track_id", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (id in ids) recorded[id] = cursor.getLong(1)
            }
        }
        val zone = ZoneId.systemDefault()
        val cutoff = Instant.ofEpochMilli(now).atZone(zone).minusDays(90).toInstant().toEpochMilli()
        liked.mapNotNull { song ->
            val last = maxOf(recorded[song.id] ?: 0L, legacyDates[song.id] ?: 0L)
            if (last in 1L..cutoff) song to last else null
        }.sortedBy { it.second }.map { it.first }
    }

    suspend fun yearReview(selectedYear: Int? = null, now: Long = System.currentTimeMillis()): YearReview =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val today = localDate(now, zone)
            val db = readDatabase()
            db.inTransaction {
                val trackedAt = rawQuery("SELECT started_at FROM coverage", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else now
                }
                val firstTrackedYear = localDate(trackedAt.coerceAtMost(now), zone).year
                val year = (selectedYear ?: today.year).coerceIn(firstTrackedYear, today.year)
                val yearStart = LocalDate.of(year, 1, 1)
                val through = minOf(now, yearStart.plusYears(1).startMillis(zone) - 1L)
                val from = maxOf(yearStart.startMillis(zone), trackedAt).coerceAtMost(through)
                val tracks = ranked(this, from, through)
                val knownArtists = tracks.mapNotNull { track ->
                    primaryArtistName(track.song.artist).takeIf { it.isNotBlank() }?.let { it to track.plays }
                }.groupBy { it.first.lowercase(Locale.ROOT) }
                val topArtist = knownArtists.values.maxByOrNull { group -> group.sumOf { it.second } }
                    ?.maxByOrNull { it.second }?.first
                YearReview(
                    year = year,
                    availableYears = firstTrackedYear..today.year,
                    from = localDate(from, zone),
                    through = localDate(through, zone),
                    plays = tracks.sumOf { it.plays },
                    trackCount = tracks.size,
                    artistCount = knownArtists.size,
                    topTrack = tracks.firstOrNull(),
                    topArtist = topArtist
                )
            }
        }

    private fun readDatabase(): SQLiteDatabase =
        checkNotNull(database) { "Listening history hasn't started" }.writableDatabase

    private fun ranked(db: SQLiteDatabase, from: Long, through: Long, limit: Int? = null): List<RankedListen> {
        val sql = """
            SELECT t.id, t.title, t.artist, t.artwork, COUNT(*) AS play_count
            FROM plays p JOIN tracks t ON t.id = p.track_id
            WHERE p.played_at >= ? AND p.played_at <= ?
            GROUP BY t.id, t.title, t.artist, t.artwork
            ORDER BY play_count DESC, MAX(p.played_at) DESC, t.id ASC
        """.trimIndent() + if (limit == null) "" else " LIMIT $limit"
        return db.rawQuery(sql, arrayOf(from.toString(), through.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val song = Song(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
                    add(RankedListen(song, cursor.getLong(4)))
                }
            }
        }
    }

    private fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    private inline fun <T> SQLiteDatabase.inTransaction(action: SQLiteDatabase.() -> T): T {
        beginTransactionNonExclusive()
        try {
            return action().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }
}

private class ListeningDatabase(context: Context, private val startedAt: Long) :
    SQLiteOpenHelper(context, "listening_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, artwork TEXT)")
        db.execSQL("CREATE TABLE plays (track_id TEXT NOT NULL, played_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX plays_period ON plays(played_at, track_id)")
        db.execSQL("CREATE INDEX plays_track ON plays(track_id, played_at)")
        db.execSQL("CREATE TABLE coverage (started_at INTEGER NOT NULL)")
        db.execSQL("INSERT INTO coverage VALUES (?)", arrayOf<Any>(startedAt))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No listening history migration from $oldVersion to $newVersion")
    }
}