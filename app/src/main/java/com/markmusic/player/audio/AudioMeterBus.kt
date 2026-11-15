package com.markmusic.player.audio

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Small read-only meter snapshot. These values are based on decoded PCM samples,
 * not generated waves and not "play is true" random movement. No RECORD_AUDIO or
 * Android Visualizer audio capture is needed.
 */
data class AudioMeterFrame(
    val left: Float = 0f,
    val right: Float = 0f,
    val peakLeft: Float = 0f,
    val peakRight: Float = 0f
)

object AudioMeterBus {
    val enabled = AtomicBoolean(false)
    val levels = MutableStateFlow(AudioMeterFrame())

    fun clear() { levels.value = AudioMeterFrame() }
}

/** Convert a normalized PCM RMS to a 0..1 level on a -48..0 dB scale. */
internal fun normalizeMeterRms(rms: Double): Float {
    if (!rms.isFinite() || rms <= 0.0) return 0f
    return ((20.0 * log10(rms.coerceAtLeast(0.000001)) + 48.0) / 48.0)
        .coerceIn(0.0, 1.0).toFloat()
}

internal data class ChannelStats(val rms: Float, val peak: Float)

/** Unit-testable channel analysis; reads only a duplicate of the audio buffer. */
internal fun pcmStats(
    source: ByteBuffer,
    channels: Int,
    encoding: Int,
    maxFrames: Int = 2048
): Pair<ChannelStats, ChannelStats> {
    val bytesPerSample = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> return ChannelStats(0f, 0f) to ChannelStats(0f, 0f)
    }
    if (channels !in 1..8) return ChannelStats(0f, 0f) to ChannelStats(0f, 0f)
    val data = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val frameBytes = channels * bytesPerSample
    val frames = data.remaining() / frameBytes
    if (frames == 0) return ChannelStats(0f, 0f) to ChannelStats(0f, 0f)
    val skip = max(1, frames / maxFrames.coerceAtLeast(1))
    val start = data.position()
    var sumLeft = 0.0
    var sumRight = 0.0
    var peakLeft = 0.0
    var peakRight = 0.0
    var count = 0
    fun sample(byteOffset: Int): Double {
        return when (encoding) {
            C.ENCODING_PCM_8BIT -> (data.get(byteOffset).toInt() and 0xff).let { (it - 128) / 128.0 }
            C.ENCODING_PCM_16BIT -> data.getShort(byteOffset) / 32768.0
            C.ENCODING_PCM_24BIT -> {
                val raw = (data.get(byteOffset).toInt() and 0xff) or
                    ((data.get(byteOffset + 1).toInt() and 0xff) shl 8) or
                    (data.get(byteOffset + 2).toInt() shl 16)
                raw / 8388608.0
            }
            C.ENCODING_PCM_32BIT -> data.getInt(byteOffset) / 2147483648.0
            C.ENCODING_PCM_FLOAT -> data.getFloat(byteOffset).toDouble().let {
                if (it.isFinite()) it.coerceIn(-1.0, 1.0) else 0.0
            }
            else -> 0.0
        }
    }
    for (i in 0 until frames step skip) {
        val offset = start + i * frameBytes
        val l = sample(offset)
        val r = if (channels > 1) sample(offset + bytesPerSample) else l
        sumLeft += l * l
        sumRight += r * r
        peakLeft = max(peakLeft, kotlin.math.abs(l))
        peakRight = max(peakRight, kotlin.math.abs(r))
        count++
    }
    return ChannelStats(sqrt(sumLeft / count).toFloat(), peakLeft.toFloat()) to
        ChannelStats(sqrt(sumRight / count).toFloat(), peakRight.toFloat())
}

/**
 * Installs into the PCM chain of the existing ExoPlayer audio sink. Samples at
 * most 2048 stereo frames 25-30 times a second while a modern deck is visible.
 * Calls are on ExoPlayer's audio thread, so work is intentionally bounded.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MeterAudioBufferSink : TeeAudioProcessor.AudioBufferSink {
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var lastNs = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        lastNs = 0L
        AudioMeterBus.clear()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!AudioMeterBus.enabled.get()) return
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - lastNs < 33_000_000L) return // <= 30 meter updates/s
        lastNs = now
        val (left, right) = pcmStats(buffer, channels, encoding)
        AudioMeterBus.levels.value = AudioMeterFrame(
            left = normalizeMeterRms(left.rms.toDouble()),
            right = normalizeMeterRms(right.rms.toDouble()),
            peakLeft = normalizeMeterRms(left.peak.toDouble()),
            peakRight = normalizeMeterRms(right.peak.toDouble())
        )
    }
}
