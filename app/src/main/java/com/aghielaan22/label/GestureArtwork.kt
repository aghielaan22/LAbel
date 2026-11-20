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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun GestureArtwork(song: Song?, size: Dp) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var heart by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }

    LaunchedEffect(heart) {
        if (heart) {
            delay(700)
            heart = false
        }
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = song?.artworkUrl(1200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = offsetX.value / 60f
                    alpha = 1f - (abs(offsetX.value) / (threshold * 6f)).coerceIn(0f, 0.4f)
                }
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val dx = offsetX.value
                            scope.launch {
                                if (dx > threshold) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PlayerController.previous()
                                } else if (dx < -threshold) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PlayerController.next()
                                }
                                offsetX.animateTo(
                                    0f,
                                    spring(Spring.DampingRatioMediumBouncy)
                                )
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        }
                    )
                }
                .pointerInput(song?.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            song?.let {
                                if (!LibraryRepository.isLiked(it)) LibraryRepository.toggleLike(it)
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            heart = true
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