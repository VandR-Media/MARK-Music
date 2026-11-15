package com.markmusic.player.audio

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioMeterBusTest {
    @Test fun silenceProducesZeroLevels() {
        assertEquals(0f,normalizeMeterRms(0.0),0f)
        assertEquals(0f,normalizeMeterRms(-1.0),0f)
        assertEquals(0f,normalizeMeterRms(Double.NaN),0f)
        assertEquals(1f,normalizeMeterRms(1.0),.00001f)
    }

    @Test fun analyzesLeftAndRightRealPcmIndependently() {
        val data=ByteBuffer.allocate(4000).order(ByteOrder.LITTLE_ENDIAN)
        repeat(1000) {
            data.putShort(16384.toShort()) // 0.5 left
            data.putShort(0)              // silence right
        }
        data.flip()
        val origin=data.position()
        val (l,r)=pcmStats(data,2,C.ENCODING_PCM_16BIT)
        assertEquals(.5f,l.rms,.001f)
        assertEquals(.5f,l.peak,.001f)
        assertEquals(0f,r.rms,0f)
        assertEquals(0f,r.peak,0f)
        assertEquals(origin,data.position()) // tee never consumes original audio
    }

    @Test fun monoIsMirroredToBothIndicators() {
        val data=ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        repeat(1024) { data.putShort(if(it%2==0) 8000.toShort() else (-8000).toShort()) }
        data.flip()
        val (l,r)=pcmStats(data,1,C.ENCODING_PCM_16BIT)
        assertEquals(l.rms,r.rms,0f)
        assertEquals(l.peak,r.peak,0f)
    }

    @Test fun levelValuesRemainClamped() {
        assertEquals(1f,normalizeMeterRms(2.0),0f)
        assertTrue(normalizeMeterRms(.01)>0f)
        assertTrue(normalizeMeterRms(.01)<1f)
    }

    @Test fun floatPcmCanBeMeasuredWithoutMicrophonePermission() {
        val data=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        data.putFloat(.75f).putFloat(-.25f).flip()
        val (left,right)=pcmStats(data,2,C.ENCODING_PCM_FLOAT)
        assertEquals(.75f,left.peak,0f)
        assertEquals(.25f,right.peak,0f)
    }
}
