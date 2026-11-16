package com.aghielaan22.label

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null
)