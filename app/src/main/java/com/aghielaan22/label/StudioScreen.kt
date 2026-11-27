package com.aghielaan22.label

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(modifier: Modifier = Modifier) {
    var showServerSetup by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text("Studio", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Listen together, perfectly in sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StudioController.notice?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .glassPanel(RoundedCornerShape(16.dp))
                    .padding(12.dp)
            )
        }

        when {
            StudioController.active -> ActiveStudio()
            !StudioController.isConfigured || showServerSetup -> ServerSetup(
                onDone = { showServerSetup = false }
            )
            else -> StudioHome(onServerSettings = { showServerSetup = true })
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StudioHome(onServerSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf(StudioController.displayName) }
    var joinInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsGroup("Your name") {
        OutlinedTextField(
            value = nameInput,
            onValueChange = {
                nameInput = it.take(24)
                StudioController.updateName(it)
            },
            label = { Text("Shown to everyone in the studio") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    SettingsGroup("Start a studio") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "You'll be the host. You play, pause and skip, and everyone who joins hears it too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = StudioController.createStudio()
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(if (busy) "Starting..." else "Start a studio") }
        }
    }

    SettingsGroup("Join a studio") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Type the 6-character code, or paste the invite your friend shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = joinInput,
                onValueChange = { joinInput = it.take(300) },
                label = { Text("Code or invite") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Button(
                onClick = { error = StudioController.join(joinInput) },
                enabled = joinInput.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Join") }
        }
    }

    error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp)
        )
    }

    TextButton(onClick = onServerSettings, modifier = Modifier.padding(top = 8.dp)) {
        Text("Server settings")
    }
}

@Composable
private fun ServerSetup(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf(StudioController.serverUrl) }
    var keyInput by remember { mutableStateOf(StudioController.serverKey) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingsGroup("Studio server") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Studio needs a tiny free relay server that passes messages between phones " +
                        "(never any audio). You set it up once, and friends can join with a pasted " +
                        "invite without any setup of their own.\n\n" +
                        "1. Deploy the LAbel Studio relay on a free Cloudflare account.\n" +
                        "2. Paste its address below (it starts with https://).\n" +
                        "3. If you set a server key, paste that too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Server address") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Server key (if you set one)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            message?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        StudioController.updateServer(urlInput, keyInput)
                        val error = StudioController.testServer()
                        message = error ?: "Connected. Your server works."
                        busy = false
                        if (error == null) onDone()
                    }
                },
                enabled = !busy && urlInput.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp)
            ) { Text(if (busy) "Testing..." else "Save and test") }
        }
    }
}

@Composable
private fun ActiveStudio() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmEnd by remember { mutableStateOf(false) }
    val code = StudioController.code ?: return

    val statusText = when (StudioController.status) {
        StudioStatus.Connecting -> "Connecting..."
        StudioStatus.Reconnecting -> "Connection lost. Reconnecting..."
        StudioStatus.Connected -> when {
            StudioController.hostAway -> "Waiting for the host to come back…"
            PlayerController.isLoading -> "Buffering…"
            StudioController.isHost -> "You're the host"
            StudioController.resyncRequested -> "Resyncing with the host…"
            StudioController.guestPlaybackFailed -> "Playback interrupted. Tap Resync to retry."
            StudioController.waitingForSync -> "Waiting for the host's playback…"
            StudioController.guestFollowsHost && !StudioController.hostWantsPlayback -> "Synced · Host paused"
            StudioController.guestFollowsHost && PlayerController.isPlaying -> "Synced"
            StudioController.hostWantsPlayback -> "Catching up to the host…"
            else -> "Waiting for playback…"
        }
        StudioStatus.Idle -> ""
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp)
    )

    if (StudioController.isGuest) {
        TextButton(
            onClick = StudioController::resyncGuest,
            enabled = StudioController.status == StudioStatus.Connected &&
                    !StudioController.hostAway && !StudioController.resyncRequested
        ) {
            Text("Resync")
        }
    }

    // The code
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Studio code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${code.take(3)} ${code.drop(3)}",
            fontSize = 40.sp,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Text("Copy code")
            }
            Button(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, StudioController.inviteText())
                }
                context.startActivity(Intent.createChooser(intent, "Invite to your studio"))
            }) { Text("Share invite") }
        }
    }

    // What's playing
    PlayerController.currentSong?.let { song ->
        SettingsGroup("Now playing") {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Artwork(
                        url = song.artworkUrl(256),
                        fallbackUrl = song.thumbnailUrl,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(52.dp)
                    )
                },
                headlineContent = { Text(song.title, maxLines = 1) },
                supportingContent = { Text(song.artist, maxLines = 1) }
            )
        }
    }

    // Who's here
    SettingsGroup("In the studio (${StudioController.members.size})") {
        StudioController.members.forEach { member ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                headlineContent = {
                    Text(if (member.id == StudioController.myMemberId) "${member.name} (you)" else member.name)
                },
                trailingContent = {
                    if (member.host) {
                        Text(
                            text = "Host",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    }

    if (StudioController.isHost) {
        SettingsGroup("Host controls") {
            SwitchRow(
                title = "Let guests suggest songs",
                description = "Their \"Add to queue\" sends the song to your queue.",
                checked = StudioController.allowSuggestions
            ) { StudioController.updateAllowSuggestions(it) }
        }
        Button(
            onClick = { confirmEnd = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier.padding(top = 16.dp)
        ) { Text("End studio") }
        Text(
            text = "Play anything as usual and everyone hears it with you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    } else {
        Text(
            text = "The host controls play, pause and skipping. Use \"Add to queue\" on any song " +
                    if (StudioController.allowSuggestions) "to suggest it." else "(suggestions are off right now).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        OutlinedButton(
            onClick = { StudioController.leave() },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("Leave studio") }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this studio?") },
            text = { Text("Everyone will be disconnected, and the code stops working.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    StudioController.endStudio()
                }) { Text("End studio") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Cancel") }
            }
        )
    }
}