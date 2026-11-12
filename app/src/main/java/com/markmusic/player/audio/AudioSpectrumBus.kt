package com.markmusic.player.audio

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-process PCM spectrum; no microphone, RECORD_AUDIO, Internet or system-wide capture.
 * A lightweight 256-sample Fourier analysis is throttled to avoid blocking audio playback.
 */
internal object AudioSpectrumBus : TeeAudioProcessor.AudioBufferSink {
    val spectrum = MutableStateFlow(List(64) { 0f })
    @Volatile private var channels = 2
    @Volatile private var encoding = C.ENCODING_PCM_16BIT
    @Volatile private var lastPublishNs = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        lastPublishNs = 0L
        spectrum.value = List(64) { 0f }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (encoding != C.ENCODING_PCM_16BIT) return
        val now = System.nanoTime()
        if (now - lastPublishNs < 65_000_000L) return
        lastPublishNs = now
        val copy = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        val frameBytes = 2 * channels
        val frames = (copy.remaining() / frameBytes).coerceAtMost(256)
        if (frames < 128) return
        val samples = FloatArray(256)
        val start = copy.position()
        for (i in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channels) sum += copy.getShort(start + i * frameBytes + 2 * ch) / 32768f
            samples[i] = (sum / channels) * (0.5f - 0.5f * cos(2.0 * PI * i / 255)).toFloat()
        }
        // 64 frequency bands from the decoded PCM, not from microphone audio.
        val values = FloatArray(64)
        for (bin in 0 until 64) {
            val k = bin + 1
            var re = 0.0
            var im = 0.0
            val step = 2.0 * PI * k / 256.0
            for (i in 0 until 256) {
                val phase = step * i
                re += samples[i] * cos(phase)
                im -= samples[i] * sin(phase)
            }
            values[bin] = (sqrt(re * re + im * im) / 16.0).toFloat().coerceIn(0f, 1f)
        }
        spectrum.value = values.toList()
    }
}
