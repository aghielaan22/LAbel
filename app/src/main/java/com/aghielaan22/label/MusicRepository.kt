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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object MusicRepository {

    init {
        NewPipe.init(AppDownloader())
    }

    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    // ---- Search ----

    fun searchSongs(query: String): List<Song> =
        searchStreams(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)

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

    // ---- Playback ----

    fun getAudioUrl(videoUrl: String): String {
        val quality = AppSettings.audioQuality
        val cacheKey = "$videoUrl|${quality.name}"

        val cached = urlCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < 5 * 60 * 60 * 1000L) {
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

        urlCache[cacheKey] = chosen.content to now
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

    private fun bestThumbnail(item: InfoItem): String? =
        item.thumbnails.maxByOrNull { it.height }?.url
}