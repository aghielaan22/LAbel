package com.aghielaan22.label

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private fun albumSubtitle(album: Album): String =
    listOf("Album", album.artist).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
fun AlbumCard(album: Album) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { Navigator.open(DetailPage.AlbumPage(album)) }
    ) {
        AsyncImage(
            model = album.artworkUrl(512),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumRow(album: Album) {
    ListItem(
        modifier = Modifier.clickable { Navigator.open(DetailPage.AlbumPage(album)) },
        leadingContent = {
            AsyncImage(
                model = album.artworkUrl(256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        },
        headlineContent = { Text(album.title, maxLines = 1) },
        supportingContent = { Text(albumSubtitle(album), maxLines = 1) }
    )
}

@Composable
fun ArtistRow(artist: Artist) {
    ListItem(
        modifier = Modifier.clickable { Navigator.open(DetailPage.ArtistPage(artist)) },
        leadingContent = { ArtistAvatar(artist, 56.dp) },
        headlineContent = { Text(artist.name, maxLines = 1) },
        supportingContent = { Text("Artist") }
    )
}

@Composable
fun ArtistAvatar(artist: Artist, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (artist.thumbnailUrl != null) {
            AsyncImage(
                model = artist.artworkUrl(256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = artist.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}