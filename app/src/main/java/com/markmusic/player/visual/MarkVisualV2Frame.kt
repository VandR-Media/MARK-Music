package com.markmusic.player.visual

import com.markmusic.player.audio.AudioMeterFrame
import com.markmusic.player.audio.StereoFrequencyBands
import com.markmusic.player.audio.normalizeMeterRms
import java.util.Locale

/**
 * Strict MARK Audio -> Studio v2 stereo frame contract.
 * The values come from ExoPlayer's existing PCM TeeAudioProcessor and
 * StereoSpectrumWorker, never a microphone, random demo or second decoder.
 *
 * The current Android bus has no 128-sample waveform. Explicit zeros signal
 * unavailable waveforms and must not be described as measured oscillograms.
 */
internal object MarkVisualV2Frame {
    private fun level(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    private fun f(value: Float): String =
        String.format(Locale.US, "%.6f", level(value))
    private val emptyWaveform = List(128) { "0" }.joinToString(",")
    private fun channel(
        rms: Float, peak: Float,
        low: Float, mid: Float, high: Float
    ) = """{"rms":${f(rms)},"peak":${f(peak)},
        "low":${f(low)},"mid":${f(mid)},"high":${f(high)},
        "waveform":[${emptyWaveform}]}""".trimIndent().replace("\n", "")

    fun serialize(
        levels: AudioMeterFrame,
        bands: StereoFrequencyBands,
        playing: Boolean,
        timestampMs: Long
    ): String {
        val ready = playing && bands.ready
        val left = channel(
            if (playing) levels.left else 0f,
            if (playing) levels.peakLeft else 0f,
            if (ready) normalizeMeterRms(bands.left.low.toDouble()) else 0f,
            if (ready) normalizeMeterRms(bands.left.mid.toDouble()) else 0f,
            if (ready) normalizeMeterRms(bands.left.high.toDouble()) else 0f
        )
        val right = channel(
            if (playing) levels.right else 0f,
            if (playing) levels.peakRight else 0f,
            if (ready) normalizeMeterRms(bands.right.low.toDouble()) else 0f,
            if (ready) normalizeMeterRms(bands.right.mid.toDouble()) else 0f,
            if (ready) normalizeMeterRms(bands.right.high.toDouble()) else 0f
        )
        return """{"schema":"mark.audio.stereo/1",
            "timestampMs":${timestampMs.coerceAtLeast(0)},
            "source":"${if (playing) "pcm" else "silence"}",
            "playing":${playing},"left":${left},"right":${right},
            "beat":${f(if (playing) levels.beatStrength else 0f)},
            "bass":${f(if (playing) levels.bassLevel else 0f)}}"""
            .trimIndent().replace("\n", "")
    }
}
