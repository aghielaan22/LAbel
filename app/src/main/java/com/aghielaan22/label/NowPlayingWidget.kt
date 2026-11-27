package com.aghielaan22.label

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class WidgetAction(val intentAction: String) {
    Play("com.aghielaan22.label.widget.PLAY"),
    Pause("com.aghielaan22.label.widget.PAUSE"),
    Next("com.aghielaan22.label.widget.NEXT")
}

internal data class TilePlayback(
    val hasTrack: Boolean,
    val isPlaying: Boolean,
    val controlsLocked: Boolean
)

class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = WidgetAction.entries.firstOrNull { it.intentAction == intent.action }
        if (action == null) {
            super.onReceive(context, intent)
            return
        }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                PlayerController.runWidgetAction(app, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NowPlayingWidget", "Could not handle widget action", e)
            } finally {
                try {
                    refresh(app)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        internal const val OPEN_PLAYER = "com.aghielaan22.label.widget.OPEN"

        private const val TITLE = "title"
        private const val ARTIST = "artist"
        private const val CAN_NEXT = "can_next"
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        private data class Display(
            val title: String?,
            val artist: String?,
            val isPlaying: Boolean = false,
            val canNext: Boolean = false
        )

        private var live: Display? = null
        private var locked = false

        private val mutableTilePlayback = MutableStateFlow<TilePlayback?>(null)
        internal val tilePlayback = mutableTilePlayback.asStateFlow()

        internal fun currentTilePlayback(context: Context): TilePlayback =
            mutableTilePlayback.value ?: readTilePlayback(context)

        private fun readTilePlayback(context: Context): TilePlayback = TilePlayback(
            hasTrack = (live ?: cachedDisplay(context)).title != null,
            isPlaying = live?.isPlaying == true,
            controlsLocked = locked
        )

        private fun publishPlayback(context: Context) {
            mutableTilePlayback.value = readTilePlayback(context)
            refresh(context)
        }

        @OptIn(UnstableApi::class)
        internal fun attach(context: Context, player: Player): Player.Listener {
            val app = context.applicationContext
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (!events.containsAny(
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_MEDIA_METADATA_CHANGED,
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_PLAYER_ERROR,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                        )
                    ) return
                    val metadata = player.currentMediaItem?.mediaMetadata
                    val display = if (metadata == null) {
                        Display(null, null)
                    } else {
                        Display(
                            title = metadata.title?.toString().takeUnless { it.isNullOrBlank() }
                                ?: app.getString(R.string.widget_label_empty),
                            artist = metadata.artist?.toString(),
                            isPlaying = player.playWhenReady && player.playerError == null &&
                                    player.playbackState != Player.STATE_ENDED,
                            canNext = player.hasNextMediaItem()
                        )
                    }
                    if (display != live) {
                        live = display
                        saveDisplay(app, display)
                        publishPlayback(app)
                    }
                }
            }
            player.addListener(listener)
            return listener
        }

        internal fun updateLock(context: Context, value: Boolean) {
            if (locked == value) return
            locked = value
            publishPlayback(context)
        }

        internal fun serviceClosed(context: Context) {
            live = null
            publishPlayback(context)
        }

        internal fun clearStaleQueue(context: Context) {
            live = null
            preferences(context).edit().clear().apply()
            publishPlayback(context)
        }

        private fun preferences(context: Context) =
            context.getSharedPreferences("now_playing_widget", Context.MODE_PRIVATE)

        private fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun cachedDisplay(context: Context): Display {
            val store = preferences(context)
            return Display(
                store.getString(TITLE, null),
                store.getString(ARTIST, null),
                canNext = store.getBoolean(CAN_NEXT, false)
            )
        }

        private fun saveDisplay(context: Context, display: Display) {
            if (display.copy(isPlaying = false) == cachedDisplay(context)) return
            preferences(context).edit()
                .putString(TITLE, display.title)
                .putString(ARTIST, display.artist)
                .putBoolean(CAN_NEXT, display.canNext)
                .apply()
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val display = live ?: cachedDisplay(context)

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).setAction(OPEN_PLAYER)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                FLAGS
            )
            val hasSong = display.title != null
            val controlsVisible = hasSong && !locked
            val mainAction = if (display.isPlaying) WidgetAction.Pause else WidgetAction.Play
            val subtitle = when {
                locked -> context.getString(R.string.widget_label_studio)
                !hasSong -> context.getString(R.string.widget_label_open)
                else -> display.artist.orEmpty()
            }

            val views = RemoteViews(context.packageName, R.layout.label_now_playing_widget).apply {
                setTextViewText(R.id.widget_title, display.title ?: context.getString(R.string.widget_label_empty))
                setTextViewText(R.id.widget_artist, subtitle)
                setOnClickPendingIntent(R.id.widget_track, open)

                setImageViewResource(
                    R.id.widget_play_pause,
                    if (display.isPlaying) R.drawable.widget_label_pause else R.drawable.widget_label_play
                )
                setContentDescription(
                    R.id.widget_play_pause,
                    context.getString(when {
                        !controlsVisible -> R.string.widget_label_open
                        display.isPlaying -> R.string.widget_label_pause
                        else -> R.string.widget_label_play
                    })
                )
                setOnClickPendingIntent(
                    R.id.widget_play_pause,
                    if (controlsVisible) actionIntent(context, mainAction) else open
                )
                setViewVisibility(R.id.widget_play_pause, if (locked) View.INVISIBLE else View.VISIBLE)
                setViewVisibility(R.id.widget_next, if (controlsVisible && display.canNext) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.widget_next, actionIntent(context, WidgetAction.Next))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun actionIntent(context: Context, action: WidgetAction): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, NowPlayingWidget::class.java).setAction(action.intentAction),
                FLAGS
            )
    }
}