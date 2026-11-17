package com.markmusic.player.audio

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StereoSpectrumWorkerTest {
    @Test fun realStereoPcm16DecodesLeftAndRightSeparately() {
        val data=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        data.putShort(16384.toShort()).putShort((-8192).toShort())
        data.putShort(8192.toShort()).putShort(0)
        val result=decodePcmForSpectrum(data.array(),2,C.ENCODING_PCM_16BIT)
        assertEquals(4,result.size)
        assertEquals(.5f,result[0],.00001f)
        assertEquals(-.25f,result[1],.00001f)
        assertEquals(.25f,result[2],.00001f)
        assertEquals(0f,result[3],0f)
    }

    @Test fun monoPcmIsDeliveredAsOneChannelAndMirroredInSpectrum() {
        val data=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        data.putShort(16384.toShort()).putShort((-16384).toShort())
        val pcm=decodePcmForSpectrum(data.array(),1,C.ENCODING_PCM_16BIT)
        assertEquals(2,pcm.size)
        assertEquals(.5f,pcm[0],0f)
        assertEquals(-.5f,pcm[1],0f)
    }

    @Test fun floatPcmNaNIsSanitizedAndDoesNotEnterSpectralAnalysis() {
        val data=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        data.putFloat(Float.NaN).putFloat(.75f)
        val pcm=decodePcmForSpectrum(data.array(),2,C.ENCODING_PCM_FLOAT)
        assertEquals(0f,pcm[0],0f)
        assertEquals(.75f,pcm[1],0f)
    }

    @Test fun multichannelPcmUsesFirstTwoChannelsWithoutInterleavingMistakes() {
        val data=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        data.putShort(16384.toShort()).putShort((-16384).toShort()).putShort(0)
        data.putShort(8192.toShort()).putShort((-8192).toShort()).putShort(0)
        val pcm=decodePcmForSpectrum(data.array(),3,C.ENCODING_PCM_16BIT)
        assertEquals(4,pcm.size)
        assertEquals(.5f,pcm[0],0f)
        assertEquals(-.5f,pcm[1],0f)
        assertEquals(.25f,pcm[2],0f)
        assertEquals(-.25f,pcm[3],0f)
    }
}
