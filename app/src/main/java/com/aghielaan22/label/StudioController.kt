package com.aghielaan22.label

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class StudioStatus { Idle, Connecting, Connected, Reconnecting }

data class StudioMember(val id: String, val name: String, val host: Boolean)

private data class HostState(
    val index: Int,
    val playing: Boolean,
    val position: Long,
    val serverTime: Long,
    val repeat: Int,
    val queueHash: Int
)

object StudioController {

    private const val PREFS_NAME = "label_studio"
    private const val KEY_URL = "server_url"
    private const val KEY_SERVER_KEY = "server_key"
    private const val KEY_NAME = "display_name"
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var prefs: SharedPreferences? = null

    private var socket: WebSocket? = null
    private var hostToken: String? = null
    private var intentionalClose = false
    private var retry = 0
    private var loopJob: Job? = null
    private var reconnectJob: Job? = null
    private var resyncJob: Job? = null
    private var lastStateAtMs: Long? = null
    private var sendJob: Job? = null
    private var noticeJob: Job? = null
    private var lastSentHash: Int? = null
    private var lastHost: HostState? = null
    private var clockOffset = 0L
    private val clockSamples = mutableListOf<Pair<Long, Long>>()

    var serverUrl by mutableStateOf("")
        private set

    var serverKey by mutableStateOf("")
        private set

    var displayName by mutableStateOf("")
        private set

    var status by mutableStateOf(StudioStatus.Idle)
        private set

    var code by mutableStateOf<String?>(null)
        private set

    var isHost by mutableStateOf(false)
        private set

    var members by mutableStateOf<List<StudioMember>>(emptyList())
        private set

    var myMemberId by mutableStateOf<String?>(null)
        private set

    var allowSuggestions by mutableStateOf(true)
        private set

    var hostAway by mutableStateOf(false)
        private set

    var waitingForSync by mutableStateOf(false)
        private set
    var resyncRequested by mutableStateOf(false)
        private set
    var guestFollowsHost by mutableStateOf(false)
        private set
    var guestPlaybackFailed by mutableStateOf(false)
        private set
    var hostWantsPlayback by mutableStateOf(false)
        private set

    /** A short message for the Studio screen ("Sam added ..."). */
    var notice by mutableStateOf<String?>(null)
        private set

    val active: Boolean get() = code != null
    val isGuest: Boolean get() = active && !isHost
    val isConfigured: Boolean get() = baseUrl() != null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        serverUrl = p.getString(KEY_URL, "") ?: ""
        serverKey = p.getString(KEY_SERVER_KEY, "") ?: ""
        displayName = p.getString(KEY_NAME, null) ?: "Guest-${(1000..9999).random()}".also {
            p.edit().putString(KEY_NAME, it).apply()
        }
    }

    // ---- Settings ----

    fun updateServer(url: String, key: String) {
        serverUrl = url.trim()
        serverKey = key.trim()
        prefs?.edit()?.putString(KEY_URL, serverUrl)?.putString(KEY_SERVER_KEY, serverKey)?.apply()
    }

    fun updateName(name: String) {
        displayName = name.trim().take(24).ifBlank { "Guest" }
        prefs?.edit()?.putString(KEY_NAME, displayName)?.apply()
    }

    /** Checks the server address and key. Returns an error message, or null if it works. */
    suspend fun testServer(): String? = withContext(Dispatchers.IO) {
        val base = baseUrl()
            ?: return@withContext "Enter your server address. It starts with https://"
        try {
            val builder = Request.Builder().url("$base/health")
            if (serverKey.isNotBlank()) builder.header("X-Studio-Key", serverKey)
            client.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 401 -> "The server key doesn't match."
                    response.isSuccessful -> null
                    else -> "The server answered with code ${response.code}."
                }
            }
        } catch (e: Exception) {
            "Couldn't reach that address."
        }
    }

    // ---- Starting, joining, leaving ----

    /** Creates a studio and becomes its host. Returns an error message, or null on success. */
    suspend fun createStudio(): String? = withContext(Dispatchers.IO) {
        val base = baseUrl() ?: return@withContext "Set up your studio server first."
        try {
            val builder = Request.Builder().url("$base/create").post("".toRequestBody())
            if (serverKey.isNotBlank()) builder.header("X-Studio-Key", serverKey)
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 401) return@withContext "The server key doesn't match."
                if (!response.isSuccessful) {
                    return@withContext "The server couldn't start a studio (code ${response.code})."
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val newCode = json.getString("code")
                val token = json.getString("hostToken")
                withContext(Dispatchers.Main) { startSession(newCode, token) }
                null
            }
        } catch (e: Exception) {
            "Couldn't reach your studio server."
        }
    }

    /** Joins with a plain code, or with a pasted invite. Returns an error message, or null. */
    fun join(input: String): String? {
        val text = input.trim()
        var raw = text

        if (text.contains("://")) {
            val uri = Uri.parse(text)
            raw = uri.pathSegments.firstOrNull().orEmpty()
            uri.getQueryParameter("s")?.let { server ->
                updateServer("https://$server", uri.getQueryParameter("k").orEmpty())
            }
        }

        val clean = raw.uppercase().filter { it in CODE_ALPHABET }
        if (clean.length != 6) return "A studio code has 6 letters and numbers."
        if (baseUrl() == null) return "Set up your studio server first, or paste a full invite."
        startSession(clean, null)
        return null
    }

    fun leave() = finish(null)

    fun endStudio() {
        if (isHost) socket?.send("""{"t":"end"}""")
        finish("Your studio has ended.")
    }

    fun updateAllowSuggestions(value: Boolean) {
        allowSuggestions = value
        hostChanged()
    }

    /** What the Share button sends. Pasting it into Join sets everything up. */
    fun inviteText(): String {
        val studioCode = code ?: return ""
        val host = baseUrl()?.let { Uri.parse(it).host }.orEmpty()
        val link = buildString {
            append("label://studio/$studioCode?s=${Uri.encode(host)}")
            if (serverKey.isNotBlank()) append("&k=${Uri.encode(serverKey)}")
        }
        return "Join my LAbel studio and listen together!\n\n" +
                "Open LAbel, tap Studio, then Join, and paste this:\n$link\n\nCode: $studioCode"
    }

    // ---- Called by the player ----

    /** The host's player changed (play, pause, seek, new song, queue). */
    fun hostChanged() {
        if (!isHost || status != StudioStatus.Connected) return
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(150)
            sendState(forceFull = false)
        }
    }

    /** A guest's player finished buffering: line up with the host right away. */
    fun onPlayerReady() {
        if (!isGuest || hostAway || status != StudioStatus.Connected) return
        guestPlaybackFailed = false
        if (!waitingForSync) lastHost?.takeIf { hasFreshHostState() }?.let { follow(it) }
    }

    internal fun onGuestPlaybackError() {
        if (isGuest) guestPlaybackFailed = true
    }

    fun resyncGuest() {
        if (!isGuest || status != StudioStatus.Connected || hostAway || resyncRequested) return
        if (socket?.send("""{"t":"need_state"}""") != true) {
            showNotice("Couldn't request a sync. Try again once connected.")
            return
        }
        resyncRequested = true
        waitingForSync = true
        resyncJob?.cancel()
        resyncJob = scope.launch {
            delay(8_000)
            resyncRequested = false
            waitingForSync = true
            showNotice("The host hasn't answered the sync request yet.")
        }
    }

    private fun hasFreshHostState(): Boolean {
        val received = lastStateAtMs ?: return false
        return SystemClock.elapsedRealtime() - received <= 12_000L
    }

    /** A guest suggests a song to the host's queue. */
    fun suggestSong(song: Song) {
        if (!isGuest) return
        if (!allowSuggestions) {
            showNotice("The host isn't taking suggestions right now.")
            return
        }
        val songJson = listOf(song).toJsonArray().getJSONObject(0)
        socket?.send(JSONObject().put("t", "suggest").put("song", songJson).toString())
        showNotice("Suggested \"${song.title}\" to the host")
    }

    fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(4000)
            notice = null
        }
    }

    // ---- Connection ----

    private fun baseUrl(): String? {
        val raw = serverUrl.trim().trimEnd('/')
        if (raw.isEmpty()) return null
        val full = if (raw.startsWith("http")) raw else "https://$raw"
        return if (full.startsWith("https://")) full else null
    }

    private fun startSession(newCode: String, token: String?) {
        finish(null)
        TrackLoops.stop()
        code = newCode
        hostToken = token
        isHost = token != null
        members = emptyList()
        hostAway = false
        notice = null
        intentionalClose = false
        retry = 0
        lastSentHash = null
        PlayerController.forceShuffleOff()
        PlayerController.setControlsLocked(!isHost)
        connect()
    }

    private fun connect() {
        val studioCode = code ?: return
        val base = baseUrl() ?: run {
            finish("Set up your studio server first.")
            return
        }
        status = if (retry == 0) StudioStatus.Connecting else StudioStatus.Reconnecting

        var url = base.replaceFirst("https://", "wss://") +
                "/ws/$studioCode?name=" + Uri.encode(displayName)
        hostToken?.let { url += "&token=" + Uri.encode(it) }

        val builder = Request.Builder().url(url)
        if (serverKey.isNotBlank()) builder.header("X-Studio-Key", serverKey)

        socket = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    if (webSocket !== socket) return@launch
                    retry = 0
                    status = StudioStatus.Connected
                    waitingForSync = isGuest
                    startLoop()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { if (webSocket === socket) handleMessage(text) }
            }

            override fun onClosed(webSocket: WebSocket, closeCode: Int, reason: String) {
                scope.launch { disconnected(webSocket, null) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { disconnected(webSocket, response?.code) }
            }
        })
    }

    private fun disconnected(ws: WebSocket, httpCode: Int?) {
        if (ws !== socket || intentionalClose) return
        if (httpCode == 404) {
            finish("No studio with that code. It may have ended.")
            return
        }
        if (httpCode == 401) {
            finish("The server key doesn't match.")
            return
        }
        socket = null
        status = StudioStatus.Reconnecting
        loopJob?.cancel()
        resyncJob?.cancel()
        resyncRequested = false
        waitingForSync = isGuest
        guestFollowsHost = false
        lastHost = null
        lastStateAtMs = null
        retry++
        val wait = minOf(1000L shl minOf(retry, 4), 15_000L)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(wait)
            if (code != null && !intentionalClose && status == StudioStatus.Reconnecting) connect()
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            var tick = 0
            while (true) {
                if (tick % 8 == 0) {
                    socket?.send("""{"t":"ping","c":${System.currentTimeMillis()}}""")
                }
                if (isHost && tick > 0 && tick % 3 == 0) sendState(forceFull = false)
                if (isGuest && !hasFreshHostState()) guestFollowsHost = false
                delay(1000)
                tick++
            }
        }
    }

    private fun finish(message: String?) {
        intentionalClose = true
        loopJob?.cancel()
        sendJob?.cancel()
        reconnectJob?.cancel()
        resyncJob?.cancel()
        socket?.close(1000, "bye")
        socket = null
        code = null
        hostToken = null
        isHost = false
        members = emptyList()
        myMemberId = null
        hostAway = false
        status = StudioStatus.Idle
        lastHost = null
        lastStateAtMs = null
        lastSentHash = null
        waitingForSync = false
        resyncRequested = false
        guestFollowsHost = false
        guestPlaybackFailed = false
        hostWantsPlayback = false
        clockSamples.clear()
        clockOffset = 0L
        PlayerController.setControlsLocked(false)
        if (message != null) showNotice(message)
    }

    // ---- Messages ----

    private fun handleMessage(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        try {
            when (msg.optString("t")) {
                "hello" -> {
                    myMemberId = msg.optString("you")
                    members = parseMembers(msg.optJSONArray("members"))
                    hostAway = false
                    if (isHost) sendState(forceFull = true)
                }
                "members" -> members = parseMembers(msg.optJSONArray("list"))
                "pong" -> handlePong(msg)
                "host_away" -> {
                    hostAway = true
                    guestFollowsHost = false
                    resyncJob?.cancel()
                    resyncRequested = false
                    waitingForSync = true
                    lastHost = null
                    lastStateAtMs = null
                }
                "host_back" -> {
                    hostAway = false
                    if (isGuest) resyncGuest()
                }
                "ended" -> finish("The studio has ended.")
                "need_state" -> if (isHost) sendState(forceFull = true)
                "suggest" -> if (isHost) handleSuggestion(msg)
                "state", "beat" -> if (isGuest) handleHostState(msg)
            }
        } catch (e: Exception) {
            // Ignore a message we can't understand
        }
    }

    private fun parseMembers(array: JSONArray?): List<StudioMember> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            StudioMember(o.getString("id"), o.optString("name", "Guest"), o.optBoolean("host"))
        }.sortedByDescending { it.host }
    }

    private fun handlePong(msg: JSONObject) {
        val sent = msg.optLong("c")
        val serverTime = msg.optLong("s")
        val rtt = System.currentTimeMillis() - sent
        if (rtt !in 0..5000) return
        // Our clock is off by roughly (server time) - (our time at the midpoint of the round trip)
        clockSamples.add(rtt to (serverTime - (sent + rtt / 2)))
        if (clockSamples.size > 5) clockSamples.removeAt(0)
        // The sample with the shortest trip is the most trustworthy
        clockOffset = clockSamples.minByOrNull { it.first }!!.second
    }

    // ---- Host: sending the state ----

    private fun sendState(forceFull: Boolean) {
        val ws = socket ?: return
        val hash = PlayerController.queueHash()
        val full = forceFull || hash != lastSentHash
        val json = JSONObject().apply {
            put("t", if (full) "state" else "beat")
            put("index", PlayerController.currentIndex)
            put("playing", PlayerController.isPlaying)
            put("pos", PlayerController.position())
            put("repeat", PlayerController.repeatMode)
            put("qh", hash)
            put("allow", allowSuggestions)
            if (full) {
                put("queue", PlayerController.queueSongs.toJsonArray())
                lastSentHash = hash
            }
        }
        ws.send(json.toString())
    }

    private fun handleSuggestion(msg: JSONObject) {
        if (!allowSuggestions) return
        val songJson = msg.optJSONObject("song") ?: return
        val song = parseSongs(JSONArray().put(songJson)).firstOrNull() ?: return
        if (!isYouTube(song.id)) return
        PlayerController.addToQueue(song)
        showNotice("${msg.optString("from", "Someone")} added \"${song.title}\"")
    }

    // ---- Guest: following the host ----

    private fun handleHostState(msg: JSONObject) {
        allowSuggestions = msg.optBoolean("allow", true)
        hostAway = false
        val state = HostState(
            index = msg.optInt("index"),
            playing = msg.optBoolean("playing"),
            position = msg.optLong("pos").coerceAtLeast(0),
            serverTime = msg.optLong("s"),
            repeat = msg.optInt("repeat").coerceIn(0, 2),
            queueHash = msg.optInt("qh")
        )
        val queueJson = msg.optJSONArray("queue")
        if (queueJson == null && resyncRequested) return

        val songs = if (queueJson != null) parseSongs(queueJson).filter { isYouTube(it.id) } else null
        if (songs == null && state.queueHash != PlayerController.queueHash()) {
            waitingForSync = true
            guestFollowsHost = false
            resyncGuest()
            return
        }

        val force = resyncRequested
        if (songs != null) {
            resyncJob?.cancel()
            resyncRequested = false
        }
        waitingForSync = false
        lastHost = state
        lastStateAtMs = SystemClock.elapsedRealtime()
        hostWantsPlayback = state.playing

        if (songs != null && songs.isEmpty()) {
            PlayerController.remoteClearQueue()
            guestFollowsHost = false
            guestPlaybackFailed = false
            return
        }
        if (songs != null && songs.map { it.id } != PlayerController.queueSongs.map { it.id }) {
            guestPlaybackFailed = false
            guestFollowsHost = false
            PlayerController.remotePlayQueue(songs, state.index, targetPosition(state), state.playing)
            PlayerController.remoteSetRepeat(state.repeat)
        } else {
            follow(state, force = force)
        }
    }

    private fun follow(state: HostState, force: Boolean = false) {
        val index = state.index
        if (index !in PlayerController.queueSongs.indices) {
            guestFollowsHost = false
            return
        }

        val target = targetPosition(state)
        val sameTrack = PlayerController.currentIndex == index
        val drift = abs(PlayerController.position() - target)
        val tolerance = if (state.playing) 1500L else 700L
        val seek = !sameTrack ||
                ((force || !PlayerController.isLoading) && drift > (if (force) 180L else tolerance))
        if (guestPlaybackFailed && !force && sameTrack) return
        if (force || !sameTrack) guestPlaybackFailed = false

        val aligned = PlayerController.remoteAlign(
            index, target, state.playing, state.repeat, seek,
            recover = force || !sameTrack
        )
        guestFollowsHost = aligned && PlayerController.currentIndex == index &&
                abs(PlayerController.position() - target) <= tolerance
    }

    /** Where the host is right now: their position, plus the time that has passed since. */
    private fun targetPosition(state: HostState): Long {
        if (!state.playing || state.serverTime <= 0L) return state.position
        val elapsed = (System.currentTimeMillis() + clockOffset - state.serverTime).coerceIn(0L, 10_000L)
        return state.position + elapsed
    }

    private fun isYouTube(url: String): Boolean =
        url.startsWith("https://www.youtube.com/") ||
                url.startsWith("https://music.youtube.com/") ||
                url.startsWith("https://youtube.com/")
}