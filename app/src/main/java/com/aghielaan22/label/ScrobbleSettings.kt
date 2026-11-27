package com.aghielaan22.label

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ScrobbleSettings() {
    val scope = rememberCoroutineScope()
    var tokenInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    SettingsGroup("Scrobbling") {
        if (ListenBrainz.isConnected) {
            ListItem(
                colors = transparent,
                headlineContent = { Text("Connected to ListenBrainz") },
                supportingContent = { Text("Signed in as ${ListenBrainz.userName}") }
            )
            SwitchRow(
                title = "Send my listens",
                description = "A song is sent once you've heard about half of it. " +
                        "LAbel also shares what's playing right now.",
                checked = ListenBrainz.enabled
            ) { ListenBrainz.updateEnabled(it) }
            if (ListenBrainz.status.isNotEmpty()) {
                ListItem(
                    colors = transparent,
                    headlineContent = { Text(ListenBrainz.status) }
                )
            }
            TextButton(
                onClick = { ListenBrainz.disconnect() },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text("Disconnect") }
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "ListenBrainz is a free, open service that keeps your listening history. " +
                            "Create an account, then copy your user token from its settings page " +
                            "(listenbrainz.org/settings) and paste it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("User token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = ListenBrainz.connect(tokenInput)
                            busy = false
                            if (message == null) tokenInput = ""
                        }
                    },
                    enabled = !busy && tokenInput.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text(if (busy) "Checking..." else "Connect") }
            }
        }
    }
}