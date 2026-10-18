package com.musichub.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musichub.app.MusicHubApplication
import com.musichub.app.data.download.DownloadManager
import com.musichub.app.data.download.DownloadState
import com.musichub.app.data.download.DownloadedTrack
import com.musichub.app.data.media.MediaException
import com.musichub.app.data.media.MediaRepository
import com.musichub.app.data.media.SearchCursor
import com.musichub.app.data.player.PlayerController
import com.musichub.app.data.player.PlayerUiState
import com.musichub.app.data.settings.SettingsRepository
import com.musichub.app.domain.model.ThemeMode
import com.musichub.app.domain.model.Track
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// ------------------------------------------------------------------ Settings

data class SettingsUiState(
    val isGlassEffectEnabled: Boolean = true,
    val downloadLyricsEnabled: Boolean = true,
    val downloaded: List<DownloadedTrack> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val downloads: DownloadManager,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.isGlassEffectEnabled,
        settings.downloadLyricsEnabled,
        settings.themeMode,
        downloads.downloaded,
    ) { glass, lyrics, theme, downloaded ->
        SettingsUiState(
            isGlassEffectEnabled = glass,
            downloadLyricsEnabled = lyrics,
            downloaded = downloaded,
            themeMode = theme,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setGlassEffect(enabled: Boolean) {
        viewModelScope.launch { settings.setGlassEffectEnabled(enabled) }
    }

    fun setDownloadLyrics(enabled: Boolean) {
        viewModelScope.launch { settings.setDownloadLyricsEnabled(enabled) }
    }
}

// ------------------------------------------------------------------ Player (+ downloads + lyrics dialog)

data class LyricsUiState(val track: Track, val isLoading: Boolean = true, val text: String? = null)

class PlayerViewModel(
    private val player: PlayerController,
    private val media: MediaRepository,
    private val downloads: DownloadManager,
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> = player.state
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states
    val downloaded: StateFlow<List<DownloadedTrack>> = downloads.downloaded

    private val _lyrics = MutableStateFlow<LyricsUiState?>(null)
    val lyrics: StateFlow<LyricsUiState?> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    fun play(track: Track) = player.play(track)
    fun togglePlayPause() = player.togglePlayPause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun stop() = player.stop()

    fun download(track: Track) = downloads.download(track)
    fun cancelDownload(trackId: String) = downloads.cancel(trackId)
    fun deleteDownload(trackId: String) {
        viewModelScope.launch { downloads.deleteDownload(trackId) }
    }

    fun showLyrics(track: Track) {
        lyricsJob?.cancel()
        _lyrics.value = LyricsUiState(track)
        lyricsJob = viewModelScope.launch {
            val text = downloads.readLyrics(track.id) ?: media.fetchLyrics(track)
            _lyrics.update { it?.copy(isLoading = false, text = text) }
        }
    }

    fun dismissLyrics() {
        lyricsJob?.cancel()
        _lyrics.value = null
    }
}

// ------------------------------------------------------------------ Home

data class HomeUiState(
    val recent: List<Track> = emptyList(),
    val recommendations: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    private val media: MediaRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.recentTracks.collect { recent -> _uiState.update { it.copy(recent = recent) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val tracks = media.searchPage("top hits").tracks.take(8)
                _uiState.update { it.copy(recommendations = tracks, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MediaException) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

// ------------------------------------------------------------------ Catalog (search + endless feed)

data class CatalogUiState(
    val query: String = "",
    val items: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class CatalogViewModel(private val media: MediaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val queries = MutableStateFlow("")
    private var cursor: SearchCursor? = null
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            queries
                .debounce { if (it.isBlank()) 0L else 400L } // blank = default feed, load immediately
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { loadFirstPage(it) }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        queries.value = value
    }

    fun retry() = viewModelScope.launch { loadFirstPage(_uiState.value.query.trim()) }

    private suspend fun loadFirstPage(query: String) {
        moreJob?.cancel()
        cursor = null
        _uiState.update { it.copy(items = emptyList(), isLoading = true, isLoadingMore = false, error = null) }
        try {
            val page = media.searchPage(query.ifEmpty { FEED_QUERY })
            cursor = page.next
            _uiState.update { it.copy(items = page.tracks, isLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaException) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    /** Called when the list scrolls near its end. */
    fun loadMore() {
        val next = cursor ?: return
        if (_uiState.value.isLoading || moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = media.searchPage("", next)
                cursor = page.next
                _uiState.update {
                    it.copy(items = (it.items + page.tracks).distinctBy { t -> t.id }, isLoadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MediaException) {
                cursor = null
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    private companion object {
        const val FEED_QUERY = "popular songs"
    }
}

// ------------------------------------------------------------------ Factory

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = app().container
            SettingsViewModel(c.settingsRepository, c.downloadManager)
        }
        initializer {
            val c = app().container
            PlayerViewModel(c.playerController, c.mediaRepository, c.downloadManager)
        }
        initializer {
            val c = app().container
            HomeViewModel(c.mediaRepository, c.settingsRepository)
        }
        initializer {
            CatalogViewModel(app().container.mediaRepository)
        }
    }
}

private fun CreationExtras.app(): MusicHubApplication = this[APPLICATION_KEY] as MusicHubApplication
