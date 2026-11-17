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
    val resizable = host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com")
    return if (resizable) {
        url.replace(Regex("=[ws]\\d+[^/?]*$"), "=w$size-h$size-l90-rj")
    } else {
        url
    }
}