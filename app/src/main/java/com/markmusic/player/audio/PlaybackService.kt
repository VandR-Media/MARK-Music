package com.markmusic.player.audio

import android.content.Intent
import android.app.PendingIntent
import com.markmusic.player.MainActivity
import org.json.JSONArray
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AudioSessionBus {
    val sessionId = MutableStateFlow(0)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private val eqScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eqEngine = MarkEqualizerEngine()
    private var meterSink: MeterAudioBufferSink? = null
    override fun onCreate() {
        super.onCreate()
        MarkEqualizer.load(this)
        eqScope.launch {
            combine(AudioSessionBus.sessionId, MarkEqualizer.state, MarkEqualizer.enabled) {
                sessionId, preset, active -> Triple(sessionId, preset, active)
            }.collect { (sessionId, preset, active) ->
                eqEngine.update(sessionId, preset, active)
            }
        }
        val capturedMeterSink = MeterAudioBufferSink()
        meterSink = capturedMeterSink
        // A pass-through tee reads PCM for authentic studio VU/LED meters.
        // No RECORD_AUDIO permission and no file/network access are involved.
        val rendererFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(
                    TeeAudioProcessor(capturedMeterSink)
                ))
                .build()
        }
        val p = ExoPlayer.Builder(this, rendererFactory).build()
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
        val openPlayerIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPlayer = PendingIntent.getActivity(
            this, 100, openPlayerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaSession.Builder(this, p)
            .setSessionActivity(openPlayer)
            .build()
        AudioSessionBus.sessionId.value = p.audioSessionId
    }
    // The service is exported for Android media controls, but arbitrary apps must not
    // receive unrestricted access to the user's playback queue and local file URIs.
    // Media3 trusts our own UID, system controllers and user-approved media controllers.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.isTrusted) session else null
    /** Extra checkpoint when Android stops the service while the UI is closed. */
    private fun savePlaybackPosition() {
        val p = player ?: return
        if (p.mediaItemCount <= 0) return
        val queue = JSONArray()
        for (i in 0 until p.mediaItemCount) queue.put(p.getMediaItemAt(i).mediaId)
        getSharedPreferences("mark_music", MODE_PRIVATE).edit()
            .putString("last_queue", queue.toString())
            .putInt("last_index", p.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("last_position", p.currentPosition.coerceAtLeast(0L))
            .putBoolean("resume_playing", p.playWhenReady)
            .putInt("last_repeat", p.repeatMode)
            .apply()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackPosition()
        if (player?.isPlaying != true) stopSelf()
    }
    override fun onDestroy() {
        savePlaybackPosition()
        eqScope.cancel()
        eqEngine.release()
        AudioMeterBus.enabled.set(false)
        meterSink?.close()
        meterSink = null
        AudioMeterBus.clear()
        AudioSessionBus.sessionId.value = 0
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
