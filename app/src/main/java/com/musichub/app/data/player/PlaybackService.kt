package com.musichub.app.data.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musichub.app.MusicHubApplication

/** Keeps playback alive in the background and shows the media notification / lock-screen controls. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as MusicHubApplication).container.player
        session = MediaSession.Builder(this, player).build().also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release() // the ExoPlayer itself is owned by AppContainer
        session = null
        super.onDestroy()
    }
}
