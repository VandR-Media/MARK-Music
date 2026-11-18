package com.markmusic.player.audio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Collect actual PCM frames across arbitrary ExoPlayer callbacks. A callback
 * need not contain a complete 2048-frame FFT window.
 *
 * Worker thread owns this ring; no locking or FFT is performed in the audio
 * callback. Mono is mirrored into L/R as the original decoded PCM demands.
 */
internal class StereoPcmWindow(private val frameCapacity: Int = 2048) {
    init { require(frameCapacity > 0) }
    private val ring = FloatArray(frameCapacity * 2)
    private var next = 0
    private var count = 0
    private var sampleRate = 0

    val full: Boolean get() = count == frameCapacity
    fun reset() {
        next = 0
        count = 0
        sampleRate = 0
        ring.fill(0f)
    }

    fun append(pcm: FloatArray, channels: Int, rateHz: Int) {
        require(channels in 1..2)
        require(rateHz in 8000..192000)
        require(pcm.size % channels == 0)
        if (sampleRate != rateHz) reset()
        sampleRate = rateHz
        val frames = pcm.size / channels
        for (i in 0 until frames) {
            val offset = i * channels
            val left = pcm[offset].takeIf { it.isFinite() } ?: 0f
            val right = if (channels == 1) left
                else (pcm[offset + 1].takeIf { it.isFinite() } ?: 0f)
            ring[next * 2] = left
            ring[next * 2 + 1] = right
            next = (next + 1) % frameCapacity
            if (count < frameCapacity) count++
        }
    }

    fun snapshotStereo(): FloatArray {
        require(full) { "Cannot analyze incomplete PCM window" }
        val result = FloatArray(frameCapacity * 2)
        for (i in 0 until frameCapacity) {
            val source = ((next + i) % frameCapacity) * 2
            result[i * 2] = ring[source]
            result[i * 2 + 1] = ring[source + 1]
        }
        return result
    }
}

/**
 * Worker handles only one outstanding short PCM chunk, dropping visual updates
 * if CPU is overloaded. No FFT, blocking wait, or disk I/O on audio callback.
 */
internal class StereoSpectrumWorker {
    private val analyzer = StereoSpectrumAnalyzer()
    private val window = StereoPcmWindow(2048)
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private var workerGeneration = -1
    private var lastPublishNs = 0L
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mark-stereo-spectrum").apply { isDaemon = true }
    }

    fun reset() {
        generation.incrementAndGet()
        AudioMeterBus.frequencyBands.value = StereoFrequencyBands()
    }

    fun submit(source: ByteBuffer, channels: Int, encoding: Int, sampleRate: Int) {
        if (closed.get() || !AudioMeterBus.enabled.get() ||
            !busy.compareAndSet(false, true)) return
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
        if (available <= 0) { busy.set(false); return }
        // Copy the newest portion of the PCM callback, at most 2048 frames.
        val frames = minOf(available, 2048)
        val duplicate = source.duplicate()
        duplicate.position(source.position() + (available - frames) * stride)
        val block = ByteArray(frames * stride)
        duplicate.get(block)
        val session = generation.get()
        try {
            executor.execute {
                try {
                    if (closed.get() || !AudioMeterBus.enabled.get()) return@execute
                    if (session != generation.get()) return@execute
                    if (workerGeneration != session) {
                        workerGeneration = session
                        window.reset()
                        lastPublishNs = 0L
                    }
                    val pcm = decodePcmForSpectrum(block, channels, encoding)
                    window.append(pcm, minOf(channels, 2), sampleRate)
                    if (!window.full) return@execute

                    val nowNs = System.nanoTime()
                    if (lastPublishNs != 0L && nowNs - lastPublishNs < 33_000_000L) return@execute
                    lastPublishNs = nowNs
                    val bands = analyzer.analyzeInterleaved(
                        window.snapshotStereo(), 2, sampleRate
                    )
                    if (!closed.get() && AudioMeterBus.enabled.get() &&
                        session == generation.get()) {
                        AudioMeterBus.frequencyBands.value = bands.copy(ready = true)
                    }
                } finally {
                    busy.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            busy.set(false)
        }
    }

    fun close() {
        closed.set(true)
        generation.incrementAndGet()
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
