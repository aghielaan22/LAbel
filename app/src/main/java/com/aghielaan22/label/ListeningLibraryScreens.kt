package com.aghielaan22.label

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class SmartPlaylist(val title: String, val subtitle: String, val emptyMessage: String) {
    Forgotten(
        "Forgotten Favorites",
        "Liked tracks whose last counted play was 90+ days ago",
        "No liked tracks have a known last play at least 90 days ago."
    ),
    HeavyRotation(
        "Heavy Rotation",
        "Your most-played tracks this calendar month",
        "Play some music this month to start filling this playlist."
    )
}

@Composable
internal fun SmartPlaylistScreen(rule: SmartPlaylist, modifier: Modifier, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val liked = if (rule == SmartPlaylist.Forgotten) LibraryRepository.likedSongs.toList() else emptyList()
    val lastPlayed = if (rule == SmartPlaylist.Forgotten) StatsRepository.lastPlayedDates() else emptyMap()
    var retry by remember(rule) { mutableIntStateOf(0) }
    val result = rememberListeningResult(Triple(rule, liked, lastPlayed), retry) {
        when (rule) {
            SmartPlaylist.Forgotten -> ListeningHistory.forgottenFavorites(liked, lastPlayed)
            SmartPlaylist.HeavyRotation -> ListeningHistory.heavyRotation()
        }
    }

    Column(modifier.fillMaxSize()) {
        PageHeader(rule.title, onBack)
        Text(
            rule.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val current = result
        when {
            current == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            current.isFailure -> ListeningLoadError { retry++ }
            else -> LibraryCollection(
                collectionId = "smart:${rule.name}",
                songs = current.getOrThrow(),
                emptyMessage = rule.emptyMessage,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun YearReviewScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var yearMenuOpen by remember { mutableStateOf(false) }
    val result = rememberListeningResult(selectedYear, retry) { ListeningHistory.yearReview(selectedYear) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Year in review", onBack)
        val current = result
        when {
            current == null -> CircularProgressIndicator(Modifier.padding(16.dp))
            current.isFailure -> ListeningLoadError { retry++ }
            else -> {
                val review = current.getOrThrow()
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(26.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (review.availableYears.first < review.availableYears.last) {
                            Box {
                                TextButton(onClick = { yearMenuOpen = true }) {
                                    Text("Year · ${review.year}", style = MaterialTheme.typography.titleLarge)
                                }
                                DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                                    review.availableYears.reversed().forEach { year ->
                                        DropdownMenuItem(text = { Text(year.toString()) }, onClick = {
                                            selectedYear = year
                                            yearMenuOpen = false
                                        })
                                    }
                                }
                            }
                        } else {
                            Text(review.year.toString(), style = MaterialTheme.typography.headlineLarge)
                        }
                        Text("Recorded ${review.coverage}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReviewMetric(review.plays.toString(), "Plays", Modifier.weight(1f))
                            ReviewMetric(review.trackCount.toString(), "Tracks", Modifier.weight(1f))
                            ReviewMetric(review.artistCount.toString(), "Artists", Modifier.weight(1f))
                        }
                    }

                    if (review.plays == 0L) {
                        Text("Your recap starts to fill after your first counted play.")
                    } else {
                        review.topTrack?.let {
                            Text("Top track", style = MaterialTheme.typography.labelLarge)
                            Text(it.song.title, style = MaterialTheme.typography.titleLarge)
                            Text("${it.song.artist} · ${it.plays} ${if (it.plays == 1L) "play" else "plays"}")
                        }
                        review.topArtist?.let {
                            Text("Top artist", style = MaterialTheme.typography.labelLarge)
                            Text(it, style = MaterialTheme.typography.titleLarge)
                        }

                        Button(enabled = !sharing, onClick = {
                            sharing = true
                            shareError = false
                            scope.launch {
                                try {
                                    val intent = yearReviewCardIntent(context.applicationContext, review)
                                    context.startActivity(Intent.createChooser(intent, "Share year in review"))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    shareError = true
                                } finally {
                                    sharing = false
                                }
                            }
                        }) {
                            Text(if (sharing) "Preparing…" else "Share recap card")
                        }
                        if (shareError) Text("Couldn't share the card. Try again.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ListeningLoadError(onRetry: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Couldn't load listening history.")
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun <T> rememberListeningResult(
    key: Any?,
    retry: Int,
    load: suspend () -> T
): Result<T>? {
    return produceState<Result<T>?>(null, key, retry) {
        if (value?.isFailure == true) value = null
        ListeningHistory.revision.collect {
            value = try {
                Result.success(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }.value
}