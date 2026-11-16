package com.aghielaan22.label

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val songs = LibraryRepository.likedSongs

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Liked songs",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (songs.isEmpty()) {
            Text(
                text = "Songs you like will show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(song) {
                        PlayerController.playQueue(songs.toList(), index)
                    }
                }
            }
        }
    }
}