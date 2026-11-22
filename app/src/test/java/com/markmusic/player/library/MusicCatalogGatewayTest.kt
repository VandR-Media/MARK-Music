package com.markmusic.player.library

import org.junit.Assert.*
import org.junit.Test

class MusicCatalogGatewayTest {
    @Test fun errorsPreserveAuthAndRateLimitFailures() {
        assertEquals(CatalogError.AUTH_REQUIRED, CatalogErrors.fromHttpStatus(401))
        assertEquals(CatalogError.FORBIDDEN, CatalogErrors.fromHttpStatus(403))
        assertEquals(CatalogError.RATE_LIMITED, CatalogErrors.fromHttpStatus(429))
        assertEquals(CatalogError.INVALID_RESPONSE, CatalogErrors.fromHttpStatus(503))
    }

    @Test fun catalogModelsKeepProviderIdsAndNullableMetadata() {
        val track = CatalogTrack(
            providerId = "123:456",
            title = "Synthetic track",
            artists = listOf("Test artist"),
            durationMs = null
        )
        assertEquals("123:456", track.providerId)
        assertFalse(track.canPlay)
        assertNull(track.coverUri)
        assertEquals(0, CatalogPage(listOf(track), page = 0, hasNext = false).page)
        assertThrows(IllegalArgumentException::class.java) {
            CatalogTrack(providerId = "", title = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CatalogTrack(providerId = "1", title = "", durationMs = -1)
        }
    }

    @Test fun noGatewayNeverPretendsToHaveAnEmptyRemoteLibrary() {
        val gateway = UnconfiguredMusicCatalogGateway
        val result = kotlinx.coroutines.runBlocking { gateway.playlists() }
        assertEquals(CatalogResult.Failure(CatalogError.NOT_CONFIGURED), result)
        val search = kotlinx.coroutines.runBlocking { gateway.search("test") }
        assertEquals(CatalogResult.Failure(CatalogError.NOT_CONFIGURED), search)
    }
}
