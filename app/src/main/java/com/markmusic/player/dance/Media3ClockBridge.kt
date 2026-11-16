package com.markmusic.player.dance

import android.os.SystemClock
import androidx.media3.common.Player

/** Install on the player application thread. refresh() may also correct interpolation drift. */
class Media3ClockBridge(private val player: Player, val clock: DancePlaybackClock) :
    Player.Listener {
    fun refresh() {
        val id = if (player.mediaItemCount > 0) player.currentMediaItem?.mediaId.orEmpty() else ""
        clock.update(
            id, player.currentPosition.coerceAtLeast(0L),
            SystemClock.elapsedRealtimeNanos(),
            player.playbackParameters.speed,
            player.isPlaying
        )
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
            events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
            events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED) ||
            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
            events.contains(Player.EVENT_TIMELINE_CHANGED)) refresh()
    }
}
