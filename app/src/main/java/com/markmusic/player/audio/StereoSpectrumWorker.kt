package com.markmusic.player.audio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real six-band PCM analyzer, isolated from ExoPlayer's real-time callback.
 * The audio thread only copies at most one 2048-frame chunk every ~33 ms.
 * When its single worker is busy the next chunk is simply dropped: audio must
 * always have priority over graphics.
 */
internal class StereoSpectrumWorker {
    private val analyzer = StereoSpectrumAnalyzer()
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mark-stereo-spectrum").apply { isDaemon = true }
    }

    fun submit(source: ByteBuffer, channels: Int, encoding: Int, sampleRate: Int) {
        if (closed.get() || !AudioMeterBus.enabled.get() || !busy.compareAndSet(false, true)) return
        val bytes = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> { busy.set(false); return }
        }
        if (channels !in 1..8 || sampleRate !in 8000..192000) {
            busy.set(false)
            return
        }
        val stride = channels * bytes
        val available = source.remaining() / stride
        // Only analyze a complete window. Never block or copy a huge buffer.
        if (available < 2048) { busy.set(false); return }
        val duplicate = source.duplicate()
        duplicate.position(source.position() + (available - 2048) * stride)
        val block = ByteArray(2048 * stride)
        duplicate.get(block)
        try {
            executor.execute {
                try {
                    val pcm = decodePcmForSpectrum(block, channels, encoding)
                    if (!closed.get() && AudioMeterBus.enabled.get()) {
                        AudioMeterBus.frequencyBands.value =
                            analyzer.analyzeInterleaved(pcm, minOf(channels, 2), sampleRate)
                    }
                } finally {
                    busy.set(false)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            busy.set(false)
        }
    }

    fun close() {
        closed.set(true)
        executor.shutdownNow()
        busy.set(false)
    }
}

/** Convert first L/R channels of decoded PCM to real float audio for FFT. */
internal fun decodePcmForSpectrum(
    buffer: ByteArray, channels: Int, encoding: Int
): FloatArray {
    require(channels in 1..8)
    val bps = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> throw IllegalArgumentException("Unsupported PCM encoding")
    }
    require(buffer.size % (channels * bps) == 0)
    val outputChannels = minOf(channels, 2)
    val frames = buffer.size / (channels * bps)
    val output = FloatArray(frames * outputChannels)
    val src = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
    for (frame in 0 until frames) for (ch in 0 until outputChannels) {
        val offset = (frame * channels + ch) * bps
        val sample = when (encoding) {
            C.ENCODING_PCM_8BIT ->
                ((src.get(offset).toInt() and 0xff) - 128) / 128f
            C.ENCODING_PCM_16BIT ->
                src.getShort(offset) / 32768f
            C.ENCODING_PCM_24BIT -> {
                val raw = (src.get(offset).toInt() and 0xff) or
                    ((src.get(offset+1).toInt() and 0xff) shl 8) or
                    (src.get(offset+2).toInt() shl 16)
                raw / 8388608f
            }
            C.ENCODING_PCM_32BIT ->
                src.getInt(offset) / 2147483648f
            C.ENCODING_PCM_FLOAT -> src.getFloat(offset)
            else -> 0f
        }
        output[frame * outputChannels + ch] =
            if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
    }
    return output
}
