package com.aghielaan22.label

import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

object MusicRepository {

    init {
        NewPipe.init(AppDownloader())
    }

    fun searchSongs(query: String): List<Song> {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        val info = SearchInfo.getInfo(service, handler)

        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                Song(
                    id = item.url,
                    title = item.name,
                    artist = item.uploaderName ?: "Unknown artist",
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url
                )
            }
            .distinctBy { it.id }
    }

    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    fun getAudioUrl(videoUrl: String): String {
        val cached = urlCache[videoUrl]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < 5 * 60 * 60 * 1000L) {
            return cached.first
        }

        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val best = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No playable audio stream found")

        urlCache[videoUrl] = best.content to now
        return best.content
    }
}