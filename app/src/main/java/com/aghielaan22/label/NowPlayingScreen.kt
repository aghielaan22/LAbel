package com.aghielaan22.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class PlayerPane { Artwork, Lyrics, Queue }

@Composable
fun NowPlayingContent(onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    val song = PlayerController.currentSong
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var pane by remember { mutableStateOf(PlayerPane.Artwork) }
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }

    // Keeps position and duration up to date
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) {
                position = PlayerController.position()
                duration = PlayerController.duration()
            }
            delay(250)
        }
    }

    // Loads lyrics when the lyrics pane is open, and again for each new song
    LaunchedEffect(song?.id, pane) {
        if (pane != PlayerPane.Lyrics || song == null) return@LaunchedEffect
        lyrics = null
        var waited = 0
        while (PlayerController.duration() <= 0 && waited < 5000) {
            delay(250)
            waited += 250
        }
        val durationSec = PlayerController.duration() / 1000
        lyrics = try {
            withContext(Dispatchers.IO) { LyricsRepository.fetch(song, durationSec) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player")
                }
                Row {
                    TextButton(
                        onClick = {
                            pane = if (pane == PlayerPane.Queue) PlayerPane.Artwork
                            else PlayerPane.Queue
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (pane == PlayerPane.Queue)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Queue") }
                    TextButton(
                        onClick = {
                            pane = if (pane == PlayerPane.Lyrics) PlayerPane.Artwork
                            else PlayerPane.Lyrics
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (pane == PlayerPane.Lyrics)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Lyrics") }
                }
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                when (pane) {
                    PlayerPane.Lyrics -> LyricsView(
                        lines = lyrics,
                        positionMs = position,
                        onSeek = {
                            PlayerController.seekTo(it)
                            position = it
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    PlayerPane.Queue -> QueueView(modifier = Modifier.fillMaxSize())

                    PlayerPane.Artwork -> AsyncImage(
                        model = song?.artworkUrl(1200),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2
                    )
                    Text(
                        text = song?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                song?.let { current ->
                    val liked = LibraryRepository.isLiked(current)
                    IconButton(onClick = { LibraryRepository.toggleLike(current) }) {
                        Icon(
                            imageVector = if (liked) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            contentDescription = if (liked) "Unlike" else "Like",
                            tint = if (liked) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Slider(
                value = when {
                    dragging -> sliderValue
                    duration > 0 -> position.toFloat() / duration
                    else -> 0f
                },
                onValueChange = {
                    dragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    val target = (sliderValue * duration).toLong()
                    PlayerController.seekTo(target)
                    position = target
                    dragging = false
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { PlayerController.toggleShuffle() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (PlayerController.shuffleEnabled)
                            MaterialTheme.colorScheme.primary
                        else LocalContentColor.current
                    )
                }
                IconButton(
                    onClick = { PlayerController.previous() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(
                    onClick = { PlayerController.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (PlayerController.isPlaying)
                            Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause",
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(
                    onClick = { PlayerController.next() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(
                    onClick = { PlayerController.cycleRepeatMode() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (PlayerController.repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (PlayerController.repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else LocalContentColor.current
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}