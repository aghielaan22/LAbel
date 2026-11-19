package com.aghielaan22.label

import androidx.compose.runtime.mutableStateListOf

sealed interface DetailPage {
    data class AlbumPage(val album: Album) : DetailPage
    data class ArtistPage(val artist: Artist) : DetailPage
    data object SettingsPage : DetailPage
}

object Navigator {

    val stack = mutableStateListOf<DetailPage>()

    val current: DetailPage? get() = stack.lastOrNull()

    fun open(page: DetailPage) {
        stack.add(page)
    }

    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun clear() {
        stack.clear()
    }
}