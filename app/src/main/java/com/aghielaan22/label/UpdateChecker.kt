package com.aghielaan22.label

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

data class AppRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?
)

object UpdateChecker {

    // Set these to your own GitHub repository
    private const val OWNER = "aghielaan22"
    private const val REPO = "LAbel"

    private const val PREFS_NAME = "label_updates"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SKIPPED = "skipped_tag"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    private val client = OkHttpClient()
    private var prefs: SharedPreferences? = null

    var autoCheck by mutableStateOf(true)
        private set

    var status by mutableStateOf("Not checked yet")
        private set

    var checking by mutableStateOf(false)
        private set

    /** A newer release that should be offered to the user, or null. */
    var dialogRelease by mutableStateOf<AppRelease?>(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        autoCheck = p.getBoolean(KEY_AUTO, true)
    }

    fun updateAutoCheck(enabled: Boolean) {
        autoCheck = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO, enabled)?.apply()
    }

    suspend fun check(context: Context, manual: Boolean) {
        if (checking) return
        if (OWNER.startsWith("<")) {
            status = "Set your GitHub username in UpdateChecker.kt first"
            return
        }
        val p = prefs
        val now = System.currentTimeMillis()
        if (!manual && p != null && now - p.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return

        checking = true
        status = "Checking..."
        try {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            p?.edit()?.putLong(KEY_LAST_CHECK, now)?.apply()

            if (release == null) {
                status = "No releases found yet"
                return
            }
            val installed = installedVersion(context)
            if (isNewer(release.tag, installed)) {
                status = "${release.tag} is available"
                val skipped = p?.getString(KEY_SKIPPED, null)
                if (manual || skipped != release.tag) dialogRelease = release
            } else {
                status = "You're up to date ($installed)"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Couldn't check for updates"
        } finally {
            checking = false
        }
    }

    fun dismissDialog(skipVersion: Boolean) {
        if (skipVersion) {
            dialogRelease?.let { prefs?.edit()?.putString(KEY_SKIPPED, it.tag)?.apply() }
        }
        dialogRelease = null
    }

    fun openDownload(context: Context, release: AppRelease) {
        val url = release.apkUrl ?: release.pageUrl
        // Only ever open GitHub addresses
        if (!url.startsWith("https://github.com/")) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

    private fun fetchLatest(): AppRelease? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=5")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LAbel-update-check")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: return null
            val array = JSONArray(body)

            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                if (o.optBoolean("draft", false)) continue
                val tag = o.optString("tag_name")
                if (tag.isEmpty()) continue

                var apk: String? = null
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.optString("name").endsWith(".apk")) {
                            apk = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                return AppRelease(
                    tag = tag,
                    title = o.optString("name").ifEmpty { tag },
                    notes = o.optString("body"),
                    pageUrl = o.optString("html_url"),
                    apkUrl = apk
                )
            }
            return null
        }
    }

    private fun parseVersion(text: String): List<Int>? {
        val match = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(text) ?: return null
        return listOf(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toIntOrNull() ?: 0
        )
    }

    private fun isNewer(remoteTag: String, installed: String): Boolean {
        val remote = parseVersion(remoteTag) ?: return false
        val local = parseVersion(installed) ?: return false
        for (i in 0 until 3) {
            if (remote[i] != local[i]) return remote[i] > local[i]
        }
        return false
    }
}

@Composable
fun UpdateDialog(release: AppRelease) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { UpdateChecker.dismissDialog(skipVersion = false) },
        title = { Text("Update available") },
        text = {
            Text("${release.title} (${release.tag}) is ready to download.\n\n${release.notes.take(500)}")
        },
        confirmButton = {
            TextButton(onClick = {
                UpdateChecker.openDownload(context, release)
                UpdateChecker.dismissDialog(skipVersion = false)
            }) { Text("Download") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { UpdateChecker.dismissDialog(skipVersion = true) }) {
                    Text("Skip this version")
                }
                TextButton(onClick = { UpdateChecker.dismissDialog(skipVersion = false) }) {
                    Text("Later")
                }
            }
        }
    )
}

/** The "Updates" card for the Settings screen. */
@Composable
fun UpdatesSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsGroup("Updates") {
        SwitchRow(
            title = "Check automatically",
            description = "Looks for a new LAbel version when you open the app, at most twice a day.",
            checked = UpdateChecker.autoCheck
        ) { UpdateChecker.updateAutoCheck(it) }

        ListItem(
            modifier = Modifier.clickable(enabled = !UpdateChecker.checking) {
                scope.launch { UpdateChecker.check(context, manual = true) }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Check for updates") },
            supportingContent = { Text(UpdateChecker.status) },
            trailingContent = {
                if (UpdateChecker.checking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        )
    }
}