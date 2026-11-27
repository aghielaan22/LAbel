package com.aghielaan22.label

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage

@Composable
fun ReorderablePlaylistList(playlist: Playlist) {
    val listState = rememberLazyListState()
    val reorder = remember(playlist.id) {
        ReorderState(listState) { from, to -> PlaylistRepository.moveSong(playlist.id, from, to) }
    }

    LazyColumn(state = listState) {
        itemsIndexed(playlist.songs, key = { _, song -> song.id }) { index, song ->
            val dragging = reorder.draggingIndex == index
            ListItem(
                modifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = reorder.draggedOffset }
                } else {
                    Modifier.animateItem()
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    AsyncImage(
                        model = song.artworkUrl(256),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                },
                headlineContent = { Text(song.title, maxLines = 1) },
                supportingContent = { Text(song.artist, maxLines = 1) },
                trailingContent = { DragHandle(index, reorder) }
            )
        }
    }
}