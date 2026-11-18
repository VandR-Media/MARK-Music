package com.markmusic.player.visual

import com.markmusic.player.audio.AudioMeterFrame
import com.markmusic.player.audio.FrequencyBandLevels
import com.markmusic.player.audio.StereoFrequencyBands
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkVisualV2FrameTest {
    @Test fun playingMapsRealIndependentLeftLowRightHigh() {
        val measured = AudioMeterFrame(left=.52f,right=.37f,peakLeft=.7f,peakRight=.6f)
        val bins = StereoFrequencyBands(
            left = FrequencyBandLevels(low=.5f,mid=0f,high=0f),
            right = FrequencyBandLevels(low=0f,mid=0f,high=.45f),
            ready=true
        )
        val frame = MarkVisualV2Frame.serialize(measured,bins,true,1200)
        assertTrue(frame.contains("\"schema\":\"mark.audio.stereo/1\""))
        assertTrue(frame.contains("\"source\":\"pcm\""))
        assertTrue(frame.contains("\"playing\":true"))
        assertTrue(frame.contains("\"rms\":0.520000"))
        assertTrue(frame.contains("\"rms\":0.370000"))
        assertTrue(frame.contains("\"low\":0.000000"))
        assertTrue(frame.contains("\"high\":0.000000"))
        assertFalse(frame.contains("NaN"))
        assertFalse(frame.contains("DEMO"))
    }

    @Test fun pauseResetsAllChannelsWithoutInventingMusic() {
        val measured = AudioMeterFrame(left=.9f,right=.8f,bassLevel=.7f)
        val bins = StereoFrequencyBands(
            left=FrequencyBandLevels(1f,1f,1f),
            right=FrequencyBandLevels(1f,1f,1f),ready=true
        )
        val frame=MarkVisualV2Frame.serialize(measured,bins,false,0)
        assertTrue(frame.contains("\"source\":\"silence\""))
        assertTrue(frame.contains("\"playing\":false"))
        assertFalse(frame.contains("\"rms\":0.900000"))
        assertFalse(frame.contains("\"low\":1.000000"))
        assertTrue(frame.contains("\"beat\":0.000000"))
    }

    @Test fun unavailableFftDoesNotFakeSixBands() {
        val frame=MarkVisualV2Frame.serialize(
            AudioMeterFrame(left=.42f,right=.23f),
            StereoFrequencyBands(ready=false),true,100
        )
        assertTrue(frame.contains("\"rms\":0.420000"))
        assertTrue(frame.contains("\"low\":0.000000"))
        assertTrue(frame.contains("\"source\":\"pcm\""))
    }
}
