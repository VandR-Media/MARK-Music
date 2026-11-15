package com.markmusic.player.audio

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class MarkBeatDetectorTest {
    @Test fun actualBassTransientTriggersBeat() {
        val detector = PcmBeatDetector()
        assertEquals(0L, detector.update(0.002, 0L).index)
        val beat = detector.update(.29, 210_000_000L)
        assertEquals(1L, beat.index)
        assertTrue(beat.strength > .30f)
    }

    @Test fun cooldownPreventsRepeatedPulsesFromSustainedNotes() {
        val detector = PcmBeatDetector()
        assertEquals(1L, detector.update(.22, 500_000_000L).index)
        for (n in 1..5) {
            assertEquals(1L, detector.update(.28, 500_000_000L + n * 28_000_000L).index)
        }
        detector.update(.002, 900_000_000L)
        assertEquals(2L, detector.update(.29, 1_000_000_000L).index)
    }

    @Test fun silenceNeverCreatesFakeBeats() {
        val detector = PcmBeatDetector()
        repeat(100) {
            assertEquals(0L, detector.update(0.0, it * 30_000_000L).index)
        }
    }

    @Test fun lowFrequencyDetectorRespondsToBassAndDoesNotConsumeBuffer() {
        fun sine(freq: Double): ByteBuffer {
            val audio = ByteBuffer.allocate(2048 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(2048) { i ->
                val value = sin(i * 2.0 * PI * freq / 44100.0) * 20000.0
                audio.putShort(value.toInt().toShort())
            }
            audio.flip()
            return audio
        }
        val bass = sine(75.0)
        val treble = sine(6000.0)
        val bassRms = pcmLowFrequencyRms(bass, 1, C.ENCODING_PCM_16BIT, 44100)
        val highRms = pcmLowFrequencyRms(treble, 1, C.ENCODING_PCM_16BIT, 44100)
        assertTrue("Bass should drive dance onsets", bassRms > highRms * 3.0)
        assertEquals(0, bass.position())
        assertEquals(0, treble.position())
    }

    @Test fun resetClearsOldRhythmAndResumesCleanly() {
        val detector = PcmBeatDetector()
        assertEquals(1L, detector.update(.3, 1_000_000_000L).index)
        detector.reset()
        assertEquals(0L, detector.update(0.0, 2_000_000_000L).index)
    }
}
