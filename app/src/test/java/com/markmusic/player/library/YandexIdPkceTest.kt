package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class YandexIdPkceTest {
    private fun request() = YandexIdPkce.prepare(
        clientId = "test-client-id",
        redirectUri = "markaudio://yandex-oauth",
        scopes = listOf("login:info"),
        nowMillis = 1_000_000L
    )

    @Test fun generatedPkceHasCorrectSha256AndUniqueState() {
        val first = request()
        val second = request()
        assertNotEquals(first.codeVerifier, second.codeVerifier)
        assertNotEquals(first.state, second.state)
        assertTrue(first.codeVerifier.length in 43..128)
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                first.codeVerifier.toByteArray(StandardCharsets.US_ASCII)
            )
        )
        assertEquals(expected, first.codeChallenge)
        assertTrue(first.authorizationUrl().startsWith("https://oauth.yandex.ru/authorize?"))
        assertTrue(first.authorizationUrl().contains("response_type=code"))
        assertTrue(first.authorizationUrl().contains("code_challenge_method=S256"))
        assertTrue(first.authorizationUrl().contains("scope=login%3Ainfo"))
        assertFalse(first.authorizationUrl().contains(first.codeVerifier))
    }

    @Test fun callbackRequiresSameStateAndExactRedirect() {
        val attempt = request()
        val ok = "markaudio://yandex-oauth?code=code123&state=" + attempt.state
        assertEquals(YandexIdPkce.CallbackResult.Code("code123"),
            attempt.validateRedirect(ok, 1_001_000L))
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.WRONG_REDIRECT),
            attempt.validateRedirect("markaudio://evil-host?code=x&state=" + attempt.state, 1_001_000L)
        )
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.STATE_MISMATCH),
            attempt.validateRedirect("markaudio://yandex-oauth?code=x&state=wrong", 1_001_000L)
        )
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.EXPIRED),
            attempt.validateRedirect(ok, 1_601_000L)
        )
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.ACCESS_DENIED),
            attempt.validateRedirect("markaudio://yandex-oauth?error=access_denied&state=" +
                attempt.state, 1_001_000L)
        )
    }

    @Test fun rejectAmbiguousOrMalformedCallbackWithoutThrowing() {
        val attempt = request()
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.BAD_RESPONSE),
            attempt.validateRedirect(
                "markaudio://yandex-oauth?code=a&code=b&state=" + attempt.state, 1_001_000L
            )
        )
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.BAD_RESPONSE),
            attempt.validateRedirect(
                "markaudio://yandex-oauth?code=%XZ&state=" + attempt.state, 1_001_000L
            )
        )
        assertEquals(
            YandexIdPkce.CallbackResult.Rejected(YandexIdPkce.CallbackError.WRONG_REDIRECT),
            attempt.validateRedirect(
                "markaudio://yandex-oauth?code=a&state=" + attempt.state + "#fragment", 1_001_000L
            )
        )
    }
}
