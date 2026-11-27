package com.aghielaan22.label

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun ArtworkStyleDialog(song: Song, onDismiss: () -> Unit) {
    val current = AppSettings.artworkStyle
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artwork style") },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                ArtworkStyle.entries.forEach { style ->
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = current == style,
                            role = Role.RadioButton,
                            onClick = { AppSettings.updateArtworkStyle(style) }
                        ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { NowPlayingArtwork(song, style, 54.dp, preview = true) },
                        headlineContent = { Text(style.label) },
                        supportingContent = { Text(style.description) },
                        trailingContent = { RadioButton(selected = current == style, onClick = null) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}