package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MayakReelToReelTest {
    @Test fun sourceReelUnwindsWhileTakeUpReelFills() {
        assertEquals(.386f, mayakTapePackRadius(0f, false), .0001f)
        assertEquals(.145f, mayakTapePackRadius(0f, true), .0001f)
        assertEquals(.145f, mayakTapePackRadius(1f, false), .0001f)
        assertEquals(.386f, mayakTapePackRadius(1f, true), .0001f)
        assertEquals(mayakTapePackRadius(.5f, false),
            mayakTapePackRadius(.5f, true), .0001f)
    }

    @Test fun reelProgressIsClampedToTrackBounds() {
        for (takeup in listOf(true, false)) {
            assertEquals(mayakTapePackRadius(0f, takeup),
                mayakTapePackRadius(-10f, takeup), 0f)
            assertEquals(mayakTapePackRadius(1f, takeup),
                mayakTapePackRadius(10f, takeup), 0f)
        }
    }

    @Test fun tapeDirectionIsCounterClockwiseAndAnglesWrap() {
        val rotated = advanceMayakReel(0f, 16_666_667L, .5f, true)
        assertTrue(rotated > 350f && rotated < 360f)
        assertTrue(advanceMayakReel(rotated, 16_666_667L, .5f, true) < rotated)
    }

    @Test fun smallerTapePackRotatesFaster() {
        val startSupply = advanceMayakReel(0f, 16_666_667L, 0f, false)
        val startTakeup = advanceMayakReel(0f, 16_666_667L, 0f, true)
        assertTrue(360f - startTakeup > 360f - startSupply)
    }

    @Test fun angularTravelIsFrameRateIndependent() {
        val frames = listOf(60, 90, 120)
        val angles = frames.map { fps ->
            val dt = 1_000_000_000L / fps
            var degrees = 0f
            repeat(fps) {
                degrees = advanceMayakReel(degrees, dt, .25f, false)
            }
            degrees
        }
        assertEquals(angles[0], angles[1], .02f)
        assertEquals(angles[1], angles[2], .02f)
    }

    @Test fun zeroOrNegativeFrameTimeDoesNotChangePausedAngle() {
        assertEquals(113f, advanceMayakReel(113f, 0L, .7f, false), 0f)
        assertEquals(113f, advanceMayakReel(113f, -1L, .7f, false), 0f)
    }
}
