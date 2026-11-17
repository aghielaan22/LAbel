package com.aghielaan22.label

data class Album(
    val id: String,            // the album's playlist address
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null
) {
    fun artworkUrl(size: Int): String? = thumbnailUrl?.let { upscaleArtwork(it, size) }
}

data class Artist(
    val name: String,
    val thumbnailUrl: String? = null
) {
    fun artworkUrl(size: Int): String? = thumbnailUrl?.let { upscaleArtwork(it, size) }
}

data class ArtistDetails(val songs: List<Song>, val albums: List<Album>)

/** "Artist - Topic" or "A, B & C" becomes just the first artist's name. */
fun primaryArtistName(raw: String): String =
    raw.removeSuffix(" - Topic").substringBefore(",").substringBefore(" & ").trim()