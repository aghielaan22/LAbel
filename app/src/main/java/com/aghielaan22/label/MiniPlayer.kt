package com.aghielaan22.label

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs

@Composable
fun MiniPlayer(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val song = PlayerController.currentSong ?: return

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeX = with(density) { 64.dp.toPx() }
    val swipeY = with(density) { 28.dp.toPx() }
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320))
    ) {
        var drag by remember { mutableStateOf(Offset.Zero) }

        Surface(
            tonalElevation = 3.dp,
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        },
                        onDragEnd = {
                            val horizontal = abs(drag.x) > abs(drag.y)
                            when {
                                !horizontal && drag.y < -swipeY -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClick()
                                }

                                horizontal && drag.x > swipeX -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PlayerController.previous()
                                }

                                horizontal && drag.x < -swipeX -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PlayerController.next()
                                }
                            }
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.artworkUrl(192),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { PlayerController.togglePlayPause() }) {
                    PlayPauseIcon(
                        isPlaying = PlayerController.isPlaying,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { PlayerController.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}