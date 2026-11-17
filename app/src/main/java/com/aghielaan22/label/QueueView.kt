package com.aghielaan22.label

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun QueueView(modifier: Modifier = Modifier) {
    val songs = PlayerController.queueSongs
    val current = PlayerController.currentIndex
    val listState = rememberLazyListState()

    // Keep the current song in view as the queue advances
    LaunchedEffect(current, songs.size) {
        if (songs.isNotEmpty()) {
            listState.animateScrollToItem(current.coerceIn(0, songs.lastIndex))
        }
    }

    Column(modifier = modifier) {
        if (PlayerController.shuffleEnabled) {
            Text(
                text = "Shuffle is on, so songs play in random order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(state = listState) {
            itemsIndexed(songs) { index, song ->
                val isCurrent = index == current
                ListItem(
                    modifier = Modifier.clickable { PlayerController.playQueueIndex(index) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    },
                    headlineContent = {
                        Text(
                            text = song.title,
                            maxLines = 1,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else Color.Unspecified
                        )
                    },
                    supportingContent = { Text(song.artist, maxLines = 1) },
                    trailingContent = {
                        IconButton(onClick = { PlayerController.removeFromQueue(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from queue")
                        }
                    }
                )
            }
        }
    }
}