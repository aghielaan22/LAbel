package com.aghielaan22.label

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val song = PlayerController.currentSong
    var colors by remember { mutableStateOf<PlayerColors?>(null) }

    // Work out the cover's colors whenever the song changes
    LaunchedEffect(song?.thumbnailUrl) {
        val url = song?.thumbnailUrl ?: return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) { ArtworkColors.extract(url) }
            if (result != null) colors = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the previous colors if this one fails
        }
    }

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

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, onPrimary = Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(background, Color.Black)))
        ) {
            NowPlayingContent(onClose)
        }
    }
}