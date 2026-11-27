package com.aghielaan22.label

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import android.util.LruCache

data class PlayerColors(val background: Color, val accent: Color)

object ArtworkColors {

    private val cache = LruCache<String, PlayerColors>(96)

    /** Downloads the cover at [url] and works out its colors. Call from a background thread. */
    suspend fun extract(context: Context, url: String): PlayerColors? {
        cache.get(url)?.let { return it }

        val request = ImageRequest.Builder(context).data(url).size(128, 128).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.image.toBitmap()

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
            accent = accentSwatch?.let { Color(it.rgb) } ?: Color(base.rgb)
        )
        cache.put(url, colors)
        return colors
    }
}