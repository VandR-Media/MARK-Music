package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDeckModeTest {
    @Test fun defaultsToVinylWhenPreferenceNotSet() {
        assertEquals(PlayerDeckMode.VINYL,playerDeckFromPreference(null))
        assertEquals(PlayerDeckMode.VINYL,playerDeckFromPreference("OTHER"))
    }

    @Test fun savesAndRestoresAllSevenModes() {
        for (mode in PlayerDeckMode.entries)
            assertEquals(mode,playerDeckFromPreference(mode.name))
    }

    @Test fun retiredModesMigrateSafelyToVinyl() {
        for (discarded in listOf("CD","MINIDISC","DAT","JUKEBOX","MARK_3D"))
            assertEquals(PlayerDeckMode.VINYL,
                playerDeckFromPreference(discarded))
    }
}
