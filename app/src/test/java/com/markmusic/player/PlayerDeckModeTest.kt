package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDeckModeTest {
    @Test fun firstRunDefaultsToVinyl() {
        assertEquals(PlayerDeckMode.VINYL, playerDeckFromPreference(null))
        assertEquals(PlayerDeckMode.VINYL, playerDeckFromPreference("UNKNOWN"))
    }

    @Test fun savedMayakChoiceIsRestored() {
        assertEquals(PlayerDeckMode.MAYAK_203,
            playerDeckFromPreference(PlayerDeckMode.MAYAK_203.name))
    }

    @Test fun savedVinylChoiceIsRestored() {
        assertEquals(PlayerDeckMode.VINYL,
            playerDeckFromPreference(PlayerDeckMode.VINYL.name))
    }
}
