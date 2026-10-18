package com.musichub.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musichub.app.ui.components.GlassNavBar
import com.musichub.app.ui.components.LyricsDialog
import com.musichub.app.ui.components.MiniPlayer
import com.musichub.app.ui.components.NavItem
import com.musichub.app.ui.screens.CatalogScreen
import com.musichub.app.ui.screens.HomeScreen
import com.musichub.app.ui.screens.ProfileScreen
import com.musichub.app.ui.screens.SettingsScreen
import com.musichub.app.ui.theme.LocalUiStyle
import com.musichub.app.ui.theme.UiStyle
import com.musichub.app.ui.theme.appBackgroundBrush
import com.musichub.app.ui.viewmodel.AppViewModelProvider
import com.musichub.app.ui.viewmodel.PlayerViewModel
import com.musichub.app.ui.viewmodel.SettingsViewModel

enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Catalog("Catalog", Icons.Default.Search),
    Settings("Settings", Icons.Default.Settings),
    Profile("Profile", Icons.Default.Person),
}

@Composable
fun MusicHubApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val style = if (settings.isGlassEffectEnabled) UiStyle.Glass else UiStyle.Matte

    val playerViewModel: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyrics.collectAsStateWithLifecycle()

    val backdrop = rememberGraphicsLayer()
    val navItems = remember { AppTab.entries.map { NavItem(it.label, it.icon) } }

    CompositionLocalProvider(LocalUiStyle provides style) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = playerState,
                        onToggle = playerViewModel::togglePlayPause,
                        onClose = playerViewModel::stop,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                    GlassNavBar(
                        items = navItems,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        backdrop = backdrop,
                        style = style,
                    )
                }
            },
        ) { innerPadding ->
            // Content deliberately fills the whole screen (also behind the floating bar);
            // it is recorded into [backdrop] so the bar can blur what is under it.
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }
                    .background(appBackgroundBrush())
            ) {
                Crossfade(selectedTab, label = "tabs") { index ->
                    when (AppTab.entries[index]) {
                        AppTab.Home -> HomeScreen()
                        AppTab.Catalog -> CatalogScreen()
                        AppTab.Settings -> SettingsScreen()
                        AppTab.Profile -> ProfileScreen()
                    }
                }
            }
        }
        lyricsState?.let { LyricsDialog(it, onDismiss = playerViewModel::dismissLyrics) }
    }
}
