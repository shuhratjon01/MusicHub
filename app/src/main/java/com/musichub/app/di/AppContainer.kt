package com.musichub.app.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.musichub.app.data.download.DownloadManager
import com.musichub.app.data.media.CompositeLyricsProvider
import com.musichub.app.data.media.DEFAULT_USER_AGENT
import com.musichub.app.data.media.DescriptionLyricsProvider
import com.musichub.app.data.media.LrcLibLyricsProvider
import com.musichub.app.data.media.MediaRepository
import com.musichub.app.data.player.PlayerController
import com.musichub.app.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container (swap for Hilt/Koin later if you wish). Created on the main thread. */
@OptIn(UnstableApi::class)
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val settingsRepository = SettingsRepository(appContext)

    private val lyricsProvider = CompositeLyricsProvider(
        listOf(DescriptionLyricsProvider(), LrcLibLyricsProvider(okHttpClient))
    )

    val mediaRepository = MediaRepository(okHttpClient, settingsRepository, lyricsProvider)

    val downloadManager = DownloadManager(appContext, okHttpClient, mediaRepository, settingsRepository)

    /** Single player shared by the UI controller and the background [com.musichub.app.data.player.PlaybackService]. */
    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(appContext).setDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent(DEFAULT_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
            )
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    val playerController = PlayerController(appContext, player, mediaRepository, downloadManager, settingsRepository)
}
