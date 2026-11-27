package com.aghielaan22.label

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private fun formatListenTime(ms: Long): String {
    val hours = ms / 3_600_000L
    val minutes = (ms / 60_000L) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "$minutes min"
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    var showReview by remember { mutableStateOf(false) }
    if (showReview) {
        YearReviewScreen(onBack = { showReview = false })
        return
    }
    BackHandler(onBack = onBack)
    var confirmReset by remember { mutableStateOf(false) }

    val totalMs = StatsRepository.totalListenedMs()
    val totalPlays = StatsRepository.totalPlays()
    val artistCount = StatsRepository.distinctArtists()
    val artists = StatsRepository.topArtists(5)
    val songs = StatsRepository.topSongs(10)

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = "Your stats", onBack = onBack)
        TextButton(
            onClick = { showReview = true },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { Text("Year in review") }

        if (totalPlays == 0) {
            Text(
                text = "Nothing here yet. A song counts once you've heard about half of it, " +
                        "so play some music and come back.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassPanel(RoundedCornerShape(24.dp))
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBlock(formatListenTime(totalMs), "listened")
                        StatBlock("$totalPlays", "plays")
                        StatBlock("$artistCount", "artists")
                    }
                }

                item {
                    Text(
                        text = "Top artists",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                    )
                }
                item { ArtistBars(artists) }

                item {
                    Text(
                        text = "Top songs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                    )
                }
                itemsIndexed(songs, key = { _, stat -> stat.song.id }) { index, stat ->
                    ListItem(
                        modifier = Modifier.clickable {
                            PlayerController.playQueue(songs.map { it.song }, index)
                        },
                        leadingContent = {
                            AsyncImage(
                                model = stat.song.artworkUrl(256),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        },
                        headlineContent = { Text(stat.song.title, maxLines = 1) },
                        supportingContent = {
                            Text(
                                "${stat.song.artist} · ${stat.plays} ${if (stat.plays == 1) "play" else "plays"}",
                                maxLines = 1
                            )
                        }
                    )
                }

                item {
                    TextButton(onClick = { confirmReset = true }) { Text("Reset stats") }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset your stats?") },
            text = { Text("Your play counts, listening time and dated history will be cleared. Your playlists and liked songs will stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    StatsRepository.reset()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistBars(artists: List<ArtistStat>) {
    val most = artists.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(24.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        artists.forEachIndexed { index, artist ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        Navigator.open(
                            DetailPage.ArtistPage(Artist(artist.name, artist.thumbnailUrl))
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((artist.plays / most.toFloat()).coerceIn(0.05f, 1f))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = artist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${artist.plays} plays",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}