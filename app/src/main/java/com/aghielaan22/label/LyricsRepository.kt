package com.aghielaan22.label

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import android.util.LruCache
import java.io.IOException
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

data class SongLyrics(val synced: List<LyricLine> = emptyList(), val plain: String? = null)

object LyricsRepository {

    private val client = OkHttpClient()

    // Remembers lyrics we already fetched, so we don't ask the server again
    private val cache = LruCache<String, SongLyrics>(64)

    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    fun fetch(song: Song, durationSec: Long, useCache: Boolean = true): SongLyrics {        val cacheKey = "${song.id}|$durationSec"
        if (useCache) {
            cache.get(cacheKey)?.let { return it }
        }
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", song.title)
            .addQueryParameter("artist_name", song.artist.removeSuffix(" - Topic"))
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LAbel/0.1 (personal learning project)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Lyrics server returned HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty lyrics response")
            val results = JSONArray(body)

            var synced = emptyList<LyricLine>()
            var syncedDiff = Double.POSITIVE_INFINITY
            var plain: String? = null
            var plainDiff = Double.POSITIVE_INFINITY
            for (i in 0 until results.length()) {
                val row = results.optJSONObject(i) ?: continue
                val duration = row.optDouble("duration", Double.NaN)
                val diff = when {
                    durationSec <= 0 -> 0.0
                    duration.isFinite() && duration > 0 -> abs(duration - durationSec)
                    else -> 60.0
                }
                val unsynced = row.optString("plainLyrics").takeIf {
                    !row.isNull("plainLyrics") && it.isNotBlank()
                }
                if (unsynced != null && diff < plainDiff) {
                    plain = unsynced
                    plainDiff = diff
                }
                if (diff <= 5.0 && diff < syncedDiff && !row.isNull("syncedLyrics")) {
                    val lines = parseLrc(row.optString("syncedLyrics"))
                    if (lines.isNotEmpty()) {
                        synced = lines
                        syncedDiff = diff
                    }
                }
            }
            val lyrics = SongLyrics(synced, plain)
            if (useCache) cache.put(cacheKey, lyrics)
            return lyrics
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in lrc.lines()) {
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim()
            for (tag in tags) {
                val minutes = tag.groupValues[1].toLong()
                val seconds = tag.groupValues[2].toLong()
                val fraction = tag.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                result.add(LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }
}