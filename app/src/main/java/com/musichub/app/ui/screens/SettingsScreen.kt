package com.musichub.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.musichub.app.domain.model.ThemeMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musichub.app.ui.components.RemoteTrackRow
import com.musichub.app.ui.components.SectionTitle
import com.musichub.app.ui.components.styledSurface
import com.musichub.app.ui.viewmodel.AppViewModelProvider
import com.musichub.app.ui.viewmodel.PlayerViewModel
import com.musichub.app.ui.viewmodel.SettingsViewModel
import com.musichub.app.data.download.DownloadState
import com.musichub.app.ui.components.TrackTrailing

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    playerViewModel: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDownloads by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = top + 16.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Settings")

        SectionTitle("Theme")
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().styledSurface().padding(12.dp)) {
            ThemeSelector(selected = state.themeMode, onSelect = viewModel::setThemeMode)
        }

        SectionTitle("Appearance", Modifier.padding(top = 8.dp))
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().styledSurface().padding(16.dp)) {
            StyleOption("Liquid Glass", "Translucent, blurred, futuristic", state.isGlassEffectEnabled) {
                viewModel.setGlassEffect(true)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            StyleOption("Matte / Solid", "Opaque surfaces, no blur", !state.isGlassEffectEnabled) {
                viewModel.setGlassEffect(false)
            }
        }

        SectionTitle("Downloads", Modifier.padding(top = 8.dp))
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().styledSurface().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Download lyrics", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Save the song text together with the audio", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
            }
            Switch(checked = state.downloadLyricsEnabled, onCheckedChange = viewModel::setDownloadLyrics)
        }

        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .styledSurface()
                .clickable { showDownloads = !showDownloads }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Downloaded Tracks", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("${state.downloaded.size} tracks available offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
            }
            Icon(
                if (showDownloads) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        AnimatedVisibility(showDownloads) {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.downloaded.isEmpty()) {
                    Text("Nothing downloaded yet", color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                        modifier = Modifier.padding(8.dp))
                }
                state.downloaded.forEach { item ->
                    val track = item.track
                    RemoteTrackRow(
                        track,
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
        }
    }
}

@Composable
private fun StyleOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.65f))
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Default.PhoneAndroid
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
    ThemeMode.OLED -> Icons.Default.Contrast
}

/** Segmented control: System | Light | Dark | OLED */
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "segBg",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                label = "segFg",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(mode.icon(), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(2.dp))
                Text(mode.label, color = content, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
