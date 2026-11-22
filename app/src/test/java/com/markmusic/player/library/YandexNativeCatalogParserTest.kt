package com.markmusic.player.library

import org.junit.Assert.*
import org.junit.Test

class YandexNativeCatalogParserTest {
    @Test fun parsesNativeSearchWithoutClaimingStreamingRights() {
        val json = """
            {"result":{"tracks":{"total":1,"results":[{
              "id":12345,"title":"Тестовый трек","available":true,
              "durationMs":195000,
              "artists":[{"name":"Исполнитель"}],
              "albums":[{"id":678}],
              "coverUri":"avatars.yandex.net/cover/%%"
            }]}}}
        """.trimIndent()
        val value = YandexNativeCatalogParser.parseSearch(json, 0)
        assertTrue(value is CatalogResult.Success)
        val page = (value as CatalogResult.Success).data
        assertEquals(1, page.items.size)
        assertEquals("12345", page.items.single().providerId)
        assertEquals("678", page.items.single().albumId)
        assertEquals("Исполнитель", page.items.single().artists.single())
        assertEquals(195000L, page.items.single().durationMs)
        assertFalse(page.items.single().canPlay)
        assertFalse(page.hasNext)
    }

    @Test fun toleratesSparseFieldsAndStringTrackIds() {
        val result = YandexNativeCatalogParser.parseSearch(
            """{"result":{"tracks":{"results":[{"id":"ab:cd","title":"Track"}]}}}""", 0)
        assertTrue(result is CatalogResult.Success)
        val track = (result as CatalogResult.Success).data.items.single()
        assertEquals("ab:cd", track.providerId)
        assertNull(track.durationMs)
        assertTrue(track.artists.isEmpty())
        assertFalse(track.canPlay)
    }

    @Test fun rejectsMissingExpectedSchema() {
        assertEquals(
            CatalogResult.Failure(CatalogError.INVALID_RESPONSE),
            YandexNativeCatalogParser.parseSearch("""{"result":{"artists":{}}}""", 0)
        )
    }

    @Test fun nativeClientsAlwaysUseOurRegisteredIds() {
        assertEquals(YandexMusicUserOAuthRules.CLIENT_ID, NativeYandexOAuthClient.USER.clientId)
        assertEquals("user_token", NativeYandexOAuthClient.USER.slot)
        assertEquals("major_token", NativeYandexOAuthClient.MAJOR.slot)
        assertNotEquals(
            NativeYandexOAuthClient.USER.clientId,
            NativeYandexOAuthClient.MAJOR.clientId
        )
    }
}
