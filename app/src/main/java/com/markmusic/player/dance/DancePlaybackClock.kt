package com.markmusic.player.dance

import kotlin.math.roundToLong

/** A monotonic, media-position-based timeline; never driven by rendered frame count. */
data class PlaybackAnchor(
    val mediaId: String,
    val positionMs: Long,
    val sampledAtNs: Long,
    val speed: Float,
    val playing: Boolean
)

class DancePlaybackClock {
    @Volatile private var anchor = PlaybackAnchor("", 0L, 0L, 1f, false)

    fun update(mediaId: String, positionMs: Long, nowNs: Long, speed: Float, playing: Boolean) {
        require(positionMs >= 0 && nowNs >= 0 && speed.isFinite() && speed > 0f)
        anchor = PlaybackAnchor(mediaId, positionMs, nowNs, speed, playing)
    }

    fun snapshot(): PlaybackAnchor = anchor

    fun positionAt(nowNs: Long): Long {
        val a = anchor
        if (!a.playing) return a.positionMs
        val elapsed = ((nowNs - a.sampledAtNs).coerceAtLeast(0L) / 1_000_000.0) * a.speed
        return (a.positionMs + elapsed.roundToLong()).coerceAtLeast(0L)
    }

    /** Animation position is anchored to song time, including after seeks and rate changes. */
    fun phaseAt(nowNs: Long, bpm: Double, firstBeatMs: Long): Double {
        require(bpm.isFinite() && bpm > 0)
        val beats = (positionAt(nowNs) - firstBeatMs) * bpm / 60_000.0
        return ((beats % 1.0) + 1.0) % 1.0
    }
}
