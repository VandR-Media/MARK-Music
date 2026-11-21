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

    @Test fun eachAccessFailureIsReportedWithoutExposingBodyOrMarkingSuccess() {
        assertEquals(
            YandexMusicProbeStatus.UNAUTHORIZED_401,
            YandexMusicProbeRules.classify(401, hasAccountUid = true)
        )
        assertEquals(
            YandexMusicProbeStatus.FORBIDDEN_403,
            YandexMusicProbeRules.classify(403, hasAccountUid = true)
        )
        assertEquals(
            YandexMusicProbeStatus.RATE_LIMITED_429,
            YandexMusicProbeRules.classify(429, hasAccountUid = true)
        )
        for (code in listOf(302, 404, 500, 503)) {
            assertEquals(
                YandexMusicProbeStatus.NOT_AVAILABLE,
                YandexMusicProbeRules.classify(code, hasAccountUid = false)
            )
        }
    }
}
