package com.aghielaan22.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalSongColors = staticCompositionLocalOf<PlayerColors?> { null }

@Composable
fun rememberSongColors(song: Song?): PlayerColors? = key(song?.thumbnailUrl) {
    val context = LocalContext.current.applicationContext
    val url = song?.thumbnailUrl
    produceState<PlayerColors?>(null, url) {
        if (url != null) {
            value = try {
                withContext(Dispatchers.IO) { ArtworkColors.extract(context, url) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }.value
}