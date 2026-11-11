package com.markmusic.player.audio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow

object AudioSessionBus {
    val sessionId = MutableStateFlow(0)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    override fun onCreate() {
        super.onCreate()
        val p = ExoPlayer.Builder(this).build()
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
        )
        p.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioSessionBus.sessionId.value = audioSessionId
            }
        })
        player = p
        session = MediaSession.Builder(this, p).build()
        AudioSessionBus.sessionId.value = p.audioSessionId
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.isPlaying != true) stopSelf()
    }
    override fun onDestroy() {
        AudioSessionBus.sessionId.value = 0
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
