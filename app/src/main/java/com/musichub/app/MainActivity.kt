package com.musichub.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musichub.app.domain.model.ThemeMode
import com.musichub.app.ui.MusicHubApp
import com.musichub.app.ui.theme.MusicHubTheme
import com.musichub.app.ui.theme.isDark

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* playback works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val container = (application as MusicHubApplication).container
            val themeMode by container.settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val isDark = themeMode.isDark()

            // Keeps status/navigation bar icons readable when the chosen theme differs from the system one
            DisposableEffect(isDark) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { isDark },
                )
                onDispose {}
            }

            MusicHubTheme(themeMode) { MusicHubApp() }
        }
    }
}
