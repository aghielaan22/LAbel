package com.aghielaan22.label

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** The "what glass should blur" state, shared with everything inside a screen. */
val LocalGlassState = staticCompositionLocalOf<GlassBackdrop?> { null }

class GlassBackdrop(val layer: GraphicsLayer, val haze: HazeState = HazeState()) {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassState(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marks this area as the background that nearby glass panels will blur. */
fun Modifier.glassSource(state: GlassBackdrop): Modifier = composed {
    when {
        !AppSettings.glassEffectsEnabled -> this
        !androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated -> this
        Build.VERSION.SDK_INT < 33 -> this.hazeSource(state.haze)
        else -> this.onGloballyPositioned { state.origin = it.positionInWindow() }
            .drawWithContent {
                state.layer.record { this@drawWithContent.drawContent() }
                drawLayer(state.layer)
            }
    }
}

/**
 * A frosted-glass panel: blurred backdrop, tint, a soft sheen on top,
 * and a bright rim that catches the light.
 * With liquid glass switched off, it becomes a plain solid panel.
 */
fun Modifier.glassPanel(shape: Shape, blur: Dp? = null): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    if (!AppSettings.glassEffectsEnabled) {
        return@composed this
            .clip(shape)
            .background(scheme.surfaceContainerHigh, shape)
    }

    val state = LocalGlassState.current
    val t = AppSettings.glassTransparency

    // More transparency means a lighter tint and less blur
    val fill = if (dark) {
        lerp(Color.Black.copy(alpha = 0.60f), Color.White.copy(alpha = 0.05f), t)
    } else {
        lerp(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.12f), t)
    }
    val radius = blur ?: lerpDp(36.dp, 18.dp, t)

    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.30f), Color.Transparent)
    )
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = if (dark) 0.28f else 0.55f)
        )
    )

    var result: Modifier = this.clip(shape)
    result = if (state != null && Build.VERSION.SDK_INT >= 33 &&
        androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated) {
        result.refractedBackdrop(state, radius, t).background(fill, shape)
    } else if (state != null && Build.VERSION.SDK_INT < 33) {
        result.hazeEffect(state = state.haze) {
            blurRadius = radius
            tints = listOf(HazeTint(fill))
            noiseFactor = 0.03f
        }
    } else {
        result.background(fill, shape)
    }
    result
        .background(sheen, shape)
        .border(1.dp, rim, shape)
}

/** Song-colored gradients cached until the palette or screen size changes. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    if (!AppSettings.glassEffectsEnabled) return

    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier = modifier.fillMaxSize().drawWithCache {
            fun glow(color: Color, x: Float, y: Float, radius: Float): Brush =
                Brush.radialGradient(
                    listOf(color, Color.Transparent),
                    center = Offset(size.width * x, size.height * y),
                    radius = (size.width * radius).coerceAtLeast(1f)
                )

            val top = glow(scheme.primary.copy(alpha = 0.30f), 0.32f, 0.18f, 0.9f)
            val middle = glow(scheme.tertiary.copy(alpha = 0.26f), 0.88f, 0.48f, 0.8f)
            val bottom = glow(scheme.secondary.copy(alpha = 0.24f), 0.46f, 0.80f, 0.9f)
            onDrawBehind {
                drawRect(top)
                drawRect(middle)
                drawRect(bottom)
            }
        }
    ) {}
}