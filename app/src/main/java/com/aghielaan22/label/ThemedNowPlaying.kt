package com.aghielaan22.label

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.WindowCompat

@Composable
fun NowPlayingScreen(visible: Boolean = true, onClose: () -> Unit) {
    val song = PlayerController.currentSong
    var pane by remember { mutableStateOf(PlayerPane.Artwork) }
    val colors = if (AppSettings.appTheme == AppTheme.AlbumArt) LocalSongColors.current
    else rememberSongColors(song)

    val background by animateColorAsState(
        targetValue = colors?.background ?: Color(0xFF121212),
        animationSpec = tween(700),
        label = "background"
    )
    val accent by animateColorAsState(
        targetValue = colors?.accent ?: Color.White,
        animationSpec = tween(700),
        label = "accent"
    )

    // The phone's clock and battery icons must turn light on a dark background
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val insets = WindowCompat.getInsetsController(window, view)
        val previousStatus = insets.isAppearanceLightStatusBars
        val previousNav = insets.isAppearanceLightNavigationBars
        insets.isAppearanceLightStatusBars = false
        insets.isAppearanceLightNavigationBars = false
        onDispose {
            insets.isAppearanceLightStatusBars = previousStatus
            insets.isAppearanceLightNavigationBars = previousNav
        }
    }

    val glass = rememberGlassState()
    val showMusicGlow = !AppSettings.batterySaver && AppSettings.musicReactiveGlow && visible &&
            pane == PlayerPane.Artwork && LocalWindowInfo.current.isWindowFocused &&
            PlayerController.isPlaying

    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = if (accent.luminance() >= 0.18f) Color.Black else Color.White
        )
    }

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalGlassState provides glass,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (pane == PlayerPane.Artwork) Modifier.dragDownToDismiss(onClose) else Modifier)
                    .background(Color.Black)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .glassSource(glass)
                        .playerBackdrop(background, accent)
                )
                if (showMusicGlow) MusicReactiveGlow(accent, song?.id)

                NowPlayingContent(
                    pane = pane,
                    onPaneChange = { pane = it },
                    onClose = onClose,
                    visible = visible
                )
            }
        }
    }
}

/** Palette-based depth without decoding or blurring a second full-screen cover. */
private fun Modifier.playerBackdrop(background: Color, accent: Color): Modifier = drawWithCache {
    val night = Color(0xFF0D0C12)
    val wash = Brush.verticalGradient(
        listOf(lerp(night, background, 0.72f), night, Color.Black),
        endY = size.height.coerceAtLeast(1f)
    )
    val glow = Brush.radialGradient(
        listOf(accent.copy(alpha = 0.22f), Color.Transparent),
        center = Offset(size.width * 0.80f, size.height * 0.19f),
        radius = (size.maxDimension * 0.53f).coerceAtLeast(1f)
    )
    onDrawBehind {
        drawRect(wash)
        drawRect(glow)
    }
}