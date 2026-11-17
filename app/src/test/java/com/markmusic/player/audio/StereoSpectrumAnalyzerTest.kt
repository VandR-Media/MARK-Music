package com.markmusic.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StereoSpectrumAnalyzerTest {
    private fun sine(
        sampleRate: Int = 48000,
        leftHz: Double = 90.0,
        rightHz: Double = 6000.0,
        leftGain: Double = .75,
        rightGain: Double = .75,
        frames: Int = 2048
    ): FloatArray {
        val result = FloatArray(frames * 2)
        for (i in 0 until frames) {
            result[i * 2] =
                (leftGain * sin(2.0 * PI * leftHz * i / sampleRate)).toFloat()
            result[i * 2 + 1] =
                (rightGain * sin(2.0 * PI * rightHz * i / sampleRate)).toFloat()
        }
        return result
    }

    @Test fun leftLowAndRightHighAreIndependent() {
        val bands = StereoSpectrumAnalyzer().analyzeInterleaved(
            sine(leftHz = 90.0, rightHz = 6000.0), 2, 48000
        )
        assertTrue("Left low should respond", bands.left.low > .55f)
        assertTrue("Right high should respond", bands.right.high > .55f)
        assertTrue("Left high should be quiet", bands.left.high < .04f)
        assertTrue("Right low should be quiet", bands.right.low < .04f)
        assertTrue("Left mid should be quiet", bands.left.mid < .07f)
        assertTrue("Right mid should be quiet", bands.right.mid < .07f)
    }

    @Test fun middleFrequenciesAreInMiddleBandFor44100And48000() {
        for (rate in listOf(44100, 48000)) {
            val bands = StereoSpectrumAnalyzer().analyzeInterleaved(
                sine(sampleRate = rate, leftHz = 850.0, rightHz = 1200.0),
                2, rate
            )
            assertTrue("Left mid @ $rate", bands.left.mid > .55f)
            assertTrue("Right mid @ $rate", bands.right.mid > .55f)
            assertTrue(bands.left.low < .03f)
            assertTrue(bands.right.high < .03f)
        }
    }

    @Test fun monoDuplicatesRealBandsWithoutSynthesizingStereo() {
        val mono = FloatArray(2048) {
            (.6 * sin(2.0 * PI * 160.0 * it / 48000)).toFloat()
        }
        val bands = StereoSpectrumAnalyzer().analyzeInterleaved(mono, 1, 48000)
        assertEquals(bands.left, bands.right)
        assertTrue(bands.left.low > .35f)
    }

    @Test fun silenceIsSilentAndNonFiniteSamplesAreSanitized() {
        val analyzer = StereoSpectrumAnalyzer()
        val silence = analyzer.analyzeInterleaved(FloatArray(4096), 2, 48000)
        assertEquals(StereoFrequencyBands(), silence)
        val withInvalidValues = FloatArray(4096)
        withInvalidValues[0] = Float.NaN
        withInvalidValues[1] = Float.POSITIVE_INFINITY
        assertEquals(StereoFrequencyBands(),
            analyzer.analyzeInterleaved(withInvalidValues, 2, 48000))
    }

    @Test fun corruptedFormatIsRejectedRatherThanProducingFalseLevels() {
        val analyzer = StereoSpectrumAnalyzer()
        try {
            analyzer.analyzeInterleaved(FloatArray(50), 2, 48000)
            throw AssertionError("Short FFT window should fail")
        } catch (_: IllegalArgumentException) { }
        try {
            analyzer.analyzeInterleaved(FloatArray(4096), 2, 0)
            throw AssertionError("Bad sample rate should fail")
        } catch (_: IllegalArgumentException) { }
        try {
            StereoSpectrumAnalyzer(1000)
            throw AssertionError("Non-power-of-2 FFT should fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun latestWindowIsAnalyzedAfterSilentPreviousFrames() {
        val previous = FloatArray(4096)
        val latest = sine(leftHz = 120.0, rightHz = 6000.0)
        val combined = previous + latest
        val bands = StereoSpectrumAnalyzer().analyzeInterleaved(combined, 2, 48000)
        assertTrue(bands.left.low > .55f)
        assertTrue(bands.right.high > .55f)
    }
}
