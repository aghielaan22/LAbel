package com.aghielaan22.label

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun GestureArtwork(song: Song?, size: Dp, animate: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val currentSong by rememberUpdatedState(song)
    var heart by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }

    fun settle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(offsetX).animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) {
                offsetX = value
            }
        }
    }

    LaunchedEffect(heart) {
        if (heart) {
            delay(700)
            heart = false
        }
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        val style = AppSettings.artworkStyle
        NowPlayingArtwork(
            song = song,
            style = style,
            size = size,
            animate = animate && style == ArtworkStyle.Vinyl,
            playing = style == ArtworkStyle.Vinyl && PlayerController.isPlaying,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    rotationZ = offsetX / 60f
                    alpha = 1f - (abs(offsetX) / (threshold * 6f)).coerceIn(0f, 0.4f)
                }
                .pointerInput(threshold) {
                    detectHorizontalDragGestures(
                        onDragStart = { settleJob?.cancel() },
                        onDragEnd = {
                            when {
                                offsetX > threshold -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PlayerController.previous()
                                }
                                offsetX < -threshold -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PlayerController.next()
                                }
                            }
                            settle()
                        },
                        onDragCancel = ::settle,
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-threshold * 4, threshold * 4)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            currentSong?.let {
                                if (!LibraryRepository.isLiked(it)) LibraryRepository.toggleLike(it)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                heart = true
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = heart,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )
        }
    }
}