package com.aghielaan22.label

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

data class PlayerColors(val background: Color, val accent: Color)

object ArtworkColors {

    private val client = OkHttpClient()
    private val cache = ConcurrentHashMap<String, PlayerColors>()

    /** Downloads the cover at [url] and works out its colors. Call from a background thread. */
    fun extract(url: String): PlayerColors? {
        cache[url]?.let { return it }

        val request = Request.Builder().url(url).build()
        val bitmap = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
        } ?: return null

        val palette = Palette.from(bitmap).generate()

        val base = palette.darkVibrantSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: return null

        // Keep the background dark enough for white text, even on bright covers
        var background = lerp(Color(base.rgb), Color.Black, 0.3f)
        if (background.luminance() > 0.3f) {
            background = lerp(background, Color.Black, 0.5f)
        }

        val accentSwatch = palette.lightVibrantSwatch
            ?: palette.vibrantSwatch
            ?: palette.lightMutedSwatch

        val colors = PlayerColors(
            background = background,
            accent = accentSwatch?.let { Color(it.rgb) } ?: Color.White
        )
        cache[url] = colors
        return colors
    }
}