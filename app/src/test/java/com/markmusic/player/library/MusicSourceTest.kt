package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceTest {
    @Test fun unknownOrRetiredPreferenceUsesLocalLibrary() {
        assertEquals(MusicSource.DEVICE, MusicSource.fromStored(null))
        assertEquals(MusicSource.DEVICE, MusicSource.fromStored("UNKNOWN"))
    }

    @Test fun eachSourceSurvivesPersistence() {
        MusicSource.entries.forEach { source ->
            assertEquals(source, MusicSource.fromStored(source.name))
        }
    }

    @Test fun sunoImportsHaveStableNegativeMediaIds() {
        val uri = "content://com.android.providers.downloads.documents/document/17"
        val id = sunoDocumentId(uri)
        assertTrue(id < 0L)
        assertEquals(id, sunoDocumentId(uri))
        assertTrue(sunoDocumentId("content://test/document/18") < 0L)
        assertTrue(id != sunoDocumentId("content://test/document/18"))
    }
}
