package com.markmusic.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StereoPcmWindowTest {
    @Test fun shortAudioCallbacksAccumulateIntoOneValidFftWindow() {
        val buffer = StereoPcmWindow(2048)
        val leftHz = 100.0
        val rightHz = 6000.0
        // Real audio callbacks can deliver fewer than 2048 samples.
        repeat(8) { chunk ->
            val data = FloatArray(256 * 2)
            repeat(256) { n ->
                val t = chunk * 256 + n
                data[n * 2] = (.8 * sin(2.0 * PI * leftHz * t / 48000)).toFloat()
                data[n * 2 + 1] = (.7 * sin(2.0 * PI * rightHz * t / 48000)).toFloat()
            }
            buffer.append(data, 2, 48000)
            if (chunk < 7) assertFalse(buffer.full)
        }
        assertTrue(buffer.full)
        val bands = StereoSpectrumAnalyzer().analyzeInterleaved(
            buffer.snapshotStereo(), 2, 48000
        )
        assertTrue("Left LOW should now respond", bands.left.low > .55f)
        assertTrue("Right HIGH should now respond", bands.right.high > .50f)
        assertTrue("No false HIGH in left", bands.left.high < .03f)
        assertTrue("No false LOW in right", bands.right.low < .03f)
    }

    @Test fun circularWindowHoldsTheLatest2048FramesInOrder() {
        val window=StereoPcmWindow(2048)
        window.append(FloatArray(2048*2) { -1f }, 2, 48000)
        val updated=FloatArray(128 * 2) { 0.5f }
        window.append(updated, 2, 48000)
        val snapshot=window.snapshotStereo()
        for (i in 0 until (2048-128)*2) assertEquals(-1f, snapshot[i], 0f)
        for (i in (2048-128)*2 until snapshot.size) assertEquals(.5f, snapshot[i], 0f)
    }

    @Test fun monoIsMirroredWithNoInventedStereoValues() {
        val window=StereoPcmWindow(2048)
        window.append(FloatArray(2048) { it / 4096f }, 1, 44100)
        val pcm=window.snapshotStereo()
        for (i in 0 until 2048) assertEquals(pcm[2*i], pcm[2*i+1], 0f)
    }

    @Test fun changingSampleRateDiscardsPreviousFftWindow() {
        val window=StereoPcmWindow(2048)
        window.append(FloatArray(2048*2) { .25f }, 2, 44100)
        assertTrue(window.full)
        window.append(FloatArray(400*2) { -.5f }, 2, 48000)
        assertFalse("Must not combine different rates",window.full)
        window.append(FloatArray(1648*2) { -.5f }, 2, 48000)
        assertTrue(window.full)
        assertTrue(window.snapshotStereo().all { it == -.5f })
    }

    @Test fun resetAndNonFiniteSamplesRemainSafe() {
        val window=StereoPcmWindow(2048)
        window.append(FloatArray(2048*2) { Float.NaN }, 2, 48000)
        assertTrue(window.snapshotStereo().all { it == 0f })
        window.reset()
        assertFalse(window.full)
    }
}
