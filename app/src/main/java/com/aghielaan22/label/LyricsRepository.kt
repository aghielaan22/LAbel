package com.aghielaan22.label

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

object LyricsRepository {

    private val client = OkHttpClient()

    // Remembers lyrics we already fetched, so we don't ask the server again
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    /** Returns the synced lyrics for [song], or an empty list if none were found. */
    fun fetch(song: Song, durationSec: Long): List<LyricLine> {
        cache[song.id]?.let { return it }

        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", song.title)
            .addQueryParameter("artist_name", song.artist.removeSuffix(" - Topic"))
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LAbel/0.1 (personal learning project)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val results = JSONArray(body)

            // Pick the result whose length is closest to the song we're playing.
            var bestLyrics: String? = null
            var bestDiff = Double.MAX_VALUE
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                if (o.isNull("syncedLyrics")) continue
                val synced = o.getString("syncedLyrics")
                if (synced.isBlank()) continue
                val diff = if (durationSec > 0) {
                    abs(o.optDouble("duration", 0.0) - durationSec)
                } else 0.0
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestLyrics = synced
                }
            }

            // If the closest match is a very different length, it's probably another
            // version of the song, and its timing would be wrong. No lyrics beat bad lyrics.
            val usable = bestLyrics != null && (durationSec <= 0 || bestDiff <= 5.0)
            val lines = if (usable) parseLrc(bestLyrics!!) else emptyList()
            cache[song.id] = lines
            return lines
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