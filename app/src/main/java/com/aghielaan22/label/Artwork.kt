package com.aghielaan22.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Fills the frame with square covers and fits wider images inside it.
 * A blurred backdrop is reserved for large artwork in the player.
 */
@Composable
fun Artwork(
    url: String?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    blurBackdrop: Boolean = false
) {
    var failed by remember(url) { mutableStateOf(false) }
    var squareish by remember(url) { mutableStateOf(true) }
    val model = if (failed && fallbackUrl != null) fallbackUrl else url

    val showBlur = !squareish && blurBackdrop && !AppSettings.batterySaver
    val frame = if (!squareish && !showBlur) {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else Modifier
    Box(modifier = modifier.clip(shape).then(frame)) {
        if (showBlur) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .graphicsLayer { alpha = 0.55f }
            )
        }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = if (squareish) ContentScale.Crop else ContentScale.Fit,
            onError = {
                if (!failed && fallbackUrl != null && fallbackUrl != url) failed = true
            },
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.isSpecified && size.height > 0f) {
                    squareish = (size.width / size.height) in 0.9f..1.1f
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}