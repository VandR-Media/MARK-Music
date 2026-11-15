package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDeckModeTest {
    @Test fun defaultsToVinylWhenPreferenceNotSet() {
        assertEquals(PlayerDeckMode.VINYL,playerDeckFromPreference(null))
        assertEquals(PlayerDeckMode.VINYL,playerDeckFromPreference("OTHER"))
    }

    @Test fun savesAndRestoresAllFiveRemainingModes() {
        for (mode in PlayerDeckMode.entries)
            assertEquals(mode,playerDeckFromPreference(mode.name))
    }

    @Test fun retiredModesMigrateSafelyTo3dMark() {
        for (discarded in listOf("CD","MINIDISC","DAT","JUKEBOX"))
            assertEquals(PlayerDeckMode.MARK_3D,
                playerDeckFromPreference(discarded))
    }
}
