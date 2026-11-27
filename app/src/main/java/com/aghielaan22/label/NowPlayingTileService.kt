package com.aghielaan22.label

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NowPlayingTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null
    private var command: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            NowPlayingWidget.tilePlayback.collect { playback ->
                updateTile(playback ?: NowPlayingWidget.currentTilePlayback(applicationContext))
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val playback = NowPlayingWidget.currentTilePlayback(applicationContext)
        if (playback.controlsLocked || command?.isActive == true) return

        if (!playback.hasTrack) {
            if (isLocked) unlockAndRun { openApp() } else openApp()
            return
        }

        val action = if (playback.isPlaying) WidgetAction.Pause else WidgetAction.Play
        command = scope.launch {
            try {
                PlayerController.runWidgetAction(applicationContext, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NowPlayingTile", "Could not handle playback action", e)
            }
        }
    }

    private fun updateTile(playback: TilePlayback) {
        val tile = qsTile ?: return
        val actionLabel = getString(when {
            playback.controlsLocked -> R.string.tile_label_studio
            !playback.hasTrack -> R.string.tile_label_open
            playback.isPlaying -> R.string.widget_label_pause
            else -> R.string.widget_label_play
        })
        tile.state = when {
            playback.controlsLocked -> Tile.STATE_UNAVAILABLE
            playback.isPlaying -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.app_name)
        tile.contentDescription = getString(R.string.tile_label_accessibility, tile.label, actionLabel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = actionLabel
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}