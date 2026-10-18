package com.musichub.app.data.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musichub.app.data.download.DownloadManager
import com.musichub.app.data.media.MediaException
import com.musichub.app.data.media.MediaRepository
import com.musichub.app.data.settings.SettingsRepository
import com.musichub.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

/** Main-thread facade over the shared [ExoPlayer]. Downloaded tracks play from disk, others are streamed. */
class PlayerController(
    context: Context,
    private val player: ExoPlayer,
    private val media: MediaRepository,
    private val downloads: DownloadManager,
    private val settings: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        isLoading = playbackState == Player.STATE_BUFFERING || loadJob?.isActive == true,
                        durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs,
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error", error)
                _state.update {
                    it.copy(isLoading = false, isPlaying = false, error = "Playback error: ${error.errorCodeName}")
                }
            }
        })
        scope.launch {
            while (true) {
                delay(500)
                if (player.isPlaying) {
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs,
                        )
                    }
                }
            }
        }
    }

    fun play(track: Track) {
        loadJob?.cancel()
        player.stop()
        _state.value = PlayerUiState(track = track, isLoading = true)

        loadJob = scope.launch {
            try {
                val local = downloads.downloaded.value.firstOrNull { it.track.id == track.id }
                val uri: String
                val artwork: String?
                if (local != null) {
                    uri = local.track.streamUrl ?: Uri.fromFile(local.audioFile).toString()
                    artwork = local.coverFile?.let { Uri.fromFile(it).toString() } ?: track.thumbnailUrl
                } else {
                    val details = media.fetchTrackDetails(track.id, includeLyrics = false)
                    uri = details.track.streamUrl ?: throw MediaException("No audio stream available")
                    artwork = details.highResCoverUrl ?: track.thumbnailUrl
                }

                val item = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .apply { if (!artwork.isNullOrBlank()) setArtworkUri(Uri.parse(artwork)) }
                            .build()
                    )
                    .build()

                player.setMediaItem(item)
                player.prepare()
                player.play()
                startService()
                _state.update { it.copy(isLoading = player.playbackState == Player.STATE_BUFFERING) }
                settings.addRecent(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MediaException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                Log.e(TAG, "play() failed", e)
                _state.update { it.copy(isLoading = false, error = e.message ?: "Cannot play this track") }
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.track == null) return
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
            player.mediaItemCount == 0 -> _state.value.track?.let(::play) // e.g. after an error
            else -> player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun stop() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = PlayerUiState()
    }

    private fun startService() {
        try {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start PlaybackService", e)
        }
    }

    private companion object {
        const val TAG = "PlayerController"
    }
}
