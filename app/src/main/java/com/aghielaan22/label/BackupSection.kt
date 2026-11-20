package com.aghielaan22.label

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSection() {
    val context = LocalContext.current
    var pendingBackup by remember { mutableStateOf<ParsedBackup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Couldn't open the file")
                stream.use { it.write(BackupManager.createBackupJson().toByteArray(Charsets.UTF_8)) }
                "Backup saved"
            } catch (e: Exception) {
                "Couldn't save the backup"
            }
            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: throw IOException("Couldn't open the file")
                pendingBackup = BackupManager.parse(text)
            } catch (e: IllegalArgumentException) {
                message = e.message ?: "This isn't a LAbel backup file."
            } catch (e: Exception) {
                message = "Couldn't read that file."
            }
        }
    }

    SectionHeader("Backup")
    ListItem(
        modifier = Modifier.clickable {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            exportLauncher.launch("label-backup-$date.json")
        },
        headlineContent = { Text("Export library") },
        supportingContent = { Text("Save liked songs, playlists and settings to a file") }
    )
    ListItem(
        modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
        headlineContent = { Text("Restore from backup") },
        supportingContent = { Text("Merge a backup file into your library") }
    )

    pendingBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Found ${backup.liked.size} liked songs and ${backup.playlists.size} playlists. " +
                            "They'll be merged into your library, and nothing will be deleted. " +
                            "Settings saved in the backup will be applied too."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = BackupManager.restore(backup)
                    pendingBackup = null
                    message = if (result.likedAdded == 0 && result.playlistsChanged == 0) {
                        "Nothing new to restore. Your library already has everything in this backup."
                    } else {
                        "Restored: ${result.likedAdded} liked songs added and " +
                                "${result.playlistsChanged} playlists added or updated."
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackup = null }) { Text("Cancel") }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("OK") }
            }
        )
    }
}