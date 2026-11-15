package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkDancer3DTest {
    @Test fun noRhythmRemainsAtRest() {
        assertEquals(0f, danceImpulse(0f, .03f, 0f), 0f)
        assertEquals(0f, danceMovement(1f, 0f, 0f, 0f), 0f)
    }

    @Test fun aRealBeatMovesTheDancerAndFadesNaturally() {
        val hit = danceImpulse(0f, .016f, .8f)
        assertEquals(.8f, hit, .00001f)
        val after = danceImpulse(hit, .09f, 0f)
        assertTrue(after > 0f && after < hit)
        val later = danceImpulse(after, .28f, 0f)
        assertTrue(later < after)
    }

    @Test fun movementReadsPCMLevelsAndPlaybackEnvelope() {
        assertEquals(0f, danceMovement(0f, 1f, .9f, .9f), 0f)
        assertTrue(danceMovement(1f, .8f, .5f, .5f) > .5f)
        assertTrue(danceMovement(1f, .8f, .9f, .9f) >
            danceMovement(1f, .8f, .1f, .1f))
    }

    @Test fun impossibleOrCorruptLevelsRemainSafe() {
        assertEquals(1f, danceImpulse(2f, 0f, 5f), 0f)
        assertTrue(danceMovement(5f, 5f, 4f, 4f) in 0f..1f)
    }
}
