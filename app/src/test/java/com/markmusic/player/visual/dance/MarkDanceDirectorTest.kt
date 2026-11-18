package com.markmusic.player.visual.dance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkDanceDirectorTest {
    @Test fun catalogHasTwentyDistinctDanceFamilies() {
        assertEquals(20, DanceStyle.entries.size)
        assertEquals(20, DanceStyle.entries.map { it.label }.distinct().size)
    }

    @Test fun firstFramePausedIsIdleAndStaysStill() {
        val director = MarkDanceDirector(seed = 5)
        val init = director.update(0, false, 0, DanceStyle.HOUSE)
        assertEquals(DanceState.NPC_IDLE, init.state)
        assertEquals(0f, init.danceWeight, .001f)
        val later = director.update(4000, false, 0, DanceStyle.HOUSE)
        assertEquals(DanceState.NPC_IDLE, later.state)
        assertEquals(0f, later.danceWeight, .001f)
    }

    @Test fun pauseReturnsSmoothlyToIdle() {
        val director = MarkDanceDirector()
        assertEquals(DanceState.ENTERING_DANCE,
            director.update(0, true, 0, DanceStyle.FUNK).state)
        assertEquals(DanceState.DANCING,
            director.update(500, true, 2, DanceStyle.FUNK).state)
        val paused = director.update(600, false, 2, DanceStyle.FUNK)
        assertEquals(DanceState.EXITING_DANCE, paused.state)
        assertEquals(1f, paused.danceWeight, .001f)
        val middle = director.update(810, false, 2, DanceStyle.FUNK)
        assertTrue(middle.danceWeight > 0f && middle.danceWeight < 1f)
        val ended = director.update(1110, false, 2, DanceStyle.FUNK)
        assertEquals(DanceState.NPC_IDLE, ended.state)
        assertEquals(0f, ended.danceWeight, .001f)
    }

    @Test fun styleSwitchWaitsForPhraseAndBridgesViaNeutralPose() {
        val director = MarkDanceDirector()
        director.update(0, true, 0, DanceStyle.HIP_HOP)
        director.update(500, true, 1, DanceStyle.HIP_HOP)
        var frame = director.update(1000, true, 9, DanceStyle.HOUSE)
        assertEquals(DanceStyle.HIP_HOP, frame.style)
        assertEquals(DanceState.DANCING, frame.state)
        frame = director.update(1200, true, 16, DanceStyle.HOUSE)
        assertEquals(DanceState.NEUTRAL_BRIDGE, frame.state)
        assertEquals(1f, frame.neutralWeight, .001f)
        assertEquals(DanceStyle.HIP_HOP, frame.style)
        frame = director.update(1900, true, 16, DanceStyle.HOUSE)
        assertEquals(DanceState.ENTERING_DANCE, frame.state)
        assertEquals(DanceStyle.HOUSE, frame.style)
        frame = director.update(2340, true, 17, DanceStyle.HOUSE)
        assertEquals(DanceState.DANCING, frame.state)
        assertEquals(DanceStyle.HOUSE, frame.style)
    }

    @Test fun clipChangesOnFourBeatPhrasesNotOnEachFrame() {
        val director = MarkDanceDirector()
        director.update(0, true, 0, DanceStyle.DISCO)
        director.update(500, true, 1, DanceStyle.DISCO)
        val same = director.update(520, true, 3, DanceStyle.DISCO)
        val next = director.update(590, true, 4, DanceStyle.DISCO)
        assertEquals(0, same.clipIndex)
        assertEquals(1, next.clipIndex)
        assertEquals(1, director.update(620, true, 4, DanceStyle.DISCO).clipIndex)
    }

    @Test fun idleUsesRareGreetingsAndNeverStartsDancing() {
        val director = MarkDanceDirector(seed = 17)
        val initial = director.update(0, false, 0, DanceStyle.HIP_HOP)
        assertEquals(NpcGesture.NONE, initial.idleGesture)
        val greeting = director.update(6500, false, 0, DanceStyle.HIP_HOP)
        assertTrue(greeting.idleGesture != NpcGesture.NONE)
        assertEquals(0f, greeting.danceWeight, 0f)
        val after = director.update(8000, false, 0, DanceStyle.HIP_HOP)
        assertEquals(NpcGesture.NONE, after.idleGesture)
        assertEquals(0f, after.danceWeight, 0f)
    }

    @Test fun unknownGenreDoesNotMakeUpAnExactStyle() {
        assertEquals(DanceStyle.HIP_HOP, chooseDanceStyle(
            DanceMusicFeatures(null, 0f, .5f, null)))
        assertEquals(DanceStyle.SALSA, chooseDanceStyle(
            DanceMusicFeatures(90f, .9f, .4f, "salsa")))
        assertEquals(DanceStyle.AMAPIANO, chooseDanceStyle(
            DanceMusicFeatures(null, .1f, .3f, "amapiano")))
        assertEquals(DanceStyle.HOUSE, chooseDanceStyle(
            DanceMusicFeatures(124f, .85f, .8f, null)))
    }
}
