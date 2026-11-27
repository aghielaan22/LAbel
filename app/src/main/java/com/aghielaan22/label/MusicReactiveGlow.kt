package com.aghielaan22.label

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp

@Composable
internal fun MusicReactiveGlow(accent: Color, songId: String?) {
    val observer = remember(songId) { MusicGlowFeed() }
    val strength = observer.level.collectAsState()

    LaunchedEffect(observer) {
        val motion = coroutineContext[MotionDurationScale]
        try {
            snapshotFlow { (motion?.scaleFactor ?: 1f) > 0f }.collect { enabled ->
                if (enabled) MusicGlowAudio.subscribe(observer)
                else MusicGlowAudio.unsubscribe(observer)
            }
        } finally {
            MusicGlowAudio.unsubscribe(observer)
        }
    }

    Box(
        Modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val origin = Offset(size.width * 0.43f, size.height * 0.39f)
            val radius = (size.maxDimension * 0.50f).coerceAtLeast(1f)
            val tint = lerp(Color(0xFF8256D9), accent, 0.58f)
            val brush = Brush.radialGradient(
                0f to tint.copy(alpha = 0.34f),
                0.5f to tint.copy(alpha = 0.17f),
                1f to Color.Transparent,
                center = origin,
                radius = radius
            )
            onDrawBehind {
                val level = strength.value
                if (level > 0f) {
                    scale(0.86f + level * 0.33f, pivot = origin) {
                        drawCircle(brush, radius = radius, center = origin, alpha = level * 0.84f)
                    }
                }
            }
        }
    )
}