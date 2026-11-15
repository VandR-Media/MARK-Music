package com.markmusic.player.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VintageDecksTest {
    @Test fun clocksDisplayRealHoursMinutesAndSeconds() {
        assertEquals("00:00", deckClock(0L))
        assertEquals("00:01", deckClock(1_999L))
        assertEquals("03:46", deckClock(226_000L))
        assertEquals("05:14", deckClock(314_000L))
        assertEquals("01:03:01", deckClock(3_781_000L))
        assertEquals("00:00", deckClock(-1_000L))
    }

    @Test fun onlyMechanicalMachinesRotate() {
        assertEquals(0f, machineDiscRpm(VintageMachine.RADIO,.5f,false),0f)
        assertEquals(33.333334f, machineDiscRpm(VintageMachine.JUKEBOX,.5f,false),.00001f)
        for (mode in listOf(VintageMachine.CD,VintageMachine.MINIDISC,VintageMachine.DAT)) {
            assertTrue(machineDiscRpm(mode,0f,false)>100f)
        }
    }

    @Test fun discVelocityRespondsToProgressWithinPhysicalBounds() {
        assertEquals(500f,machineDiscRpm(VintageMachine.CD,0f,false),0f)
        assertEquals(200f,machineDiscRpm(VintageMachine.CD,1f,false),0f)
        assertEquals(500f,machineDiscRpm(VintageMachine.CD,-10f,false),0f)
        assertEquals(200f,machineDiscRpm(VintageMachine.CD,5f,false),0f)
        assertTrue(machineDiscRpm(VintageMachine.DAT,0f,true)>
            machineDiscRpm(VintageMachine.DAT,0f,false))
    }

    @Test fun rotationIsVsynchronizedAt60_90And120Hz() {
        val rotations=listOf(60,90,120).map { fps ->
            var angle=0f
            repeat(fps) {
                angle=vintageAngle(angle,1_000_000_000L/fps, 33.333334f)
            }
            angle
        }
        assertEquals(rotations[0],rotations[1],.002f)
        assertEquals(rotations[1],rotations[2],.002f)
        assertEquals(200f,rotations[0],.005f)
    }

    @Test fun pauseAndUnusualFrameDurationsDoNotAdvanceHardware() {
        assertEquals(220f,vintageAngle(220f,0L,500f),0f)
        assertEquals(220f,vintageAngle(220f,-1L,500f),0f)
        assertEquals(220f,vintageAngle(220f,1000L,0f),0f)
    }
}
