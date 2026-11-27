package com.aghielaan22.label

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.TreeSet
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val audioCacheLimitsMiB = listOf(128, 256, 512, 1024)
private const val BYTES_PER_MIB = 1024L * 1024

internal data class CachedAudioTrack(val url: String, val quality: AudioQuality)
internal data class AudioCacheUsage(val available: Boolean, val bytes: Long)

@OptIn(UnstableApi::class)
internal object PlaybackAudioCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = CountDownLatch(1)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val usageState = MutableStateFlow<AudioCacheUsage?>(null)
    private val offlineState = MutableStateFlow<Set<CachedAudioTrack>>(emptySet())
    val usage = usageState.asStateFlow()
    val offlineTracks = offlineState.asStateFlow()
    private var started = false
    private var cache: SimpleCache? = null
    private var evictor: AudioCacheEvictor? = null

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val limit = AppSettings.audioCacheLimitMiB * BYTES_PER_MIB
        scope.launch {
            var database: StandaloneDatabaseProvider? = null
            var opened: SimpleCache? = null
            try {
                val provider = StandaloneDatabaseProvider(app)
                database = provider
                val policy = AudioCacheEvictor(limit) { changes.trySend(Unit) }
                val disk = SimpleCache(File(app.cacheDir, "playback"), policy, provider)
                opened = disk
                disk.checkInitialization()
                cache = disk
                evictor = policy
                publish(disk)
            } catch (e: Exception) {
                Log.w("PlaybackAudioCache", "Could not open audio cache", e)
                cache = null
                evictor = null
                usageState.value = AudioCacheUsage(false, 0)
                runCatching { opened?.release() }
                runCatching { database?.close() }
            } finally {
                initialized.countDown()
            }

            val disk = cache ?: return@launch
            for (event in changes) {
                delay(650)
                publish(disk)
            }
        }
    }

    private fun publish(disk: SimpleCache) {
        synchronized(disk) {
            usageState.value = AudioCacheUsage(true, disk.cacheSpace)
            offlineState.value = disk.keys.mapNotNull { key ->
                key.track()?.takeIf { isComplete(disk, key) }
            }.toSet()
        }
    }

    suspend fun setLimit(limitMiB: Int): Boolean = withContext(Dispatchers.IO) {
        require(limitMiB in audioCacheLimitsMiB)
        initialized.await()
        val disk = cache ?: return@withContext false
        val policy = evictor ?: return@withContext false
        synchronized(disk) {
            policy.limit = limitMiB * BYTES_PER_MIB
            policy.trim(disk, 0)
        }
        publish(disk)
        true
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        initialized.await()
        val disk = cache ?: return@withContext false
        synchronized(disk) {
            disk.keys.toList().forEach { disk.removeResource(it) }
        }
        publish(disk)
        true
    }

    fun dataSource(http: DataSource.Factory, quality: AudioQuality): DataSource {
        return PlaybackDataSource { request ->
            val host = request.uri.host.orEmpty()
            if (host != "youtube.com" && !host.endsWith(".youtube.com")) {
                return@PlaybackDataSource http.createDataSource() to request
            }

            // This runs on Media3's loader thread. Start-up indexing never blocks the UI.
            initialized.await()
            val disk = cache
            val watchUrl = request.uri.toString()
            val track = CachedAudioTrack(watchUrl, quality)
            val complete = disk?.let {
                synchronized(it) {
                    it.keys.filter { key -> key.track() == track && isComplete(it, key) }
                        .maxByOrNull { key -> it.getCachedSpans(key).maxOfOrNull { span -> span.lastTouchTimestamp } ?: 0L }
                }
            }
            if (disk != null && complete != null) {
                val reader = CacheDataSource.Factory().setCache(disk).setCacheWriteDataSinkFactory(null)
                return@PlaybackDataSource reader.createDataSource() to
                        request.buildUpon().setKey(complete).build()
            }

            val uri = try {
                Uri.parse(MusicRepository.getAudioUrl(watchUrl, quality))
            } catch (e: Exception) {
                throw IOException("Could not resolve audio stream", e)
            }
            val oldKey = StreamCachePolicy.mediaKey(
                watchUrl, quality.name, uri.getQueryParameter("itag"),
                uri.getQueryParameter("lmt"), uri.toString()
            )
            val key = if (oldKey == uri.toString()) {
                val digest = MessageDigest.getInstance("SHA-256").digest(oldKey.toByteArray(Charsets.UTF_8))
                "$watchUrl|${quality.name}|url:" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
            } else oldKey
            val source = if (disk == null) http.createDataSource() else {
                CacheDataSource.Factory().setCache(disk).setUpstreamDataSourceFactory(http)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
            }
            source to request.buildUpon().setUri(uri).setKey(key).build()
        }
    }

    fun hasFullTrack(url: String, quality: AudioQuality): Boolean =
        CachedAudioTrack(url, quality) in offlineState.value

    private fun isComplete(disk: SimpleCache, key: String): Boolean {
        val length = ContentMetadata.getContentLength(disk.getContentMetadata(key))
        return length > 0 && disk.isCached(key, 0, length)
    }

    private fun String.track(): CachedAudioTrack? {
        val parts = split('|')
        val suffixSize = if (parts.last().startsWith("url:")) 2 else 3
        val qualityIndex = parts.size - suffixSize
        if (qualityIndex < 1) return null
        val quality = AudioQuality.entries.firstOrNull { it.name == parts[qualityIndex] } ?: return null
        return CachedAudioTrack(parts.take(qualityIndex).joinToString("|"), quality)
    }
}

/** Defers network resolution and cache lookup until ExoPlayer opens the stream. */
@OptIn(UnstableApi::class)
private class PlaybackDataSource(
    private val resolve: (DataSpec) -> Pair<DataSource, DataSpec>
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var source: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        source?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val (delegate, resolved) = try {
            resolve(dataSpec)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.InterruptedIOException("Audio source interrupted")
        }
        source = delegate
        listeners.forEach(delegate::addTransferListener)
        return delegate.open(resolved)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(source).read(buffer, offset, length)

    override fun getUri(): Uri? = source?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = source?.responseHeaders.orEmpty()

    override fun close() {
        val delegate = source
        source = null
        delegate?.close()
    }
}

/** Media3 calls these callbacks while holding the cache monitor. */
@OptIn(UnstableApi::class)
private class AudioCacheEvictor(var limit: Long, private val changed: () -> Unit) : CacheEvictor {
    private val spans = TreeSet(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it })
    private var size = 0L

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        trim(cache, length.takeIf { it != C.LENGTH_UNSET.toLong() } ?: 0)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (spans.add(span)) size += span.length
        trim(cache, 0)
        changed()
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) size -= span.length
        changed()
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        spans.remove(oldSpan)
        spans.add(newSpan)
    }

    fun trim(cache: Cache, incoming: Long) {
        while (spans.isNotEmpty() && size + incoming > limit) {
            val oldest = spans.first()
            cache.removeSpan(oldest)
            if (spans.remove(oldest)) size -= oldest.length
        }
    }
}