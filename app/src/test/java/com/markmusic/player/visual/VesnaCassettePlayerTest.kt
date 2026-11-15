package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VesnaCassettePlayerTest {
    @Test fun hubsStayInsideThePhotographedCassetteNotOnThePlayerBody() {
        assertEquals(2, VESNA_CASSETTE_HUBS.size)
        assertTrue(VESNA_CASSETTE_HUBS[0].x < VESNA_CASSETTE_HUBS[1].x)
        for (hub in VESNA_CASSETTE_HUBS) {
            assertTrue(cassetteHubInsideCassetteWindow(hub))
            assertTrue(hub.x - hub.radius > 0f)
            assertTrue(hub.x + hub.radius < VESNA_IMAGE_WIDTH)
            assertTrue(hub.y - hub.radius > 0f)
            assertTrue(hub.y + hub.radius < VESNA_IMAGE_HEIGHT)
        }
    }

    @Test fun theTapeWindsFromLeftToRight() {
        assertEquals(24.0, cassetteTapePackRadiusMm(0f, false), .000001)
        assertEquals(12.0, cassetteTapePackRadiusMm(0f, true), .000001)
        assertEquals(12.0, cassetteTapePackRadiusMm(1f, false), .000001)
        assertEquals(24.0, cassetteTapePackRadiusMm(1f, true), .000001)
        assertEquals(cassetteTapePackRadiusMm(.5f, false),
            cassetteTapePackRadiusMm(.5f, true), .000001)
    }

    @Test fun playingCassetteRotatesBothHubsClockwise() {
        for (takeup in listOf(false, true)) {
            val next = advanceCassetteHub(0f, 8_333_333L, .50f, takeup)
            assertTrue(next > .5f)
            assertTrue(next < 3.0f)
        }
    }

    @Test fun emptyHubRotatesFasterThanFullHub() {
        val left = advanceCassetteHub(0f, 16_666_666L, 0f, false)
        val right = advanceCassetteHub(0f, 16_666_666L, 0f, true)
        assertTrue(right > left)
    }

    @Test fun motionIsConsistentAcross60_90_And120HzDisplays() {
        val results = listOf(60, 90, 120).map { hz ->
            val dt = 1_000_000_000L / hz
            var degrees = 0f
            repeat(hz) {
                degrees = advanceCassetteHub(degrees, dt, .4f, true)
            }
            degrees
        }
        assertEquals(results[0], results[1], .02f)
        assertEquals(results[1], results[2], .02f)
    }

    @Test fun pausedOrInvalidFrameDoesNotMoveHub() {
        assertEquals(65f, advanceCassetteHub(65f, 0L, .4f, true), 0f)
        assertEquals(65f, advanceCassetteHub(65f, -10L, .4f, true), 0f)
    }

    @Test fun twoSecondSeekOutOfRangeDoesNotExceedTapeRadius() {
        assertEquals(cassetteTapePackRadiusMm(0f, true),
            cassetteTapePackRadiusMm(-200f, true), 0.0)
        assertEquals(cassetteTapePackRadiusMm(1f, false),
            cassetteTapePackRadiusMm(300f, false), 0.0)
    }
}
