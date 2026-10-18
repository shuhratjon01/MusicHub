package com.musichub.app.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.musichub.app.data.media.DEFAULT_USER_AGENT
import com.musichub.app.data.media.MediaRepository
import com.musichub.app.data.settings.SettingsRepository
import com.musichub.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Downloading(val progress: Int) : DownloadState // 0..100
    data object Completed : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** A fully downloaded track. [track].streamUrl is a local `file://` URI, ready for the player. */
data class DownloadedTrack(
    val track: Track,
    val audioFile: File,
    val coverFile: File?,
    val lyricsFile: File?,
)

/**
 * Offline storage layout (app-private, no permissions needed):
 * `files/downloads/<trackId>/{audio.m4a|mp3, cover.jpg, lyrics.txt, meta.json}`
 * Files are written into `<trackId>.tmp` first and atomically renamed, so a cancelled or failed
 * download never leaves a half-finished track behind.
 */
class DownloadManager(
    context: Context,
    private val client: OkHttpClient,
    private val mediaRepository: MediaRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val root = File(context.applicationContext.filesDir, "downloads").apply { mkdirs() }
    private val jobs = ConcurrentHashMap<String, Job>()
    private val semaphore = Semaphore(MAX_PARALLEL)

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    /** trackId -> state. Missing key = not downloaded and not in progress. */
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val _downloaded = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    val downloaded: StateFlow<List<DownloadedTrack>> = _downloaded.asStateFlow()

    init {
        scope.launch { refreshDownloaded() }
    }

    fun stateOf(trackId: String): Flow<DownloadState?> = states.map { it[trackId] }.distinctUntilChanged()

    fun isDownloaded(trackId: String): Boolean = _downloaded.value.any { it.track.id == trackId }

    /** Starts a download (no-op if already downloaded or running). Observe [states] for progress. */
    fun download(track: Track) {
        if (isDownloaded(track.id)) {
            setState(track.id, DownloadState.Completed)
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                semaphore.withPermit { runDownload(track) }
                setState(track.id, DownloadState.Completed)
            } catch (e: CancellationException) {
                cleanupStage(track.id)
                clearState(track.id)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${track.id}", e)
                cleanupStage(track.id)
                setState(track.id, DownloadState.Failed(e.message ?: "Download failed"))
            } finally {
                jobs.remove(track.id)
            }
        }
        if (jobs.putIfAbsent(track.id, job) != null) {
            job.cancel()
            return
        }
        setState(track.id, DownloadState.Queued)
        job.start()
    }

    fun cancel(trackId: String) {
        jobs[trackId]?.cancel()
    }

    suspend fun deleteDownload(trackId: String) {
        cancel(trackId)
        withContext(Dispatchers.IO) { File(root, safeId(trackId)).deleteRecursively() }
        clearState(trackId)
        refreshDownloaded()
    }

    suspend fun readLyrics(trackId: String): String? = withContext(Dispatchers.IO) {
        File(File(root, safeId(trackId)), LYRICS).takeIf { it.isFile }?.readText()
    }

    // ---------------------------------------------------------------- pipeline

    private suspend fun runDownload(track: Track) {
        val saveLyrics = settings.downloadLyricsEnabled.first()
        val details = mediaRepository.fetchTrackDetails(track.id) // fresh, non-expired stream URL
        val streamUrl = details.track.streamUrl ?: throw IOException("No audio stream available")

        val stage = File(root, safeId(track.id) + TMP_SUFFIX)
        val finalDir = File(root, safeId(track.id))

        withContext(Dispatchers.IO) {
            stage.deleteRecursively()
            if (!stage.mkdirs()) throw IOException("Cannot create download directory")
            setProgress(track.id, 0)

            // 1) audio (0..95 %)
            val part = File(stage, "audio.part")
            val mime = downloadAudio(streamUrl, part) { pct ->
                setProgress(track.id, pct * AUDIO_SHARE / 100)
            }
            val audioName = "audio.${extensionFor(streamUrl, mime)}"
            if (!part.renameTo(File(stage, audioName))) throw IOException("Cannot finalize audio file")

            // 2) cover (non-critical)
            runOptional("cover") {
                val url = details.highResCoverUrl?.takeIf { it.isNotBlank() }
                    ?: details.track.thumbnailUrl.takeIf { it.isNotBlank() }
                    ?: return@runOptional
                downloadToFile(url, File(stage, COVER))
            }
            setProgress(track.id, 97)

            // 3) lyrics - only if the user enabled it (non-critical)
            if (saveLyrics) {
                runOptional("lyrics") {
                    val lyrics = details.lyrics ?: mediaRepository.fetchLyrics(details.track)
                    if (!lyrics.isNullOrBlank()) File(stage, LYRICS).writeText(lyrics)
                }
            }
            setProgress(track.id, 99)

            // 4) metadata + atomic publish
            File(stage, META).writeText(
                JSONObject()
                    .put("id", details.track.id)
                    .put("title", details.track.title)
                    .put("artist", details.track.artist)
                    .put("duration", details.track.duration)
                    .put("thumbnailUrl", details.track.thumbnailUrl)
                    .put("audioFile", audioName)
                    .toString()
            )
            finalDir.deleteRecursively()
            if (!stage.renameTo(finalDir)) throw IOException("Cannot publish downloaded files")
        }
        refreshDownloaded()
    }

    /**
     * Downloads in 4 MB Range chunks (avoids throttling on large single requests) and falls back to
     * a plain full download if the server ignores Range. Returns the response Content-Type.
     */
    private suspend fun downloadAudio(url: String, target: File, onProgress: (Int) -> Unit): String? {
        var offset = 0L
        var total = -1L
        var mime: String? = null
        var finished = false
        var lastPct = -1

        FileOutputStream(target).use { out ->
            while (!finished) {
                currentCoroutineContext().ensureActive()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Range", "bytes=$offset-${offset + CHUNK_SIZE - 1}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 416 && offset > 0) { // range past EOF
                        finished = true
                        return@use
                    }
                    if (!response.isSuccessful) throw IOException("Server returned HTTP ${response.code}")
                    if (mime == null) mime = response.header("Content-Type")

                    val body = response.body ?: throw IOException("Empty response body")
                    val partial = response.code == 206
                    if (!partial && offset > 0) throw IOException("Server ignored Range request")
                    total = if (partial) parseTotal(response.header("Content-Range")) else body.contentLength()

                    val read = copyWithProgress(body.byteStream(), out) { copied ->
                        if (total > 0) {
                            val pct = ((offset + copied) * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    offset += read
                    if (!partial || read == 0L || (total in 1..offset)) finished = true
                }
            }
        }
        if (target.length() == 0L) throw IOException("Downloaded file is empty")
        onProgress(100)
        return mime
    }

    private suspend fun downloadToFile(url: String, target: File) {
        val request = Request.Builder().url(url).header("User-Agent", DEFAULT_USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            FileOutputStream(target).use { out -> copyWithProgress(body.byteStream(), out) {} }
        }
    }

    private suspend fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        onCopied: (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(16 * 1024)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            copied += n
            onCopied(copied)
        }
        return copied
    }

    private suspend fun runOptional(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Optional step '$what' failed", e)
        }
    }

    // ---------------------------------------------------------------- state & storage

    private fun setProgress(id: String, pct: Int) = setState(id, DownloadState.Downloading(pct.coerceIn(0, 100)))
    private fun setState(id: String, s: DownloadState) = _states.update { it + (id to s) }
    private fun clearState(id: String) = _states.update { it - id }

    private fun cleanupStage(id: String) {
        File(root, safeId(id) + TMP_SUFFIX).deleteRecursively()
    }

    private suspend fun refreshDownloaded() {
        val list = withContext(Dispatchers.IO) {
            root.listFiles { f -> f.isDirectory && !f.name.endsWith(TMP_SUFFIX) }
                ?.mapNotNull(::readEntry)
                ?.sortedBy { it.audioFile.lastModified() }
                ?.reversed()
                .orEmpty()
        }
        _downloaded.value = list
        val done = list.associate { it.track.id to DownloadState.Completed as DownloadState }
        _states.update { cur -> cur.filterValues { it !is DownloadState.Completed } + done }
    }

    private fun readEntry(dir: File): DownloadedTrack? = try {
        val meta = File(dir, META)
        if (!meta.isFile) null else {
            val j = JSONObject(meta.readText())
            val audio = File(dir, j.getString("audioFile"))
            if (!audio.isFile) null else DownloadedTrack(
                track = Track(
                    id = j.getString("id"),
                    title = j.getString("title"),
                    artist = j.optString("artist", ""),
                    duration = j.optLong("duration", 0),
                    thumbnailUrl = j.optString("thumbnailUrl", ""),
                    streamUrl = Uri.fromFile(audio).toString(),
                ),
                audioFile = audio,
                coverFile = File(dir, COVER).takeIf { it.isFile },
                lyricsFile = File(dir, LYRICS).takeIf { it.isFile },
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Corrupted download entry ${dir.name}", e)
        null
    }

    private fun parseTotal(contentRange: String?): Long =
        contentRange?.substringAfterLast('/')?.toLongOrNull() ?: -1L

    private fun extensionFor(url: String, contentType: String?): String {
        val mime = (url.toHttpUrlOrNull()?.queryParameter("mime") ?: contentType).orEmpty().lowercase()
        return when {
            "mp4" in mime || "m4a" in mime || "aac" in mime -> "m4a"
            "mpeg" in mime || "mp3" in mime -> "mp3"
            "webm" in mime -> "webm"
            "ogg" in mime -> "ogg"
            else -> "m4a"
        }
    }

    private fun safeId(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val TAG = "DownloadManager"
        const val MAX_PARALLEL = 2
        const val CHUNK_SIZE = 4L * 1024 * 1024
        const val AUDIO_SHARE = 95
        const val TMP_SUFFIX = ".tmp"
        const val COVER = "cover.jpg"
        const val LYRICS = "lyrics.txt"
        const val META = "meta.json"
    }
}
