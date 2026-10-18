package com.musichub.app.data.media

import com.musichub.app.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * NewPipeExtractor has no dedicated lyrics API, so lyrics are resolved through a chain of providers.
 * [description] is the video description coming from the extractor (may be null).
 */
interface LyricsProvider {
    suspend fun getLyrics(track: Track, description: String?): String?
}

/** Looks for a "Lyrics:" block inside the video description (offline heuristic, no network). */
class DescriptionLyricsProvider : LyricsProvider {
    private val marker = Regex("(?i)^[\\[(]?\\s*(lyrics|текст( песни)?)\\s*[\\])]?:?$")

    override suspend fun getLyrics(track: Track, description: String?): String? {
        if (description.isNullOrBlank()) return null
        val lines = description.lines()
        val start = lines.indexOfFirst { marker.matches(it.trim()) }
        if (start < 0) return null
        val text = lines.drop(start + 1)
            .dropWhile { it.isBlank() }
            .takeWhile { !it.trimStart().startsWith("#") && !it.contains("http", ignoreCase = true) }
            .joinToString("\n")
            .trim()
        return text.takeIf { t -> t.lines().count { it.isNotBlank() } >= 4 }
    }
}

/** Free community lyrics database (https://lrclib.net), no API key required. */
class LrcLibLyricsProvider(private val client: OkHttpClient) : LyricsProvider {

    override suspend fun getLyrics(track: Track, description: String?): String? =
        withContext(Dispatchers.IO) {
            val title = cleanTitle(track.title, track.artist)
            val artist = track.artist

            val exactUrl = BASE.newBuilder().addPathSegment("get")
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .apply { if (track.duration > 0) addQueryParameter("duration", track.duration.toString()) }
                .build()
            fetch(exactUrl)?.let { return@withContext extract(JSONObject(it)) }

            val searchUrl = BASE.newBuilder().addPathSegment("search")
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .build()
            val arr = JSONArray(fetch(searchUrl) ?: return@withContext null)
            for (i in 0 until arr.length()) {
                extract(arr.getJSONObject(i))?.let { return@withContext it }
            }
            null
        }

    private fun fetch(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", "MusicHub/1.0").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("LRCLIB HTTP ${response.code}")
            return (response.body ?: throw IOException("Empty response body")).string()
        }
    }

    private fun extract(obj: JSONObject): String? {
        if (obj.optBoolean("instrumental", false) || obj.isNull("plainLyrics")) return null
        return obj.getString("plainLyrics").takeIf { it.isNotBlank() }
    }

    private companion object {
        val BASE = "https://lrclib.net/api".toHttpUrl()
    }
}

class CompositeLyricsProvider(private val providers: List<LyricsProvider>) : LyricsProvider {
    override suspend fun getLyrics(track: Track, description: String?): String? {
        for (p in providers) {
            try {
                p.getLyrics(track, description)?.let { return it }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                // try the next provider
            }
        }
        return null
    }
}

/** "Artist - Song (Official Video) [HD]" -> "Song" */
internal fun cleanTitle(title: String, artist: String): String {
    var t = title.replace(Regex("[\\(\\[][^)\\]]*[\\)\\]]"), " ")
    val parts = t.split(Regex("\\s[-–—]\\s"), limit = 2)
    if (parts.size == 2 && parts[0].trim().equals(artist.trim(), ignoreCase = true)) t = parts[1]
    return t.replace(Regex("\\s+"), " ").trim().ifEmpty { title }
}
