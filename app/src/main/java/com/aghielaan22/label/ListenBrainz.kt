package com.aghielaan22.label

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ListenBrainz {

    private const val BASE = "https://api.listenbrainz.org/1"
    private const val PREFS_NAME = "label_scrobble"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"
    private const val KEY_PENDING = "pending"
    private const val MAX_PENDING = 300

    private val client = OkHttpClient()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonType = "application/json".toMediaType()
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    var token by mutableStateOf("")
        private set

    var userName by mutableStateOf<String?>(null)
        private set

    var status by mutableStateOf("")
        private set

    val isConnected: Boolean get() = userName != null && token.isNotBlank()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY_ENABLED, false)
        token = p.getString(KEY_TOKEN, "") ?: ""
        userName = p.getString(KEY_USER, null)
        if (enabled && isConnected) ioScope.launch { flushPending() }
    }

    /** Checks a token with ListenBrainz. Returns an error message, or null on success. */
    suspend fun connect(newToken: String): String? = withContext(Dispatchers.IO) {
        val t = newToken.trim()
        if (t.isEmpty()) return@withContext "Paste your user token first."
        try {
            val request = Request.Builder()
                .url("$BASE/validate-token")
                .header("Authorization", "Token $t")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.optBoolean("valid", false)) {
                    token = t
                    userName = json.optString("user_name")
                    enabled = true
                    prefs?.edit()
                        ?.putString(KEY_TOKEN, t)
                        ?.putString(KEY_USER, userName)
                        ?.putBoolean(KEY_ENABLED, true)
                        ?.apply()
                    status = ""
                    null
                } else {
                    "That token wasn't accepted."
                }
            }
        } catch (e: Exception) {
            "Couldn't reach ListenBrainz. Check your connection."
        }
    }

    fun disconnect() {
        token = ""
        userName = null
        enabled = false
        status = ""
        prefs?.edit()?.clear()?.apply()
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    /** Tells ListenBrainz what's playing right now. */
    fun submitNowPlaying(song: Song) {
        if (!enabled || !isConnected) return
        ioScope.launch { post("playing_now", listOf(payload(song, null))) }
    }

    /** Records a finished listen. If we're offline, it waits and is sent later. */
    fun submitListen(song: Song, listenedAtSeconds: Long) {
        if (!enabled || !isConnected) return
        ioScope.launch {
            val item = payload(song, listenedAtSeconds)
            if (post("single", listOf(item))) {
                status = "Last sent: ${song.title}"
                flushPending()
            } else {
                addPending(item)
                status = "Offline, will send later"
            }
        }
    }

    // ---- Internals ----

    private fun payload(song: Song, listenedAt: Long?): JSONObject {
        val info = JSONObject().apply {
            put("media_player", "LAbel")
            put("submission_client", "LAbel")
            put("music_service", "youtube.com")
            put("origin_url", song.id)
        }
        val metadata = JSONObject().apply {
            put("artist_name", song.artist.removeSuffix(" - Topic"))
            put("track_name", song.title)
            put("additional_info", info)
        }
        return JSONObject().apply {
            if (listenedAt != null) put("listened_at", listenedAt)
            put("track_metadata", metadata)
        }
    }

    /** True if the request was handled (sent, or rejected for good). False means try again later. */
    private fun post(listenType: String, items: List<JSONObject>): Boolean {
        return try {
            val body = JSONObject().apply {
                put("listen_type", listenType)
                put("payload", JSONArray(items))
            }
            val request = Request.Builder()
                .url("$BASE/submit-listens")
                .header("Authorization", "Token $token")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> true
                    response.code == 429 || response.code >= 500 -> false
                    else -> {
                        status = "ListenBrainz refused a listen (code ${response.code})"
                        true // not worth retrying
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    private fun addPending(item: JSONObject) {
        val p = prefs ?: return
        val array = try {
            JSONArray(p.getString(KEY_PENDING, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        array.put(item)
        val trimmed = JSONArray()
        val start = maxOf(0, array.length() - MAX_PENDING)
        for (i in start until array.length()) trimmed.put(array.get(i))
        p.edit().putString(KEY_PENDING, trimmed.toString()).apply()
    }

    @Synchronized
    private fun flushPending() {
        val p = prefs ?: return
        val array = try {
            JSONArray(p.getString(KEY_PENDING, "[]"))
        } catch (e: Exception) {
            return
        }
        if (array.length() == 0) return

        val batch = (0 until minOf(array.length(), 100)).map { array.getJSONObject(it) }
        if (post("import", batch)) {
            val rest = JSONArray()
            for (i in batch.size until array.length()) rest.put(array.get(i))
            p.edit().putString(KEY_PENDING, rest.toString()).apply()
        }
    }
}