package com.aghielaan22.label

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.aghielaan22.label.ui.theme.Typography

enum class AppTheme(val label: String, val seed: Color?, val amoled: Boolean = false) {
    Dynamic("Dynamic", null),
    AlbumArt("Album art", null),
    Violet("Violet", Color(0xFF7C4DFF)),
    Ocean("Ocean", Color(0xFF0288D1)),
    Emerald("Emerald", Color(0xFF00A86B)),
    Sunset("Sunset", Color(0xFFFF6D3A)),
    Rose("Rose", Color(0xFFE91E63)),
    Gold("Gold", Color(0xFFFFB300)),
    Mono("Mono", Color(0xFF9E9E9E)),
    Amoled("AMOLED", Color(0xFF7C4DFF), amoled = true)
}

private fun hsv(h: Float, s: Float, v: Float): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/** Builds a complete, matching palette from one seed color. */
fun colorSchemeFromSeed(seed: Color, dark: Boolean, amoled: Boolean = false): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    val s = hsv[1]
    val h2 = (h + 25f) % 360f
    val h3 = (h + 320f) % 360f

    return if (dark) {
        val bg = if (amoled) Color.Black else hsv(h, s * 0.30f, 0.09f)
        val tint = if (amoled) 0.25f else 0.30f
        darkColorScheme(
            primary = hsv(h, s * 0.55f, 1.0f),
            onPrimary = hsv(h, s, 0.20f),
            primaryContainer = hsv(h, s * 0.9f, 0.38f),
            onPrimaryContainer = hsv(h, s * 0.18f, 1.0f),
            secondary = hsv(h2, s * 0.35f, 0.92f),
            onSecondary = hsv(h2, s, 0.18f),
            secondaryContainer = hsv(h2, s * 0.5f, 0.33f),
            onSecondaryContainer = hsv(h2, s * 0.15f, 0.96f),
            tertiary = hsv(h3, s * 0.45f, 0.95f),
            onTertiary = hsv(h3, s, 0.20f),
            tertiaryContainer = hsv(h3, s * 0.6f, 0.35f),
            onTertiaryContainer = hsv(h3, s * 0.15f, 0.97f),
            background = bg,
            onBackground = hsv(h, s * 0.05f, 0.93f),
            surface = bg,
            onSurface = hsv(h, s * 0.05f, 0.93f),
            surfaceVariant = hsv(h, s * 0.22f, 0.22f),
            onSurfaceVariant = hsv(h, s * 0.12f, 0.80f),
            surfaceTint = hsv(h, s * 0.55f, 1.0f),
            outline = hsv(h, s * 0.15f, 0.55f),
            outlineVariant = hsv(h, s * 0.20f, 0.30f),
            surfaceDim = bg,
            surfaceBright = hsv(h, s * tint, 0.25f),
            surfaceContainerLowest = if (amoled) Color.Black else hsv(h, s * tint, 0.06f),
            surfaceContainerLow = hsv(h, s * tint, if (amoled) 0.05f else 0.11f),
            surfaceContainer = hsv(h, s * tint, if (amoled) 0.07f else 0.13f),
            surfaceContainerHigh = hsv(h, s * tint, if (amoled) 0.10f else 0.17f),
            surfaceContainerHighest = hsv(h, s * tint, if (amoled) 0.14f else 0.21f)
        )
    } else {
        val bg = hsv(h, s * 0.04f, 0.99f)
        lightColorScheme(
            primary = hsv(h, s, 0.62f),
            onPrimary = Color.White,
            primaryContainer = hsv(h, s * 0.25f, 1.0f),
            onPrimaryContainer = hsv(h, s, 0.25f),
            secondary = hsv(h2, s * 0.6f, 0.55f),
            onSecondary = Color.White,
            secondaryContainer = hsv(h2, s * 0.15f, 0.97f),
            onSecondaryContainer = hsv(h2, s, 0.25f),
            tertiary = hsv(h3, s * 0.7f, 0.60f),
            onTertiary = Color.White,
            tertiaryContainer = hsv(h3, s * 0.18f, 0.98f),
            onTertiaryContainer = hsv(h3, s, 0.28f),
            background = bg,
            onBackground = hsv(h, s * 0.30f, 0.12f),
            surface = bg,
            onSurface = hsv(h, s * 0.30f, 0.12f),
            surfaceVariant = hsv(h, s * 0.10f, 0.92f),
            onSurfaceVariant = hsv(h, s * 0.25f, 0.30f),
            surfaceTint = hsv(h, s, 0.62f),
            outline = hsv(h, s * 0.15f, 0.50f),
            outlineVariant = hsv(h, s * 0.10f, 0.80f),
            surfaceDim = hsv(h, s * 0.06f, 0.87f),
            surfaceBright = bg,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsv(h, s * 0.04f, 0.98f),
            surfaceContainer = hsv(h, s * 0.05f, 0.96f),
            surfaceContainerHigh = hsv(h, s * 0.06f, 0.94f),
            surfaceContainerHighest = hsv(h, s * 0.07f, 0.92f)
        )
    }
}

@Composable
fun LAbelAppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val theme = AppSettings.appTheme

    val colors = if (theme == AppTheme.AlbumArt) rememberSongColors(PlayerController.currentSong) else null
    val base = if (theme == AppTheme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorSchemeFromSeed(
            seed = colors?.accent ?: theme.seed ?: AppTheme.Violet.seed!!,
            dark = darkTheme || theme.amoled,
            amoled = theme.amoled
        )
    }

    // With liquid glass on, plain surfaces become see-through, so the glow shows behind
    // list rows and pages instead of dark strips.
    val colorScheme = if (AppSettings.glassEffectsEnabled) base.copy(surface = Color.Transparent) else base
    androidx.compose.runtime.CompositionLocalProvider(LocalSongColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}