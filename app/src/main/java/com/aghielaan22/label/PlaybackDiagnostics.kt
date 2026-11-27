package com.aghielaan22.label

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

private val diagnosticSong = Song(
    id = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    title = "Never Gonna Give You Up",
    artist = "Rick Astley",
    thumbnailUrl = null
)

private enum class Check(val label: String) {
    Search("Search"),
    Stream("Stream resolution"),
    Playback("Audio playback"),
    Lyrics("Lyrics")
}

private enum class Outcome(val label: String) {
    Passed("Passed"),
    Failed("Failed"),
    Unavailable("No result"),
    Skipped("Skipped")
}

private data class Finding(val outcome: Outcome, val detail: String)
private data class CheckResult(val check: Check, val finding: Finding, val elapsedMs: Long?)

private fun passed(detail: String) = Finding(Outcome.Passed, detail)

private suspend fun runCheck(check: Check, action: suspend () -> Finding): CheckResult {
    val started = SystemClock.elapsedRealtime()
    val finding = try {
        withTimeoutOrNull(18_000L) { action() }
            ?: Finding(Outcome.Failed, "The check timed out.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Finding(Outcome.Failed, safeFailure(e))
    }
    return CheckResult(check, finding, SystemClock.elapsedRealtime() - started)
}

private fun safeFailure(error: Exception): String {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
    val http = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
    val playback = causes.filterIsInstance<PlaybackException>().firstOrNull()

    return when {
        http != null -> "The audio server returned HTTP ${http.responseCode}."
        causes.any { it is UnknownHostException } -> "Could not resolve the server address."
        causes.any { it is SocketTimeoutException } -> "The server did not respond in time."
        causes.any { it is ReCaptchaException } -> "The provider requested a verification challenge."
        playback != null -> "Media3 reported ${playback.errorCodeName}."
        else -> "The check could not complete (${error.javaClass.simpleName})."
    }
}

private suspend fun runPlaybackDiagnostics(
    context: Context,
    quality: AudioQuality,
    onResult: (CheckResult) -> Unit
) {
    onResult(runCheck(Check.Search) {
        val tracks = runInterruptible(Dispatchers.IO) {
            MusicRepository.searchSongs("${diagnosticSong.title} ${diagnosticSong.artist}")
        }
        if (tracks.isEmpty()) Finding(Outcome.Unavailable, "Search returned no songs.")
        else passed("Search returned ${tracks.size} song results.")
    })

    var streamUri: Uri? = null
    val streamResult = runCheck(Check.Stream) {
        val resolved = runInterruptible(Dispatchers.IO) {
            MusicRepository.getAudioUrl(diagnosticSong.id, quality, useCache = false)
        }
        val uri = Uri.parse(resolved)
        if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
            Finding(Outcome.Failed, "The provider returned an invalid stream address.")
        } else {
            streamUri = uri
            passed("Resolved a playable audio address for the sample.")
        }
    }
    onResult(streamResult)

    val uri = streamUri
    if (uri != null && streamResult.finding.outcome == Outcome.Passed) {
        onResult(runCheck(Check.Playback) { probeAudio(context, uri) })
    } else {
        onResult(
            CheckResult(
                Check.Playback,
                Finding(Outcome.Skipped, "Stream resolution did not supply an audio address."),
                null
            )
        )
    }

    onResult(runCheck(Check.Lyrics) {
        val lyrics = runInterruptible(Dispatchers.IO) {
            LyricsRepository.fetch(diagnosticSong, durationSec = 0L, useCache = false)
        }
        when {
            lyrics.synced.isNotEmpty() -> passed("Timed lyrics were returned.")
            !lyrics.plain.isNullOrBlank() -> passed("Plain lyrics were returned.")
            else -> Finding(Outcome.Unavailable, "The lyrics service responded without a match.")
        }
    })
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun probeAudio(context: Context, uri: Uri): Finding =
    withContext(Dispatchers.Main.immediate) {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(6_000)
            .setReadTimeoutMs(6_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_000, 2_500, 250, 500)
            .build()

        val probe = ExoPlayer.Builder(context.applicationContext)
            .setLooper(Looper.getMainLooper())
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, false)
            .build()

        try {
            probe.volume = 0f
            probe.setMediaItem(MediaItem.fromUri(uri))
            probe.prepare()
            probe.play()
            while (probe.playbackState != Player.STATE_ENDED &&
                (!probe.isPlaying || probe.currentPosition < 400L)
            ) {
                probe.playerError?.let { throw it }
                delay(75L)
            }
            probe.playerError?.let { throw it }
            if (probe.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO) &&
                probe.currentPosition >= 400L
            ) {
                passed("Muted playback advanced with an audio track selected.")
            } else {
                Finding(Outcome.Failed, "Playback ended before audio could be confirmed.")
            }
        } finally {
            probe.release()
        }
    }

@Composable
internal fun PlaybackDiagnosticsSettings() {
    var open by remember { mutableStateOf(false) }
    SettingsGroup("Diagnostics") {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { Text("Playback diagnostics") }
    }
    if (open) PlaybackDiagnosticsDialog(onDismiss = { open = false })
}

@Composable
private fun PlaybackDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<CheckResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback diagnostics") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tests a fixed sample over your connection. Audio plays silently.")
                Check.entries.forEachIndexed { index, check ->
                    val result = results.getOrNull(index)
                    val status = result?.finding?.outcome?.label ?: when {
                        running && index == results.size -> "Testing…"
                        else -> "Waiting"
                    }
                    Text("${check.label} · $status", style = MaterialTheme.typography.titleSmall)
                    if (result != null) {
                        Text(result.finding.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (shareError) {
                    Text("Couldn't open the share sheet.", color = MaterialTheme.colorScheme.error)
                }
                if (!running && results.size == Check.entries.size) {
                    TextButton(onClick = {
                        try {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, diagnosticsReport(app, results))
                            }
                            context.startActivity(Intent.createChooser(share, "Share diagnostics"))
                        } catch (_: RuntimeException) {
                            shareError = true
                        }
                    }) { Text("Share report") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !running, onClick = {
                running = true
                results = emptyList()
                shareError = false
                val quality = AppSettings.audioQuality
                scope.launch {
                    try {
                        runPlaybackDiagnostics(app, quality) { results = results + it }
                    } finally {
                        running = false
                    }
                }
            }) { Text(if (results.isEmpty()) "Test playback" else "Run again") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (running) "Cancel" else "Close") } }
    )
}

private fun diagnosticsReport(context: Context, results: List<CheckResult>): String {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    return buildString {
        appendLine("LAbel playback diagnostics")
        appendLine("App $version · Android API ${Build.VERSION.SDK_INT}")
        results.forEach { result ->
            val elapsed = result.elapsedMs?.let { " ($it ms)" }.orEmpty()
            appendLine(
                "${result.check.label} · ${result.finding.outcome.label}$elapsed — ${result.finding.detail}"
            )
        }
    }.trimEnd()
}