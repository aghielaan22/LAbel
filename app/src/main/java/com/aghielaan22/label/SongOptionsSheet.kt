package com.aghielaan22.label

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    onDismiss: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }
    val liked = LibraryRepository.isLiked(song)

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Runs the action, then slides the sheet away before removing it
    fun closeAfter(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                AsyncImage(
                    model = song.artworkUrl(256),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            },
            headlineContent = { Text(song.title, maxLines = 1) },
            supportingContent = { Text(song.artist, maxLines = 1) }
        )
        HorizontalDivider()

        SheetAction("Play next", Icons.Default.SkipNext) {
            closeAfter {
                PlayerController.playNext(song)
                toast("Will play next")
            }
        }
        SheetAction("Add to queue", Icons.Default.Menu) {
            closeAfter {
                PlayerController.addToQueue(song)
                toast("Added to queue")
            }
        }
        SheetAction("Add to playlist", Icons.Default.Add) {
            pickerOpen = true
        }
        SheetAction("Go to artist", Icons.Default.Person) {
            closeAfter {
                Navigator.open(
                    DetailPage.ArtistPage(Artist(name = primaryArtistName(song.artist)))
                )
            }
        }
        SheetAction(
            label = if (liked) "Remove from liked songs" else "Add to liked songs",
            icon = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder
        ) {
            closeAfter { LibraryRepository.toggleLike(song) }
        }
        SheetAction("Share", Icons.Default.Share) {
            closeAfter {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${song.title} by ${song.artist}\n${song.id}")
                }
                context.startActivity(Intent.createChooser(intent, "Share song"))
            }
        }
        if (onRemoveFromPlaylist != null) {
            SheetAction("Remove from playlist", Icons.Default.Delete) {
                closeAfter { onRemoveFromPlaylist() }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pickerOpen) {
        AddToPlaylistDialog(song) {
            pickerOpen = false
            onDismiss()
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) }
    )
}