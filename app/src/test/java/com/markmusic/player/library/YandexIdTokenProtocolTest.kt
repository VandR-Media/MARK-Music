package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class YandexIdTokenProtocolTest {
    @Test fun onlyOurOAuthClientIsAccepted() {
        assertTrue(YandexIdTokenProtocol.clientIdIsValid("a".repeat(32)))
        assertFalse(YandexIdTokenProtocol.clientIdIsValid("unknown"))
        assertFalse(YandexIdTokenProtocol.clientIdIsValid("a".repeat(32) + "\n"))
        assertFalse(YandexIdTokenProtocol.clientIdIsValid("a".repeat(32) + "&secret=1"))
    }

    @Test fun exchangedCodeContainsVerifierButNeverClientSecret() {
        val request = YandexIdPkce.prepare(
            clientId = "a".repeat(32),
            redirectUri = YandexIdTokenProtocol.APP_REDIRECT,
            scopes = listOf(YandexIdTokenProtocol.ACCOUNT_SCOPE),
            nowMillis = 1234L
        )
        val body = YandexIdTokenProtocol.exchangeBody(request, "code+/=!?")
        val form = body.split("&").associate { segment ->
            val fields = segment.split("=", limit = 2)
            URLDecoder.decode(fields[0], "UTF-8") to URLDecoder.decode(fields[1], "UTF-8")
        }
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("code+/=!?", form["code"])
        assertEquals("a".repeat(32), form["client_id"])
        assertEquals(request.codeVerifier, form["code_verifier"])
        assertFalse(body.contains("client_secret"))
        assertFalse(body.contains(request.state))
        assertTrue(YandexIdTokenProtocol.TOKEN_ENDPOINT.startsWith("https://"))
        assertTrue(YandexIdTokenProtocol.USERINFO_ENDPOINT.startsWith("https://"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyAuthorizationCodeIsRejected() {
        val request = YandexIdPkce.prepare(
            "a".repeat(32), YandexIdTokenProtocol.APP_REDIRECT,
            listOf(YandexIdTokenProtocol.ACCOUNT_SCOPE),
            nowMillis = 1234L
        )
        YandexIdTokenProtocol.exchangeBody(request, " ")
    }
}
