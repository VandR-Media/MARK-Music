package com.markmusic.player.dance

import org.junit.Assert.*
import org.junit.Test

class DanceEngineTest {
    @Test fun playbackPauseSeekAndSpeed() {
        val clock = DancePlaybackClock()
        clock.update("a", 1000, 1_000_000_000, 1f, true)
        assertEquals(1500L, clock.positionAt(1_500_000_000))
        clock.update("a", 1500, 1_500_000_000, 1f, false)
        assertEquals(1500L, clock.positionAt(8_000_000_000))
        clock.update("a", 8000, 8_000_000_000, 1f, true)
        assertEquals(8100L, clock.positionAt(8_100_000_000))
        clock.update("b", 0, 9_000_000_000, 2f, true)
        assertEquals(1000L, clock.positionAt(9_500_000_000))
        assertEquals(0.5, clock.phaseAt(9_125_000_000, 120.0, 0), 0.001)
    }

    @Test fun trackerFindsActualTempoAndResets() {
        val tracker = BeatTracker()
        for (n in 0..10) tracker.observe(210L + n * 500L, .9f)
        assertEquals(120.0, tracker.estimate()!!.bpm, .01)
        assertEquals(210L, tracker.estimate()!!.firstBeatMs)
        tracker.reset()
        assertNull(tracker.estimate())
        for (n in 0..8) tracker.observe(300L + n * 400L, .8f)
        assertEquals(150.0, tracker.estimate()!!.bpm, .01)
    }

    @Test fun directorUsesNeutralTransitionsAndResetsOnTrackChange() {
        val clips = listOf(
            DanceClip("neutral", "neutral", 2, "stand", "stand", 0f, true),
            DanceClip("a", "hiphop", 4, "stand", "left", .5f),
            DanceClip("b", "funk", 4, "stand", "stand", .8f)
        )
        val director = DanceDirector(clips, "neutral")
        director.requestStyle("hiphop")
        assertEquals("a", director.decide("song", true, 0, .5f).clipId)
        director.requestStyle("funk")
        assertEquals("a", director.decide("song", true, 2, .8f).clipId)
        assertEquals(DanceMode.TRANSITION, director.decide("song", true, 4, .8f).mode)
        assertEquals("b", director.decide("song", true, 6, .8f).clipId)
        assertEquals(DanceMode.IDLE, director.decide("song", false, 7, .1f).mode)
        assertEquals(DanceMode.DANCING, director.decide("new song", true, 0, .1f).mode)
    }

    @Test fun glbRejectsInvalidContainers() {
        assertFalse(GlbAssetValidator.validate(byteArrayOf(1, 2, 3)))
        val b = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x46546C67).putInt(2).putInt(20).putInt(0).putInt(0x4E4F534A)
        assertFalse(GlbAssetValidator.validate(b.array()))
    }
}
