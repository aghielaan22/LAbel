package com.aghielaan22.label

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LAbel closed unexpectedly") },
        text = {
            Text(
                "Something went wrong the last time LAbel was open. " +
                        "A report was saved. Sharing it helps find and fix the problem."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "LAbel crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(intent, "Share crash report"))
                onDismiss()
            }) { Text("Share report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}