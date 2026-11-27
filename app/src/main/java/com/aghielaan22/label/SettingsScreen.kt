package com.aghielaan22.label

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var confirmClearSearches by remember { mutableStateOf(false) }
    var confirmClearPlays by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsGroup("Appearance") {
                ChoiceSetting(
                    title = "Brightness",
                    description = null,
                    options = ThemeMode.entries.map { it to it.label },
                    selected = AppSettings.themeMode
                ) { AppSettings.updateThemeMode(it) }
                ThemePicker()
                SwitchRow(
                    title = "Music-reactive glow",
                    description = "Bass gently pulses behind the artwork in Now Playing.",
                    checked = AppSettings.musicReactiveGlow
                ) { AppSettings.updateMusicReactiveGlow(it) }
            }

            SettingsGroup("Performance") {
                SwitchRow(
                    title = "Battery Saver",
                    description = "Uses simpler visuals and skips next-track prefetching.",
                    checked = AppSettings.batterySaver,
                    onCheckedChange = AppSettings::updateBatterySaver
                )
            }

            SettingsGroup("Liquid glass") {
                SwitchRow(
                    title = "Liquid glass effect",
                    description = "Frosted panels and a softly moving glow. Turn off for a classic solid look.",
                    checked = AppSettings.glassEnabled
                ) { AppSettings.updateGlassEnabled(it) }

                if (AppSettings.glassEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Transparency", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${(AppSettings.glassTransparency * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = AppSettings.glassTransparency,
                            onValueChange = { AppSettings.updateGlassTransparency(it) },
                            valueRange = 0f..1f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Frosted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsGroup("Playback") {
                ChoiceSetting(
                    title = "Streaming quality",
                    description = "Applies to songs that start playing after you change it.",
                    options = AudioQuality.entries.map { it to it.label },
                    selected = AppSettings.audioQuality
                ) { AppSettings.updateAudioQuality(it) }
                ChoiceSetting(
                    title = "Fade between songs",
                    description = "Each song fades out at its end and the next one fades in.",
                    options = listOf(0 to "Off", 2 to "2 s", 4 to "4 s", 6 to "6 s"),
                    selected = PlaybackSettings.fadeSeconds
                ) { PlaybackSettings.updateFadeSeconds(it) }
                SwitchRow(
                    title = "Autoplay similar songs",
                    description = "When the queue is about to run out, keep playing songs similar to the last one.",
                    checked = AppSettings.autoplay
                ) { AppSettings.updateAutoplay(it) }
            }

            SettingsGroup("Sound") {
                ListItem(
                    modifier = Modifier.clickable { Navigator.open(DetailPage.SoundPage) },
                    colors = transparent,
                    headlineContent = { Text("Equalizer and sound") },
                    supportingContent = { Text("Equalizer, loudness boost and skip silence") }
                )
            }

            AudioCacheSettings()
            PlaybackDiagnosticsSettings()

            SettingsGroup("Data") {
                ListItem(
                    modifier = Modifier.clickable { confirmClearSearches = true },
                    colors = transparent,
                    headlineContent = { Text("Clear search history") },
                    supportingContent = { Text("${SearchHistoryRepository.recent.size} saved") }
                )
                ListItem(
                    modifier = Modifier.clickable { confirmClearPlays = true },
                    colors = transparent,
                    headlineContent = { Text("Clear recently played") },
                    supportingContent = { Text("${HistoryRepository.recentSongs.size} saved") }
                )
            }

            SettingsGroup("Backup") {
                BackupSection()
            }
            ScrobbleSettings()
            UpdatesSettings()

            SettingsGroup("About") {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(version) }
                )
                ListItem(
                    modifier = Modifier.clickable { showAbout = true },
                    colors = transparent,
                    headlineContent = { Text("About LAbel") },
                    supportingContent = { Text("Credits and licenses") }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClearSearches) {
        ConfirmDialog(
            title = "Clear search history?",
            message = "Your recent searches will be removed.",
            onConfirm = {
                SearchHistoryRepository.clear()
                confirmClearSearches = false
                Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClearSearches = false }
        )
    }

    if (confirmClearPlays) {
        ConfirmDialog(
            title = "Clear recently played?",
            message = "The Recently played shelf on Home will be emptied. " +
                    "Your liked songs and playlists are not affected.",
            onConfirm = {
                HistoryRepository.clear()
                confirmClearPlays = false
                Toast.makeText(context, "Recently played cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClearPlays = false }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About LAbel") },
            text = {
                Text(
                    "LAbel is a personal learning project and is licensed under GPL-3.0.\n\n" +
                            "Built with open-source software:\n" +
                            "• NewPipeExtractor (GPL-3.0)\n" +
                            "• Jetpack Compose, Media3, Palette, Coil and OkHttp (Apache 2.0)\n" +
                            "• Haze for the glass blur\n" +
                            "• Synced lyrics from LRCLIB\n\n" +
                            "LAbel is not affiliated with, endorsed by, or connected to " +
                            "YouTube or Google."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}