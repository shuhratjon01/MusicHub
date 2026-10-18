package com.musichub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musichub.app.data.MockData
import com.musichub.app.data.download.DownloadState
import com.musichub.app.ui.components.*
import com.musichub.app.ui.viewmodel.AppViewModelProvider
import com.musichub.app.ui.viewmodel.HomeViewModel
import com.musichub.app.ui.viewmodel.PlayerViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
    playerViewModel: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val downloaded by playerViewModel.downloaded.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = top + 16.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Good evening 👋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item { SectionTitle("Continue Listening") }
        item {
            if (state.recent.isEmpty()) {
                Text(
                    "Tracks you play will show up here.",
                    color = MaterialTheme.colorScheme.onBackground.copy(0.65f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.recent, key = { it.id }) { track ->
                        RemoteTrackCard(track, onClick = { playerViewModel.play(track) })
                    }
                }
            }
        }

        item { SectionTitle("Downloaded", Modifier.padding(top = 8.dp)) }
        if (downloaded.isEmpty()) {
            item {
                Text(
                    "Tracks saved for offline listening will appear here.",
                    color = MaterialTheme.colorScheme.onBackground.copy(0.65f),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else {
            items(downloaded, key = { "dl_${it.track.id}" }) { item ->
                val track = item.track
                RemoteTrackRow(
                    track = track,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    coverModel = item.coverFile ?: track.thumbnailUrl,
                    onClick = { playerViewModel.play(track) },
                ) {
                    TrackTrailing(
                        track = track,
                        downloadState = DownloadState.Completed,
                        onPlay = { playerViewModel.play(track) },
                        onDownload = {},
                        onCancel = {},
                        onDelete = { playerViewModel.deleteDownload(track.id) },
                        onLyrics = { playerViewModel.showLyrics(track) },
                    )
                }
            }
        }

        item { SectionTitle("Top Recommendations", Modifier.padding(top = 8.dp)) }
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
        }
        state.error?.let { msg ->
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = homeViewModel::refresh) { Text("Retry") }
                }
            }
        }
        items(state.recommendations.chunked(2)) { pair ->
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                pair.forEach { track ->
                    Box(Modifier.weight(1f)) { RemoteGridTrack(track, onClick = { playerViewModel.play(track) }) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item { SectionTitle("Best Playlists", Modifier.padding(top = 8.dp)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) { items(MockData.playlists, key = { it.id }) { PlaylistCard(it) } }
        }
    }
}
