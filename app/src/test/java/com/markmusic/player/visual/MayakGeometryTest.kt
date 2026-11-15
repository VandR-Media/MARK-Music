package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MayakGeometryTest {
    @Test fun onlyTwoRegisteredReelsExist() {
        assertEquals(2, MAYAK_REEL_SPECS.size)
        val left = MAYAK_REEL_SPECS[0]
        val right = MAYAK_REEL_SPECS[1]
        assertTrue(left.x < right.x)
        assertTrue(left.x + left.radius < right.x - right.radius + 10f)
    }

    @Test fun reelCropsFitTheStraightOnPhotograph() {
        for (spec in MAYAK_REEL_SPECS) {
            assertTrue(spec.x - spec.radius >= 0f)
            assertTrue(spec.x + spec.radius <= 1200f)
            assertTrue(spec.y - spec.radius >= 0f)
            assertTrue(spec.y + spec.radius <= 922f)
            assertEquals(6, spec.holes.size)
            for (hole in spec.holes) assertTrue(mayakHoleWithinReel(spec, hole))
        }
    }

    @Test fun geometricMaskCannotCoverControlsUnderTheReels() {
        // All stationary controls / model identity start below the reel panel.
        for (spec in MAYAK_REEL_SPECS) {
            assertTrue(spec.y + spec.radius < 525f)
        }
    }
}
