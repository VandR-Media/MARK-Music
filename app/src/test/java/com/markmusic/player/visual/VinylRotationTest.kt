package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylRotationTest {
    @Test fun advancesAt120HzRatherThanSkippingEveryOtherFrame() {
        val frame = 8_333_333L
        val first = advanceVinylRotation(0f, frame)
        val second = advanceVinylRotation(first, frame)
        assertEquals(1.6667f, first, .0002f)
        assertEquals(3.3333f, second, .0002f)
    }

    @Test fun oneSecondHasSameAngularSpeedAt60_90And120Hz() {
        for ((frameNs, frames) in listOf(
            16_666_666L to 60,
            11_111_111L to 90,
            8_333_333L to 120
        )) {
            var degrees = 0f
            repeat(frames) {
                degrees = advanceVinylRotation(degrees, frameNs)
            }
            assertEquals("Refresh rate: ${frames}Hz", 200f, degrees, .001f)
        }
    }

    @Test fun pausedOrInvalidElapsedTimeDoesNotMoveTheDisc() {
        assertEquals(145f, advanceVinylRotation(145f, 0L), 0f)
        assertEquals(145f, advanceVinylRotation(145f, -1L), 0f)
    }

    @Test fun wrapsAngleWithoutSnappingBackInTime() {
        val afterFrame = advanceVinylRotation(359f, 10_000_000L)
        assertEquals(1f, afterFrame, .00001f)
        assertTrue(afterFrame in 0f..360f)
    }

    @Test fun standard33AndOneThirdRpm() {
        // 200 degrees/s, so a complete revolution takes exactly 1.8 seconds.
        assertEquals(0f, advanceVinylRotation(0f, 1_800_000_000L), .00001f)
    }
}
