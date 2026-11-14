package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDeckSwipeTest {
    @Test fun swipeLeftSwitchesBetweenPhotoMayakAndVinyl() {
        assertEquals(PlayerDeckMode.MAYAK_203,
            playerDeckAfterSwipe(PlayerDeckMode.VINYL, -100f, 60f))
        assertEquals(PlayerDeckMode.VINYL,
            playerDeckAfterSwipe(PlayerDeckMode.MAYAK_203, -100f, 60f))
    }

    @Test fun swipeRightAlsoSwitchesBetweenTheTwoVisualizations() {
        assertEquals(PlayerDeckMode.MAYAK_203,
            playerDeckAfterSwipe(PlayerDeckMode.VINYL, 100f, 60f))
        assertEquals(PlayerDeckMode.VINYL,
            playerDeckAfterSwipe(PlayerDeckMode.MAYAK_203, 100f, 60f))
    }

    @Test fun tinyDragOrTapNeverChangesVisualization() {
        for (mode in PlayerDeckMode.entries) {
            assertEquals(mode, playerDeckAfterSwipe(mode, 0f, 60f))
            assertEquals(mode, playerDeckAfterSwipe(mode, 59.9f, 60f))
            assertEquals(mode, playerDeckAfterSwipe(mode, -59.9f, 60f))
        }
    }

    @Test fun exactlyAtThresholdChangesVisualization() {
        assertEquals(PlayerDeckMode.MAYAK_203,
            playerDeckAfterSwipe(PlayerDeckMode.VINYL, -60f, 60f))
        assertEquals(PlayerDeckMode.MAYAK_203,
            playerDeckAfterSwipe(PlayerDeckMode.VINYL, 60f, 60f))
    }
}
