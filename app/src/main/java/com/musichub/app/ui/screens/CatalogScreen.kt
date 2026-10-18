package com.musichub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musichub.app.MusicHubApplication
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musichub.app.ui.components.RemoteTrackRow
import com.musichub.app.ui.components.SectionTitle
import com.musichub.app.ui.components.TrackTrailing
import com.musichub.app.ui.viewmodel.AppViewModelProvider
import com.musichub.app.ui.viewmodel.CatalogViewModel
import com.musichub.app.ui.viewmodel.PlayerViewModel

@Composable
fun CatalogScreen(
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = viewModel(factory = AppViewModelProvider.Factory),
    playerViewModel: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Read download states straight from the DownloadManager (does not depend on PlayerViewModel.downloadStates)
    val container = (LocalContext.current.applicationContext as MusicHubApplication).container
    val downloads by container.downloadManager.states.collectAsStateWithLifecycle()
    val player by playerViewModel.playerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Endless scroll: ask for the next page when the user gets close to the end.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd && state.items.isNotEmpty()) viewModel.loadMore()
    }

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(top + 16.dp))
        SectionTitle("Catalog")
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("Search tracks, artists…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            },
            shape = CircleShape,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) item(key = "loading") { Spinner() }
            state.error?.let { msg ->
                item(key = "error") {
                    Column(Modifier.padding(8.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
                    }
                }
            }
            if (!state.isLoading && state.error == null && state.items.isEmpty()) {
                item(key = "empty") {
                    Text("No results", color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                        modifier = Modifier.padding(8.dp))
                }
            }
            items(state.items, key = { it.id }) { track ->
                RemoteTrackRow(
                    track = track,
                    isCurrent = player.track?.id == track.id,
                    onClick = { playerViewModel.play(track) },
                ) {
                    TrackTrailing(
                        track = track,
                        downloadState = downloads[track.id],
                        onPlay = { playerViewModel.play(track) },
                        onDownload = { playerViewModel.download(track) },
                        onCancel = { playerViewModel.cancelDownload(track.id) },
                        onDelete = { playerViewModel.deleteDownload(track.id) },
                        onLyrics = { playerViewModel.showLyrics(track) },
                    )
                }
            }
            if (state.isLoadingMore) item(key = "more") { Spinner() }
        }
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}
