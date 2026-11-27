package com.aghielaan22.label

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LyricsView(
    lyrics: SongLyrics?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    canSeek: Boolean = true,
    retry: (() -> Unit)? = null,
    onShareLyrics: ((List<String>) -> Unit)? = null
) {
    when {
        retry != null -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load lyrics.", textAlign = TextAlign.Center)
                TextButton(onClick = retry) { Text("Retry") }
            }
        }

        lyrics == null -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        lyrics.synced.isNotEmpty() || lyrics.plain != null ->
            LyricsLines(lyrics, positionMs, canSeek, onSeek, onShareLyrics, modifier)

        else -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "No lyrics found for this song.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LyricsLines(
    lyrics: SongLyrics,
    positionMs: Long,
    canSeek: Boolean,
    onSeek: (Long) -> Unit,
    onShareLyrics: ((List<String>) -> Unit)?,
    modifier: Modifier
) {
    val timed = lyrics.synced.isNotEmpty()
    val lines = remember(lyrics) {
        if (timed) lyrics.synced.map { it.text } else lyrics.plain.orEmpty().lines()
    }
    val romanizedLines by produceState<Map<Int, String>>(emptyMap(), lines) {
        value = emptyMap()
        value = romanizeLyricLines(lines)
    }
    var showRomanization by rememberSaveable { mutableStateOf(false) }
    val canShare = onShareLyrics != null && remember(lines) { lines.any { it.isNotBlank() } }
    val active = if (timed) findActiveLine(lyrics.synced, positionMs) else -1
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    var selecting by remember(lyrics) { mutableStateOf(false) }
    var selected by remember(lyrics) { mutableStateOf<Set<Int>>(emptySet()) }

    val scroll = remember(timed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (timed && source == NestedScrollSource.UserInput && available.y != 0f) following = false
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(active, following, selecting, showRomanization) {
        if (timed && following && !selecting) {
            listState.animateScrollToItem((active - 2).coerceAtLeast(0))
        }
    }

    Column(modifier) {
        if (!timed) {
            Text("Unsynced lyrics", style = MaterialTheme.typography.labelMedium)
        }
        if (romanizedLines.isNotEmpty()) {
            FilterChip(
                selected = showRomanization,
                onClick = { showRomanization = !showRomanization },
                label = { Text("Romanization") }
            )
        }
        if (selecting) {
            Text("Select up to $MAX_CARD_LYRIC_LINES lines", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    selecting = false
                    selected = emptySet()
                }) { Text("Cancel") }
                TextButton(
                    onClick = {
                        onShareLyrics?.invoke(selected.sorted().map { lines[it] })
                        selecting = false
                        selected = emptySet()
                    },
                    enabled = selected.isNotEmpty()
                ) { Text("Preview (${selected.size})") }
            }
        } else if (canShare || (timed && !following)) {
            Column {
                if (canShare) TextButton(onClick = { selecting = true }) { Text("Share lyrics") }
                if (timed && !following) {
                    TextButton(onClick = { following = true }) { Text("Jump to current line") }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).nestedScroll(scroll),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val checked = index in selected
                val selectable = selecting && line.isNotBlank()
                val current = index == active
                val color = MaterialTheme.colorScheme.onSurface
                val interaction = when {
                    selectable -> Modifier.toggleable(
                        value = checked,
                        enabled = checked || selected.size < MAX_CARD_LYRIC_LINES,
                        role = Role.Checkbox,
                        onValueChange = { on ->
                            if (!on) selected = selected - index
                            else if (selected.size < MAX_CARD_LYRIC_LINES) selected = selected + index
                        }
                    )
                    !selecting && timed && canSeek -> Modifier.clickable { onSeek(lyrics.synced[index].timeMs) }
                    else -> Modifier
                }
                Row(
                    modifier = Modifier.fillMaxWidth().then(interaction).padding(vertical = if (timed) 8.dp else 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectable) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = checked || selected.size < MAX_CARD_LYRIC_LINES,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    Column {
                        Text(
                            text = line.ifBlank { if (timed) "♪" else " " },
                            style = if (timed) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = if (current || checked) FontWeight.Bold else FontWeight.Normal,
                            color = if (checked) MaterialTheme.colorScheme.primary
                            else if (current || !timed || selecting) color
                            else color.copy(alpha = 0.56f)
                        )
                        if (showRomanization) {
                            romanizedLines[index]?.let { reading ->
                                Text(
                                    text = reading,
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun findActiveLine(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
    }
    return low - 1
}