package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDeckSwipeTest {
    @Test fun allFiveDecksAreReachableInBothDirections() {
        val modes = listOf(
            PlayerDeckMode.VINYL, PlayerDeckMode.MAYAK_203,
            PlayerDeckMode.VESNA_202_1, PlayerDeckMode.RADIO,
            PlayerDeckMode.STEREO_VU
        )
        assertEquals(modes, PlayerDeckMode.entries)
        var forward = PlayerDeckMode.VINYL
        repeat(modes.size) { i ->
            forward = playerDeckAfterSwipe(forward, -100f, 60f)
            assertEquals(modes[(i + 1) % modes.size], forward)
        }
        assertEquals(PlayerDeckMode.VINYL, forward)

        var backward = PlayerDeckMode.VINYL
        repeat(modes.size) { i ->
            backward = playerDeckAfterSwipe(backward, 100f, 60f)
            assertEquals(modes[(modes.size - i - 1) % modes.size], backward)
        }
        assertEquals(PlayerDeckMode.VINYL, backward)
    }

    @Test fun smallGesturesDoNotChangeVisualization() {
        for (mode in PlayerDeckMode.entries) {
            assertEquals(mode, playerDeckAfterSwipe(mode, 0f, 60f))
            assertEquals(mode, playerDeckAfterSwipe(mode, 59.9f, 60f))
            assertEquals(mode, playerDeckAfterSwipe(mode, -59.9f, 60f))
        }
    }

    @Test fun carouselWrapsFromStereoVuToVinyl() {
        assertEquals(PlayerDeckMode.VINYL,
            playerDeckAfterSwipe(PlayerDeckMode.STEREO_VU, -60f, 60f))
        assertEquals(PlayerDeckMode.STEREO_VU,
            playerDeckAfterSwipe(PlayerDeckMode.VINYL, 100f, 60f))
    }
}
