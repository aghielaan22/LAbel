package com.aghielaan22.label

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }

    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    LaunchedEffect(Unit) {
        started = true
        HomeRepository.requestLoad()
    }

    PullToRefreshBox(
        isRefreshing = HomeRepository.isLoading,
        onRefresh = { HomeRepository.requestLoad(force = true) },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(onClick = { Navigator.open(DetailPage.SettingsPage) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }

            if (HistoryRepository.recentSongs.isNotEmpty()) {
                item(key = "recent") {
                    SongShelf("Recently played", HistoryRepository.recentSongs.toList())
                }
            }

            items(HomeRepository.sections, key = { it.title }) { section ->
                SongShelf(section.title, section.songs, Modifier.animateItem())
            }

            if (HomeRepository.isLoading && HomeRepository.sections.isEmpty()) {
                items(2) { SkeletonShelf() }
            } else if (started && !HomeRepository.isLoading && HomeRepository.sections.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Couldn't load recommendations. Pull down to try again.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            HomeRepository.requestLoad(force = true)
                        }) {
                            Text("Try again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongShelf(title: String, songs: List<Song>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongCard(song) {
                    PlayerController.playQueue(songs, index)
                }
            }
        }
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var sheetOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(140.dp)
            .bouncyClickable(
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    sheetOpen = true
                },
                onClick = onClick
            )
    ) {
        Artwork(
            url = song.artworkUrl(512),
            fallbackUrl = song.thumbnailUrl,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (sheetOpen) {
        SongOptionsSheet(song = song, onDismiss = { sheetOpen = false })
    }
}

@Composable
private fun SkeletonShelf() {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 12.dp)
                .size(width = 160.dp, height = 22.dp)
                .shimmer(RoundedCornerShape(6.dp))
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Column(modifier = Modifier.width(140.dp)) {
                    Box(modifier = Modifier.size(140.dp).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 14.dp)
                            .shimmer(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 70.dp, height = 12.dp)
                            .shimmer(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}