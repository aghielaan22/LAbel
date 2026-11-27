package com.aghielaan22.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private fun frequencyLabel(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f).replace(".0", "") else "$hz Hz"

@Composable
fun SoundScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = "Sound", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsGroup("Equalizer") {
                SwitchRow(
                    title = "Equalizer",
                    description = if (SoundEngine.available) {
                        "Shape the sound across five frequency bands. It starts working once a song plays."
                    } else {
                        "This phone doesn't support the equalizer."
                    },
                    checked = SoundEngine.eqEnabled,
                    enabled = SoundEngine.available
                ) { SoundEngine.updateEqEnabled(it) }

                if (SoundEngine.eqEnabled) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundEngine.presets.forEach { preset ->
                            FilterChip(
                                selected = SoundEngine.preset == preset.name,
                                onClick = { SoundEngine.applyPreset(preset) },
                                label = { Text(preset.name) }
                            )
                        }
                        if (SoundEngine.preset == "Custom") {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Custom") }
                            )
                        }
                    }

                    SoundEngine.bandFrequencies.forEachIndexed { index, hz ->
                        EqBandRow(
                            label = frequencyLabel(hz),
                            gain = SoundEngine.gains[index]
                        ) { SoundEngine.updateGain(index, it) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SettingsGroup("Loudness boost") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Boost", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (SoundEngine.loudnessDb <= 0f) "Off"
                            else "+%.0f dB".format(SoundEngine.loudnessDb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Makes quiet songs louder. Start low, because a big boost can distort loud songs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = SoundEngine.loudnessDb,
                        onValueChange = { SoundEngine.updateLoudness(it) },
                        valueRange = 0f..12f,
                        steps = 11
                    )
                }
            }

            SettingsGroup("Playback") {
                SwitchRow(
                    title = "Skip silence",
                    description = "Trims quiet gaps. It can also shorten the natural pauses in some music.",
                    checked = SoundEngine.skipSilence
                ) { SoundEngine.updateSkipSilence(it) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EqBandRow(label: String, gain: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = gain,
            onValueChange = onChange,
            valueRange = -12f..12f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "%+.0f dB".format(gain),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp)
        )
    }
}