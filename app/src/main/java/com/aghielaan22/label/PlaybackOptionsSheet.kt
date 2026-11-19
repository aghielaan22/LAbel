package com.aghielaan22.label

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun countdownText(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackOptionsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    SleepTimer.atEndOfSong -> "Music will stop at the end of this song."
                    SleepTimer.endAtMs > 0L ->
                        "Music will stop in ${countdownText(SleepTimer.remainingSeconds)}."
                    else -> "Off"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tip: add a 1 to this list while testing, then take it out again
                listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = SleepTimer.activeMinutes == minutes,
                        onClick = { SleepTimer.startMinutes(minutes) },
                        label = { Text("$minutes min") }
                    )
                }
                FilterChip(
                    selected = SleepTimer.atEndOfSong,
                    onClick = { SleepTimer.startEndOfSong() },
                    label = { Text("End of song") }
                )
            }
            if (SleepTimer.isActive) {
                TextButton(onClick = { SleepTimer.cancel() }) { Text("Turn off timer") }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Fade between songs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Each song fades out at its end and the next one fades in. " +
                        "This isn't an overlapping crossfade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 2 to "2 s", 4 to "4 s", 6 to "6 s").forEach { (seconds, label) ->
                    FilterChip(
                        selected = PlaybackSettings.fadeSeconds == seconds,
                        onClick = { PlaybackSettings.updateFadeSeconds(seconds) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}