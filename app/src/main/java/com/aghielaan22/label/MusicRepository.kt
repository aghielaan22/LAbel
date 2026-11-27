package com.aghielaan22.label

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import android.util.LruCache
import kotlin.math.abs
import android.net.Uri

object MusicRepository {

    init {
        NewPipe.init(AppDownloader())
    }

    private val urlCache = LruCache<String, Pair<String, Long>>(128)

    // ---- Search ----

    fun searchSongs(query: String): List<Song> =
        searchStreams(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
            .also(PlaylistArtwork::rememberSearchCovers)

    fun searchVideos(query: String): List<Song> =
        searchStreams(query, YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS)

    fun searchAlbums(query: String): List<Album> =
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS)
            .filterIsInstance<PlaylistInfoItem>()
            .map {
                Album(
                    id = it.url,
                    title = it.name,
                    artist = it.uploaderName ?: "",
                    thumbnailUrl = bestThumbnail(it)
                )
            }
            .distinctBy { it.id }

    fun searchArtists(query: String): List<Artist> =
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS)
            .filterIsInstance<ChannelInfoItem>()
            .map { Artist(name = it.name, thumbnailUrl = bestThumbnail(it)) }
            .distinctBy { it.name }

    // ---- Pages ----

    fun getAlbumTracks(albumId: String): List<Song> {
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, albumId)
        return info.relatedItems
            .map { toSong(it) }
            .distinctBy { it.id }
    }

    fun getArtistDetails(name: String): ArtistDetails {
        fun credited(text: String) = text.contains(name, ignoreCase = true)

        val songs = searchSongs(name).filter { credited(it.artist) }
        val albums = try {
            searchAlbums(name).filter { credited(it.artist) }
        } catch (e: Exception) {
            emptyList()
        }
        return ArtistDetails(songs, albums)
    }

    /** Songs similar to [song], taken from YouTube's Mix for that song. */
    fun getRadio(song: Song): List<Song> {
        val videoId = Uri.parse(song.id).getQueryParameter("v") ?: return emptyList()
        val mixUrl = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, mixUrl)

        return info.relatedItems
            .map { item ->
                // Mix links can carry extra list information, so keep just the video address
                val id = Uri.parse(item.url).getQueryParameter("v")
                    ?.let { "https://www.youtube.com/watch?v=$it" } ?: item.url
                toSong(item).copy(id = id)
            }
            .filter { it.id != song.id }
            .distinctBy { it.id }
    }
    /** Loads a YouTube or YouTube Music playlist: its name and up to [maxSongs] songs. */
    fun importPlaylist(url: String, maxSongs: Int = 500): Pair<String, List<Song>> {
        val service = ServiceList.YouTube
        val info = PlaylistInfo.getInfo(service, url)
        val songs = info.relatedItems.map { toSong(it) }.toMutableList()

        var next = info.nextPage
        var pages = 0
        while (next != null && songs.size < maxSongs && pages < 10) {
            val page = PlaylistInfo.getMoreItems(service, url, next)
            songs.addAll(page.items.map { toSong(it) })
            next = page.nextPage
            pages++
        }
        return info.name to songs.distinctBy { it.id }.take(maxSongs)
    }

    // ---- Playback ----

    fun invalidateAudioUrl(videoUrl: String) {
        AudioQuality.entries.forEach { urlCache.remove("$videoUrl|${it.name}") }
    }

    fun getAudioUrl(videoUrl: String, quality: AudioQuality, useCache: Boolean = true): String {        val cacheKey = "$videoUrl|${quality.name}"

        val cached = urlCache.get(cacheKey)
        val now = System.currentTimeMillis()
        if (useCache && cached != null && now < cached.second) {
            return cached.first
        }

        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val streams = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

        val chosen = when (quality) {
            AudioQuality.High -> streams.maxByOrNull { it.averageBitrate }
            AudioQuality.Standard -> streams
                .filter { it.averageBitrate > 0 }
                .minByOrNull { abs(it.averageBitrate - 128) }
            AudioQuality.DataSaver -> streams
                .filter { it.averageBitrate > 0 }
                .minByOrNull { it.averageBitrate }
        } ?: streams.firstOrNull()
        ?: throw IllegalStateException("No playable audio stream found")

        val expiry = Uri.parse(chosen.content).getQueryParameter("expire")
        val validUntil = StreamCachePolicy.validUntil(now, expiry)
        if (useCache) urlCache.put(cacheKey, chosen.content to validUntil)
        return chosen.content
    }
    // ---- Helpers ----

    private fun searchItems(query: String, filter: String): List<InfoItem> {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        return SearchInfo.getInfo(service, handler).relatedItems
    }

    private fun searchStreams(query: String, filter: String): List<Song> =
        searchItems(query, filter)
            .filterIsInstance<StreamInfoItem>()
            .map { toSong(it) }
            .distinctBy { it.id }

    private fun toSong(item: StreamInfoItem) = Song(
        id = item.url,
        title = item.name,
        artist = item.uploaderName ?: "Unknown artist",
        thumbnailUrl = bestThumbnail(item)
    )

    private fun bestThumbnail(item: InfoItem): String? {
        val images = item.thumbnails
        // Some entries don't say how big they are, and the list usually runs small to large
        val known = images.filter { it.height > 0 }
        return (known.maxByOrNull { it.height } ?: images.lastOrNull())?.url
    }
}