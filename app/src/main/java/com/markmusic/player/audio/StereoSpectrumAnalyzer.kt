package com.markmusic.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Six genuine, independent audio-frequency levels:
 * L: LOW/MID/HIGH and R: LOW/MID/HIGH.
 *
 * Audio source must be decoded PCM, not playback state or an artificial animation.
 * The caller owns PCM conversion, asynchronous dispatch, attack/release smoothing,
 * timestamping and routing to MARK Audio's visualizers.
 */
internal data class FrequencyBandLevels(
    val low: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f
)

internal data class StereoFrequencyBands(
    val left: FrequencyBandLevels = FrequencyBandLevels(),
    val right: FrequencyBandLevels = FrequencyBandLevels(),
    /** True after a full real PCM FFT window has been analyzed, including silence. */
    val ready: Boolean = false
)

/**
 * Reusable, thread-confined Hann-window FFT analyzer.
 *
 * Input: N consecutive, interleaved PCM Float samples in [-1, 1] per channel.
 * LOW = 20–200 Hz, MID = 200–2000 Hz, HIGH = 2000–16000 Hz (Nyquist-limited).
 * Numbers represent normalized relative linear spectral amplitudes, **not dBFS**.
 *
 * Must be called from an analyzer worker, never from ExoPlayer's audio callback.
 * Reuses preallocated FFT scratch arrays; supports mono and stereo sources.
 */
internal class StereoSpectrumAnalyzer(private val fftSize: Int = 2048) {
    init {
        require(fftSize in 512..8192 && (fftSize and (fftSize - 1)) == 0) {
            "FFT size must be a power of two from 512 to 8192"
        }
    }

    private val window = DoubleArray(fftSize) {
        0.5 - 0.5 * cos(2.0 * PI * it / (fftSize - 1))
    }
    private val normalizedAmplitude = 2.0 / window.sum()
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)

    fun analyzeInterleaved(
        pcm: FloatArray,
        channels: Int,
        sampleRateHz: Int
    ): StereoFrequencyBands {
        require(channels in 1..2) { "Expected mono or stereo decoded PCM" }
        require(sampleRateHz in 8000..192000) { "Invalid PCM sample rate" }
        require(pcm.size % channels == 0) { "Interleaved PCM is incomplete" }
        require(pcm.size / channels >= fftSize) {
            "Insufficient samples: need $fftSize frames per channel"
        }

        val offsetFrames = pcm.size / channels - fftSize
        val left = analyzeOneChannel(pcm, channels, offsetFrames, 0, sampleRateHz)
        val right = if (channels == 1) left
            else analyzeOneChannel(pcm, channels, offsetFrames, 1, sampleRateHz)
        return StereoFrequencyBands(left, right)
    }

    private fun analyzeOneChannel(
        pcm: FloatArray,
        channels: Int,
        firstFrame: Int,
        channel: Int,
        rate: Int
    ): FrequencyBandLevels {
        for (i in 0 until fftSize) {
            val value = pcm[(firstFrame + i) * channels + channel]
            real[i] = (if (value.isFinite()) value.toDouble().coerceIn(-1.0, 1.0)
                else 0.0) * window[i]
            imag[i] = 0.0
        }
        fftInPlace()

        var lowEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0
        val maxFrequency = minOf(16000.0, rate / 2.0)
        for (bin in 1..fftSize / 2) {
            val hz = bin * rate.toDouble() / fftSize
            if (hz < 20.0 || hz >= maxFrequency) continue
            val magnitude = hypot(real[bin], imag[bin]) * normalizedAmplitude
            val energy = magnitude * magnitude
            when {
                hz < 200.0 -> lowEnergy += energy
                hz < 2000.0 -> midEnergy += energy
                else -> highEnergy += energy
            }
        }
        fun level(energy: Double): Float = sqrt(energy).coerceIn(0.0, 1.0).toFloat()
        return FrequencyBandLevels(
            low = level(lowEnergy),
            mid = level(midEnergy),
            high = level(highEnergy)
        )
    }

    // Iterative in-place radix-2 FFT, O(N log N). Scratch buffers are reused.
    private fun fftInPlace() {
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val r = real[i]
                real[i] = real[j]
                real[j] = r
            }
        }
        var length = 2
        while (length <= fftSize) {
            val angle = -2.0 * PI / length
            val rotationReal = cos(angle)
            val rotationImag = sin(angle)
            for (start in 0 until fftSize step length) {
                var weightReal = 1.0
                var weightImag = 0.0
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val oddReal = real[odd] * weightReal - imag[odd] * weightImag
                    val oddImag = real[odd] * weightImag + imag[odd] * weightReal
                    val evenReal = real[even]
                    val evenImag = imag[even]
                    real[even] = evenReal + oddReal
                    imag[even] = evenImag + oddImag
                    real[odd] = evenReal - oddReal
                    imag[odd] = evenImag - oddImag
                    val nextReal = weightReal * rotationReal - weightImag * rotationImag
                    weightImag = weightReal * rotationImag + weightImag * rotationReal
                    weightReal = nextReal
                }
            }
            length = length shl 1
        }
    }
}
