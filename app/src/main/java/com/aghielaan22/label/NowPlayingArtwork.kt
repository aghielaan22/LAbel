package com.aghielaan22.label

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.coroutines.coroutineContext

enum class ArtworkStyle(val label: String, val description: String) {
    Cover("Cover", "Full album artwork"),
    Vinyl("Vinyl", "A spinning record with the cover as its label"),
    Sleeve("Sleeve", "An album jacket with the record peeking out")
}

@Composable
internal fun NowPlayingArtwork(
    song: Song?,
    style: ArtworkStyle,
    size: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    playing: Boolean = false,
    preview: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary
    val artworkPixels = if (preview) 192 else 1024

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (style) {
            ArtworkStyle.Cover -> AlbumArtwork(
                song, artworkPixels, RoundedCornerShape(if (preview) 7.dp else 22.dp),
                Modifier.fillMaxSize()
            )
            ArtworkStyle.Vinyl -> VinylRecord(
                song, artworkPixels, accent, Modifier.fillMaxSize(),
                animate = animate && !AppSettings.batterySaver, playing = playing
            )
            ArtworkStyle.Sleeve -> {
                VinylRecord(
                    song, artworkPixels, accent,
                    Modifier.align(Alignment.CenterEnd).size(size * 0.82f),
                    showAlbumLabel = false
                )
                val sleeveShape = RoundedCornerShape(if (preview) 5.dp else 15.dp)
                AlbumArtwork(
                    song, artworkPixels, sleeveShape,
                    Modifier.align(Alignment.CenterStart)
                        .size(size * 0.73f)
                        .graphicsLayer {
                            shadowElevation = if (preview) 0f else 9.dp.toPx()
                            shape = sleeveShape
                            clip = true
                        }
                )
            }
        }
    }
}

@Composable
private fun AlbumArtwork(song: Song?, pixels: Int, shape: Shape, modifier: Modifier) {
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.30f)
        )

        Artwork(
            url = song?.artworkUrl(pixels),
            fallbackUrl = song?.thumbnailUrl,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
            blurBackdrop = true
        )
    }
}

private class RecordMotion {
    var angle by mutableFloatStateOf(0f)
    var speed = 0f
}

@Composable
private fun VinylRecord(
    song: Song?,
    pixels: Int,
    accent: Color,
    modifier: Modifier,
    animate: Boolean = false,
    playing: Boolean = false,
    showAlbumLabel: Boolean = true
) {
    val motion = remember { RecordMotion() }
    val focused = LocalWindowInfo.current.isWindowFocused

    LaunchedEffect(animate, playing, focused) {
        if (!animate || !focused) {
            motion.speed = 0f
            return@LaunchedEffect
        }
        if (!playing && motion.speed == 0f) return@LaunchedEffect

        val motionScale = coroutineContext[MotionDurationScale]
        var previousNanos = 0L
        var moving = true
        while (moving) {
            moving = withFrameNanos { now ->
                val durationScale = motionScale?.scaleFactor ?: 1f
                if (durationScale <= 0f) {
                    motion.speed = 0f
                    false
                } else {
                    val elapsed = if (previousNanos == 0L) 0f
                    else ((now - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    previousNanos = now
                    val seconds = elapsed / durationScale.coerceAtLeast(0.2f)
                    val targetSpeed = if (playing) 46f else 0f
                    val step = 136f * seconds
                    val oldSpeed = motion.speed
                    motion.speed += (targetSpeed - motion.speed).coerceIn(-step, step)
                    motion.angle = (motion.angle + (oldSpeed + motion.speed) * 0.5f * seconds) % 360f
                    playing || motion.speed > 0f
                }
            }
        }
    }

    Box(
        modifier.graphicsLayer { rotationZ = motion.angle },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().drawWithCache {
            val radius = size.minDimension / 2f
            val vinyl = lerp(Color(0xFF121117), accent, 0.10f)
            val reflection = lerp(Color(0xFF3C3941), accent, 0.25f)
            val finish = Brush.sweepGradient(
                0.00f to vinyl,
                0.10f to reflection,
                0.23f to Color(0xFF101014),
                0.48f to vinyl,
                0.59f to reflection,
                0.72f to Color(0xFF101014),
                1.00f to vinyl,
                center = Offset(size.width / 2f, size.height / 2f)
            )
            val innerRings = (0..17).map { radius * (0.47f + it * 0.028f) }
            val rimWidth = (radius * 0.009f).coerceAtLeast(1f)
            onDrawBehind {
                if (radius > 0f) {
                    drawCircle(finish, radius = radius)
                    for (ring in innerRings) {
                        drawCircle(Color(0x41000000), radius = ring, style = Stroke(rimWidth))
                        drawCircle(Color(0x18FFFFFF), radius = (ring - rimWidth).coerceAtLeast(0f), style = Stroke(rimWidth * 0.45f))
                    }
                    drawCircle(Color(0x42FFFFFF), radius = (radius - rimWidth).coerceAtLeast(0f), style = Stroke(rimWidth))
                }
            }
        }) {}
        if (showAlbumLabel) {
            AlbumArtwork(song, pixels, CircleShape, Modifier.fillMaxSize(0.45f))
        } else {
            Box(
                Modifier.fillMaxSize(0.45f).clip(CircleShape)
                    .background(lerp(Color(0xFF282431), accent, 0.32f))
            )
        }
        Box(Modifier.fillMaxSize(0.035f).clip(CircleShape).background(Color(0xFF0C0A0F)))
    }
}