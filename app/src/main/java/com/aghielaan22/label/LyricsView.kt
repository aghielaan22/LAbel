package com.aghielaan22.label

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * [lines] is null while loading, empty if no lyrics were found.
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        lines == null -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        lines.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No synced lyrics found for this song.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        else -> {
            val activeIndex = lines.indexOfLast { it.timeMs <= positionMs }
            val listState = rememberLazyListState()

            LaunchedEffect(activeIndex) {
                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            }

            LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    val active = index == activeIndex
                    val color = MaterialTheme.colorScheme.onSurface
                    Text(
                        text = line.text.ifEmpty { "♪" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) color else color.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeek(line.timeMs) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}