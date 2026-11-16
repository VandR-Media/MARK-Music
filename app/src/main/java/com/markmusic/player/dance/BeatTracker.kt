package com.markmusic.player.dance

import kotlin.math.abs

data class BeatGrid(
    val bpm: Double,
    val firstBeatMs: Long,
    val confidence: Double,
    val eventCount: Int
)

/** Lightweight onset tracker. Feed actual media timestamps, not audio callback wall clock. */
class BeatTracker(private val minIntervalMs: Long = 260L, private val maxIntervalMs: Long = 1000L) {
    private val onsets = ArrayDeque<Long>()
    private var lastOnset: Long? = null

    fun reset() {
        onsets.clear()
        lastOnset = null
    }

    /** A seek or track change must call reset before accepting new events. */
    fun observe(positionMs: Long, strength: Float): BeatGrid? {
        if (positionMs < 0 || !strength.isFinite() || strength < .25f) return estimate()
        val last = lastOnset
        if (last != null && positionMs <= last) {
            if (positionMs < last) reset() else return estimate()
        }
        val previous = lastOnset
        if (previous != null && positionMs - previous < minIntervalMs) return estimate()
        lastOnset = positionMs
        onsets.addLast(positionMs)
        while (onsets.size > 24) onsets.removeFirst()
        return estimate()
    }

    fun estimate(): BeatGrid? {
        if (onsets.size < 4) return null
        val times = onsets.toList()
        val intervals = times.zipWithNext { a, b -> b - a }
            .filter { it in minIntervalMs..maxIntervalMs }
        if (intervals.size < 3) return null
        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2].toDouble()
        val stable = intervals.count { abs(it - median) <= median * .12 }
        val confidence = stable.toDouble() / intervals.size
        if (confidence < .60) return null
        val bpm = 60_000.0 / median
        // The origin is a real detected onset. Do not infer an offset of zero.
        return BeatGrid(bpm, times.first(), confidence, times.size)
    }
}
