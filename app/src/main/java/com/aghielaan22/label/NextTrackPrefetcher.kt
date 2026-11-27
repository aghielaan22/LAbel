package com.aghielaan22.label

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

@OptIn(UnstableApi::class)
internal class NextTrackPrefetcher(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var request: Warmup? = null
    private var job: Job? = null

    private data class Warmup(val currentId: String, val url: String, val quality: AudioQuality)

    fun update(player: Player) {
        if (AppSettings.batterySaver) {
            job?.cancel()
            job = null
            request = null
            return
        }

        val quality = AppSettings.audioQuality
        val index = player.nextMediaItemIndex
        val current = player.currentMediaItem
        val next = if (index in 0 until player.mediaItemCount && index != player.currentMediaItemIndex) {
            player.getMediaItemAt(index).localConfiguration?.uri
        } else null

        if (current == null || next == null || quality == AudioQuality.DataSaver ||
            player.repeatMode == Player.REPEAT_MODE_ONE || !player.isPlaying
        ) {
            if (job?.isActive == true) {
                job?.cancel()
                request = null
            }
            return
        }

        val target = Warmup(current.mediaId, next.toString(), quality)
        if (target == request) {
            if (job?.isActive == true && !unmeteredNetwork()) {
                job?.cancel()
                request = null
            }
            return
        }
        job?.cancel()
        job = null
        request = null
        val comfortablyBuffered = player.totalBufferedDuration >= 8_000 ||
                (player.duration > 0 && player.bufferedPosition >= player.duration)
        if (!comfortablyBuffered || PlaybackAudioCache.hasFullTrack(target.url, quality) || !unmeteredNetwork()) return

        request = target
        job = scope.launch {
            try {
                runInterruptible { MusicRepository.getAudioUrl(target.url, quality) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Playback can resolve the URL normally if this opportunistic call fails.
            }
        }
    }

    private fun unmeteredNetwork(): Boolean {
        val manager = connectivity ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun close() = scope.cancel()
}