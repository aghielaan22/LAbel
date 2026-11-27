package com.aghielaan22.label

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible

enum class PlayerPane { Artwork, Lyrics, Queue }

@Composable
fun NowPlayingContent(
    pane: PlayerPane,
    onPaneChange: (PlayerPane) -> Unit,
    onClose: () -> Unit,
    visible: Boolean = true
) {
    BackHandler(enabled = visible, onBack = onClose)

    val song = PlayerController.currentSong
    var songMenuOpen by remember { mutableStateOf(false) }
    var lyricCard by remember { mutableStateOf<Pair<Song, List<String>>?>(null) }
    var optionsOpen by remember { mutableStateOf(false) }

    lyricCard?.let { (track, excerpt) ->
        ShareCardEditor(track, excerpt, onDismiss = { lyricCard = null })
    }
    if (songMenuOpen && song != null) {
        SongOptionsSheet(song, onDismiss = { songMenuOpen = false }, showArtworkStyle = true)
    }
    if (optionsOpen) {
        PlaybackOptionsSheet(onDismiss = { optionsOpen = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlaybackRecoveryNotice()
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart).glassPanel(CircleShape)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player")
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).glassPanel(RoundedCornerShape(28.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { optionsOpen = true }) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Sleep timer and fades",
                        tint = if (SleepTimer.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { songMenuOpen = true }, enabled = song != null) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Song options")
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // The middle: artwork, or lyrics or queue on a glass card
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = minOf(maxWidth, maxHeight)

            AnimatedContent(
                targetState = pane,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
                label = "pane"
            ) { current ->
                when (current) {
                    PlayerPane.Artwork -> if (side > 0.dp) {
                        GestureArtwork(
                            song = song,
                            size = side,
                            animate = visible && pane == PlayerPane.Artwork
                        )
                    }

                    PlayerPane.Lyrics -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassPanel(RoundedCornerShape(28.dp))
                            .padding(16.dp)
                    ) {
                        song?.let { track ->
                            LiveLyrics(track, Modifier.fillMaxSize()) { lines ->
                                lyricCard = track to lines
                            }
                        }
                    }

                    PlayerPane.Queue -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassPanel(RoundedCornerShape(28.dp))
                            .padding(12.dp)
                    ) {
                        QueueView(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth()) {
            if (PlayerController.controlsLocked) {
                Text(
                    "Host controls playback",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            Spacer(Modifier.height(8.dp))

            PlaybackProgress(song?.id)

            PlayerTransport()
            ActiveLoopButton(song?.id)
        }
        Spacer(Modifier.height(12.dp))
        PlayerPaneBar(pane, onPaneChange)
    }
}

@Composable
private fun PlayerTransport() {
    val enabled = !PlayerController.controlsLocked
    val scheme = MaterialTheme.colorScheme
    val repeat = PlayerController.repeatMode
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = PlayerController::toggleShuffle, enabled = enabled) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = if (PlayerController.shuffleEnabled) "Shuffle on" else "Shuffle off",
                tint = if (PlayerController.shuffleEnabled && enabled) scheme.primary else LocalContentColor.current
            )
        }
        IconButton(onClick = PlayerController::previous, enabled = enabled) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
        }
        Surface(
            onClick = PlayerController::togglePlayPause,
            enabled = enabled,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = if (enabled) scheme.primary else scheme.surfaceContainerHigh,
            contentColor = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlayPauseIcon(PlayerController.isPlaying, Modifier.size(38.dp))
            }
        }
        IconButton(onClick = PlayerController::next, enabled = enabled) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = PlayerController::cycleRepeatMode, enabled = enabled) {
            Icon(
                if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = when (repeat) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                tint = if (repeat != Player.REPEAT_MODE_OFF && enabled) scheme.primary else LocalContentColor.current
            )
        }
    }
}

@Composable
private fun PlayerPaneBar(pane: PlayerPane, onPaneChange: (PlayerPane) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .glassPanel(RoundedCornerShape(28.dp))
            .selectableGroup()
            .padding(4.dp)
    ) {
        PlayerPane.entries.forEach { item ->
            val selected = item == pane
            val label = when (item) {
                PlayerPane.Artwork -> "Artwork"
                PlayerPane.Lyrics -> "Lyrics"
                PlayerPane.Queue -> "Queue"
            }
            Box(
                modifier = Modifier.weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) scheme.primary.copy(alpha = 0.20f) else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onPaneChange(item) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun livePlaybackPosition(songId: String?): Long {
    val playing = PlayerController.isPlaying
    var position by remember(songId) { mutableLongStateOf(PlayerController.position()) }
    LaunchedEffect(songId, playing, PlayerController.positionRevision) {
        position = PlayerController.position()
        while (playing) {
            delay(250)
            position = PlayerController.position()
        }
    }
    return position
}

@Composable
private fun PlaybackProgress(songId: String?) {
    val reportedPosition = livePlaybackPosition(songId)
    var pendingSeek by remember(songId, reportedPosition) { mutableStateOf<Long?>(null) }
    val position = pendingSeek ?: reportedPosition
    val duration = remember(songId, PlayerController.isLoading, PlayerController.positionRevision) {
        PlayerController.duration()
    }
    var dragging by remember(songId) { mutableStateOf(false) }
    var sliderValue by remember(songId) { mutableFloatStateOf(0f) }

    Slider(
        enabled = duration > 0 && !PlayerController.controlsLocked,
        value = when {
            dragging -> sliderValue
            duration > 0 -> (position.toFloat() / duration).coerceIn(0f, 1f)
            else -> 0f
        },
        onValueChange = {
            dragging = true
            sliderValue = it
        },
        onValueChangeFinished = {
            val target = (sliderValue * duration).toLong()
            PlayerController.seekTo(target)
            pendingSeek = target
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
    Spacer(Modifier.height(8.dp))
}


@Composable
private fun LiveLyrics(song: Song, modifier: Modifier, onShareLyrics: (List<String>) -> Unit) {
    var lyrics by remember(song.id) { mutableStateOf<SongLyrics?>(null) }
    var failed by remember(song.id) { mutableStateOf(false) }
    var attempt by remember(song.id) { mutableStateOf(0) }

    LaunchedEffect(song.id, attempt) {
        failed = false
        var checks = 0
        while (PlayerController.duration() <= 0 && checks++ < 12) delay(125)
        val duration = PlayerController.duration() / 1000
        try {
            lyrics = runInterruptible(Dispatchers.IO) { LyricsRepository.fetch(song, duration) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }

    androidx.compose.runtime.key(song.id) {
        val position = if (lyrics?.synced?.isNotEmpty() == true) livePlaybackPosition(song.id) else 0L
        LyricsView(
            lyrics, position, PlayerController::seekTo,
            modifier = modifier, canSeek = !PlayerController.controlsLocked,
            retry = if (failed) ({ attempt++ }) else null,
            onShareLyrics = onShareLyrics
        )
    }
}