package com.aghielaan22.label

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackRecoveryNotice() {
    if (PlayerController.controlsLocked) return
    val failure = PlayerController.playbackFailure
    val retrying = PlayerController.isRetrying
    if (failure == null && !retrying) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)) {
            Text(if (retrying) "Reconnecting playback…" else failure.orEmpty())
            if (!retrying) {
                Row {
                    TextButton(onClick = PlayerController::retryPlayback) { Text("Retry") }
                    if (PlayerController.canSkipFailure) {
                        TextButton(onClick = PlayerController::skipFailedTrack) { Text("Skip") }
                    }
                }
            }
        }
    }
}