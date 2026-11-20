package com.aghielaan22.label

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SongRow(
    song: Song,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    wideArtwork: Boolean = false,
    onClick: () -> Unit
) {
    val liked = LibraryRepository.isLiked(song)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    PlayerController.addToQueue(song)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    PlayerController.playNext(song)
                    Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show()
                }

                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // always bounce back: we only wanted the gesture
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val direction = swipeState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val toRight = direction == SwipeToDismissBoxValue.StartToEnd
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (toRight) Icons.Default.Add else Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (toRight) "Add to queue" else "Play next",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    ) {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    sheetOpen = true
                }
            ),
            headlineContent = { Text(song.title, maxLines = 1) },
            supportingContent = { Text(song.artist, maxLines = 1) },
            leadingContent = {
                AsyncImage(
                    model = song.artworkUrl(if (wideArtwork) 384 else 256),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .then(
                            if (wideArtwork) Modifier.size(width = 89.dp, height = 50.dp)
                            else Modifier.size(52.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { LibraryRepository.toggleLike(song) }) {
                        Icon(
                            imageVector = if (liked) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            contentDescription = if (liked) "Unlike" else "Like",
                            tint = if (liked) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { sheetOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                }
            }
        )
    }

    if (sheetOpen) {
        SongOptionsSheet(
            song = song,
            onDismiss = { sheetOpen = false },
            onRemoveFromPlaylist = onRemoveFromPlaylist
        )
    }
}