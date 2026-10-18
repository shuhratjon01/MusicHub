package com.musichub.app.data.media

import android.util.Log
import com.musichub.app.data.settings.SettingsRepository
import com.musichub.app.domain.model.Track
import com.musichub.app.domain.model.TrackDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.coroutines.cancellation.CancellationException

/** Single error type exposed to the UI layer. */
class MediaException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Opaque paging cursor: pass it back to [MediaRepository.searchPage] to get the next page. */
class SearchCursor internal constructor(
    internal val handler: SearchQueryHandler,
    internal val page: Page,
)

data class TracksPage(val tracks: List<Track>, val next: SearchCursor?)

class MediaRepository(
    client: OkHttpClient,
    private val settings: SettingsRepository,
    private val lyricsProvider: LyricsProvider,
) {
    private val service get() = ServiceList.YouTube

    init {
        initNewPipe(client)
    }

    /** First page of YouTube Music songs for [query]. @throws MediaException */
    suspend fun searchTracks(query: String): List<Track> = searchPage(query).tracks

    /**
     * Paged search. First call: pass [query] and `cursor = null`. Next calls: pass the previous
     * [TracksPage.next] (query is ignored). `next == null` means there is nothing more.
     */
    suspend fun searchPage(query: String, cursor: SearchCursor? = null): TracksPage =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (cursor == null && q.isEmpty()) return@withContext TracksPage(emptyList(), null)
            extract {
                if (cursor == null) {
                    // 1) YouTube Music "songs" filter, 2) fallback: regular search (more tolerant to YouTube changes)
                    val musicHandler = service.searchQHFactory.fromQuery(
                        q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""
                    )
                    val musicInfo = runCatching { SearchInfo.getInfo(service, musicHandler) }.getOrNull()
                    val musicTracks = musicInfo?.relatedItems?.toTracks().orEmpty()
                    if (musicInfo != null && musicTracks.isNotEmpty()) {
                        TracksPage(
                            musicTracks,
                            if (musicInfo.hasNextPage()) musicInfo.nextPage?.let { SearchCursor(musicHandler, it) } else null,
                        )
                    } else {
                        val handler = service.searchQHFactory.fromQuery(q, emptyList(), "")
                        val info = SearchInfo.getInfo(service, handler)
                        val tracks = info.relatedItems.toTracks()
                        if (tracks.isEmpty() && info.errors.isNotEmpty()) {
                            // Items were found but could not be parsed -> extractor is outdated. Show the real reason.
                            throw MediaException("Extractor error: ${info.errors.first().message ?: info.errors.first()}")
                        }
                        TracksPage(
                            tracks,
                            if (info.hasNextPage()) info.nextPage?.let { SearchCursor(handler, it) } else null,
                        )
                    }
                } else {
                    val page = SearchInfo.getMoreItems(service, cursor.handler, cursor.page)
                    TracksPage(
                        page.items.toTracks(),
                        if (page.hasNextPage()) page.nextPage?.let { SearchCursor(cursor.handler, it) } else null,
                    )
                }
            }
        }

    /**
     * Resolves a fresh direct audio URL + cover. Lyrics are included only if [includeLyrics] and the
     * `downloadLyricsEnabled` preference are both on. @throws MediaException
     */
    suspend fun fetchTrackDetails(trackId: String, includeLyrics: Boolean = true): TrackDetails =
        withContext(Dispatchers.IO) {
            val info = extract { StreamInfo.getInfo(service, service.streamLHFactory.getUrl(trackId)) }
            val audio = selectBestAudioStream(info.audioStreams)
                ?: throw MediaException("No playable audio stream found for this track")

            val cover = info.thumbnails.maxByOrNull { it.height }?.url
            val track = Track(
                id = trackId,
                title = info.name,
                artist = cleanArtist(info.uploaderName),
                duration = info.duration.coerceAtLeast(0),
                thumbnailUrl = cover.orEmpty(),
                streamUrl = audio.content,
            )
            val lyrics = if (includeLyrics && settings.downloadLyricsEnabled.first()) {
                fetchLyrics(track, info.description?.content)
            } else null

            TrackDetails(track = track, lyrics = lyrics, highResCoverUrl = cover)
        }

    /** Fetches lyrics regardless of the preference (callers decide). Never throws except on cancellation. */
    suspend fun fetchLyrics(track: Track, description: String? = null): String? = try {
        lyricsProvider.getLyrics(track, description)?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Lyrics lookup failed", e)
        null
    }

    // ---- helpers ----

    private fun List<InfoItem>.toTracks(): List<Track> =
        filterIsInstance<StreamInfoItem>().map { it.toTrack() }.distinctBy { it.id }

    private fun StreamInfoItem.toTrack(): Track {
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrDefault(url)
        return Track(
            id = id,
            title = name,
            artist = cleanArtist(uploaderName),
            duration = duration.coerceAtLeast(0),
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
            streamUrl = null,
        )
    }

    private fun cleanArtist(name: String?): String = name.orEmpty().removeSuffix(" - Topic").trim()

    /** Prefers progressive M4A/MP3 (widest player + file support), original language, highest bitrate. */
    private fun selectBestAudioStream(streams: List<AudioStream>): AudioStream? {
        val progressive = streams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val original = progressive.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { progressive }
        val preferred = original.filter {
            it.format == MediaFormat.M4A || it.format == MediaFormat.MP3
        }.ifEmpty { original }
        return preferred.maxByOrNull { it.averageBitrate }
    }

    private inline fun <T> extract(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MediaException(e.message ?: "Unknown extractor error", e)
    }

    private companion object {
        const val TAG = "MediaRepository"
        @Volatile private var initialised = false

        fun initNewPipe(client: OkHttpClient) = synchronized(this) {
            if (!initialised) {
                NewPipe.init(NewPipeDownloader(client))
                initialised = true
            }
        }
    }
}
