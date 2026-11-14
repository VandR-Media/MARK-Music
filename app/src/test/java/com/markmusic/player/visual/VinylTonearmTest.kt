package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class VinylTonearmTest {
    private val platterX = .435f
    private val platterY = .50f

    private fun grooveRadius(progress: Float): Float {
        val (x, y) = stylusOnRecord(progress)
        return hypot(x - platterX, y - platterY)
    }

    @Test fun movesFromLeadInTowardInnerGroove() {
        val first = grooveRadius(0f)
        val middle = grooveRadius(.5f)
        val last = grooveRadius(1f)
        assertTrue(first > middle)
        assertTrue(middle > last)
        assertTrue(first in .33f.. .38f)
        assertTrue(last in .14f.. .19f)
    }

    @Test fun remainsOnOneFixedPivotRadius() {
        for (step in 0..20) {
            val (x, y) = stylusOnRecord(step / 20f)
            assertEquals(.43f, hypot(x - .882f, y - .19f), .0001f)
        }
    }

    @Test fun playbackProgressIsClamped() {
        assertEquals(stylusOnRecord(0f), stylusOnRecord(-1f))
        assertEquals(stylusOnRecord(1f), stylusOnRecord(2f))
    }
}
