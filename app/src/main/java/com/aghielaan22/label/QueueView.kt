package com.aghielaan22.label

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun QueueView(modifier: Modifier = Modifier) {
    val rows = PlayerController.queueRows
    val current = PlayerController.currentIndex
    val upcoming = PlayerController.upNextIndices
    val locked = PlayerController.controlsLocked
    var fullQueue by remember { mutableStateOf(false) }
    val visibleRows = remember(rows, current, upcoming, fullQueue) {
        if (fullQueue) rows else (listOf(current) + upcoming).mapNotNull(rows::getOrNull)
    }
    val upNextState = rememberLazyListState()
    val queueState = rememberLazyListState()
    val reorder = remember(queueState) { ReorderState(queueState, PlayerController::moveQueueItem) }

    LaunchedEffect(fullQueue, current) {
        if (!fullQueue) upNextState.scrollToItem(0)
    }

    Column(modifier) {
        Row {
            FilterChip(selected = !fullQueue, onClick = { fullQueue = false }, label = { Text("Up next") })
            FilterChip(
                selected = fullQueue, onClick = { fullQueue = true }, label = { Text("Edit queue") },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (!fullQueue && upcoming.isEmpty()) {
            val message = when {
                PlayerController.repeatMode == Player.REPEAT_MODE_ONE -> "This song is on repeat."
                rows.isEmpty() -> "The queue is empty."
                else -> "No more songs in the queue."
            }
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        }

        LazyColumn(state = if (fullQueue) queueState else upNextState) {
            items(visibleRows, key = { it.key }) { row ->
                val rowModifier = if (fullQueue && reorder.draggingIndex == row.index) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = reorder.draggedOffset }
                } else Modifier
                QueueTrack(
                    row = row,
                    isCurrent = row.index == current,
                    editable = !locked,
                    modifier = rowModifier,
                    dragHandle = if (fullQueue && !locked) {
                        { DragHandle(row.index, reorder) }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueTrack(
    row: PlayerController.QueueRow,
    isCurrent: Boolean,
    editable: Boolean,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = editable,
        modifier = modifier,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart && !PlayerController.removeFromQueue(row)) {
                scope.launch { swipeState.reset() }
            }
        },
        backgroundContent = {
            if (swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    ) {
        ListItem(
            modifier = Modifier.clickable(enabled = editable) { PlayerController.playQueueIndex(row.index) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                AsyncImage(
                    model = row.song.artworkUrl(128),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                )
            },
            headlineContent = {
                Text(
                    row.song.title,
                    maxLines = 1,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
            },
            supportingContent = {
                Text(if (isCurrent) "Now playing · ${row.song.artist}" else row.song.artist, maxLines = 1)
            },
            trailingContent = if (editable) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        dragHandle?.invoke()
                        IconButton(onClick = { PlayerController.removeFromQueue(row) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from queue")
                        }
                    }
                }
            } else null
        )
    }
}