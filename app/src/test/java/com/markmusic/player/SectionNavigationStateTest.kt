package com.markmusic.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionNavigationStateTest {
    @Test fun backReturnsToPreviouslyVisitedSection() {
        val start = SectionNavigationState()
        val library = start.open(1)
        val playlists = library.open(3)
        assertEquals(3, playlists.current)
        assertEquals(1, playlists.back().current)
        assertEquals(0, playlists.back().back().current)
    }

    @Test fun openingTheCurrentSectionDoesNotDuplicateHistory() {
        val state = SectionNavigationState().open(1).open(1)
        assertEquals(listOf(0), state.history)
        assertEquals(0, state.back().current)
    }

    @Test fun playerBackReturnsToOrigin() {
        val playlistPlayer = SectionNavigationState().open(3).open(2)
        assertEquals(3, playlistPlayer.back().current)
    }

    @Test fun rootBackNeverProducesInvalidSection() {
        val state = SectionNavigationState()
        assertEquals(SectionNavigationState(), state.back())
    }
}
