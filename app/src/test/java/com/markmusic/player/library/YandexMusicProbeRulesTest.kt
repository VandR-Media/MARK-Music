package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Test

class YandexMusicProbeRulesTest {
    @Test fun usesOnlyTheFixedMusicServiceAccountStatusEndpoint() {
        assertEquals("api.music.yandex.net", YandexMusicProbeRules.HOST)
        assertEquals("/account/status", YandexMusicProbeRules.PATH)
    }

    @Test fun successRequiresAccountDataNotJustAnHttp200() {
        assertEquals(
            YandexMusicProbeStatus.ACCOUNT_ENDPOINT_RESPONDED,
            YandexMusicProbeRules.classify(200, hasAccountUid = true)
        )
        assertEquals(
            YandexMusicProbeStatus.INVALID_RESPONSE,
            YandexMusicProbeRules.classify(200, hasAccountUid = false)
        )
    }

    @Test fun accessErrorsNeverMasqueradeAsSuccessfulMusicCatalogAccess() {
        for (code in listOf(401, 403)) {
            assertEquals(
                YandexMusicProbeStatus.ACCESS_REJECTED,
                YandexMusicProbeRules.classify(code, hasAccountUid = false)
            )
        }
        for (code in listOf(302, 404, 429, 500, 503)) {
            assertEquals(
                YandexMusicProbeStatus.NOT_AVAILABLE,
                YandexMusicProbeRules.classify(code, hasAccountUid = false)
            )
        }
    }
}
