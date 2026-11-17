package com.aghielaan22.label

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    wideArtwork: Boolean = false,
    onClick: () -> Unit
) {
    val liked = LibraryRepository.isLiked(song)
    val haptics = LocalHapticFeedback.current
    var sheetOpen by remember { mutableStateOf(false) }

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

    if (sheetOpen) {
        SongOptionsSheet(
            song = song,
            onDismiss = { sheetOpen = false },
            onRemoveFromPlaylist = onRemoveFromPlaylist
        )
    }
}