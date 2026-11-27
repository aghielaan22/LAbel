package com.aghielaan22.label

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null
) {
    /** The cover at roughly [size] pixels, when the image address allows resizing. */
    fun artworkUrl(size: Int): String? = thumbnailUrl?.let { upscaleArtwork(it, size) }
}

fun upscaleArtwork(url: String, size: Int): String {
    val host = url.substringAfter("://").substringBefore("/")
    return when {
        // Album art: ask Google's image service for a bigger copy
        host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com") ->
            url.replace(Regex("=[ws]\\d+[^/?]*$"), "=w$size-h$size-l90-rj")

        // Video-style thumbnails: ask for the largest version (Artwork falls back if it's missing)
        host.endsWith("ytimg.com") && size > 320 ->
            url.replace(
                Regex("/(default|mqdefault|hqdefault|sddefault|hq720)\\.(jpg|webp)"),
                "/maxresdefault.$2"
            )

        else -> url
    }
}