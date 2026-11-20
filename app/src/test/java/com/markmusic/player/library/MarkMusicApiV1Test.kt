package com.markmusic.player.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkMusicApiV1Test {
    @Test fun sourcesHaveExplicitNonMisleadingCapabilities() {
        val device = MarkMusicApiV1.capabilities(MusicSource.DEVICE)
        val yandex = MarkMusicApiV1.capabilities(MusicSource.YANDEX_MUSIC)
        val suno = MarkMusicApiV1.capabilities(MusicSource.SUNO)

        assertTrue(device.supports(MusicFeature.LOCAL_SCAN))
        assertFalse(device.supports(MusicFeature.REMOTE_STREAM))
        assertFalse(yandex.supports(MusicFeature.PERSONAL_CATALOG))
        assertFalse(yandex.supports(MusicFeature.REMOTE_STREAM))
        assertTrue(yandex.supports(MusicFeature.AUTHORIZATION))
        assertTrue(suno.supports(MusicFeature.IMPORTED_AUDIO))
        assertFalse(suno.supports(MusicFeature.REMOTE_STREAM))
        assertTrue(yandex.blockedReason?.isNotBlank() == true)
        assertTrue(suno.blockedReason?.isNotBlank() == true)
    }

    @Test fun unavailableApisNeverReturnFakeSongsOrUrls() = runBlocking {
        for (source in listOf(MusicSource.YANDEX_MUSIC, MusicSource.SUNO)) {
            val adapter = requireNotNull(MarkMusicApiV1.remoteGateway(source))
            assertEquals(source, adapter.source)
            assertEquals(
                MusicApiResult.Unavailable(MusicApiError.API_NOT_GRANTED),
                adapter.loadPersonalLibrary()
            )
            assertEquals(
                MusicApiResult.Unavailable(MusicApiError.API_NOT_GRANTED),
                adapter.resolveAuthorizedStream(
                    RemoteMusicItem("123", source, "Title", "Artist")
                )
            )
        }
        assertEquals(null, MarkMusicApiV1.remoteGateway(MusicSource.DEVICE))
    }

    @Test fun compactTabLabelsKeepAllThreeTargetsShort() {
        assertEquals(3, MusicSource.entries.size)
        assertEquals("На\nустройстве", MusicSource.DEVICE.compactLabel)
        assertEquals("Яндекс\nМузыка", MusicSource.YANDEX_MUSIC.compactLabel)
        assertEquals("Suno", MusicSource.SUNO.compactLabel)
        assertTrue(MusicSource.entries.all {
            it.compactLabel.split("\n").size <= 2 &&
                it.compactLabel.split("\n").all { part -> part.length <= 10 }
        })
    }
}
