package com.markmusic.player.library

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

class YandexMusicOAuthRulesTest {
    @Test fun ownerAppAndScopeStaySeparateFromLoginSdk() {
        assertEquals("b050d96e5d604629810dac08d122c698",
            YandexMusicOAuthRules.CLIENT_ID)
        assertNotEquals("cfaacee8ff6d4941a5966d00c53a61de",
            YandexMusicOAuthRules.CLIENT_ID)
        assertEquals("music:api-public", YandexMusicOAuthRules.SCOPE)
    }

    @Test fun securePkceS256RequestDoesNotContainSecretOrVerifier() {
        val verifier = YandexMusicOAuthRules.verifier(ByteArray(32) { it.toByte() })
        assertTrue(verifier.length in 43..128)
        val challenge = YandexMusicOAuthRules.challenge(verifier)
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(expected, challenge)
        val url = URI(YandexMusicOAuthRules.authorizationUrl(verifier))
        val query = url.rawQuery.split("&").associate {
            val part = it.split("=", limit = 2)
            part[0] to URLDecoder.decode(part[1], "UTF-8")
        }
        assertEquals("https", url.scheme)
        assertEquals("oauth.yandex.ru", url.host)
        assertEquals("code", query["response_type"])
        assertEquals(YandexMusicOAuthRules.CLIENT_ID, query["client_id"])
        assertEquals(YandexMusicOAuthRules.REDIRECT_URI, query["redirect_uri"])
        assertEquals("music:api-public", query["scope"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals(challenge, query["code_challenge"])
        assertFalse(url.toString().contains(verifier))
        assertFalse(url.toString().contains("client_secret"))
    }

    @Test fun exchangeFormUsesOnlyPublicIdCodeAndVerifier() {
        val verifier = YandexMusicOAuthRules.verifier(ByteArray(32) { 42 })
        val body = YandexMusicOAuthRules.tokenRequestBody("0123456", verifier)
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("client_id=" + YandexMusicOAuthRules.CLIENT_ID))
        assertTrue(body.contains("code=0123456"))
        assertTrue(body.contains("code_verifier=" + verifier))
        assertFalse(body.contains("client_secret"))
        assertFalse(body.contains("refresh_token"))

        // Synthetic test code: do not record any real user-supplied OAuth code.
        val mixedCode = "aB3cD5eF7gH9jK2m"
        assertEquals(16, mixedCode.length)
        val mixedBody = YandexMusicOAuthRules.tokenRequestBody(mixedCode, verifier)
        assertTrue(mixedBody.contains("code=" + mixedCode))
        assertTrue(mixedBody.contains("code_verifier=" + verifier))
    }

    @Test fun musicMajorUsesOwnClientAndRequiresBothReturnedScopes() {
        val client = "017187c1377846eb80cedcd93cf3fd38"
        val requested = "music:api-public music:major-public-api"
        val verifier = YandexMusicOAuthRules.verifier(ByteArray(32) { 17 })
        val url = URI(YandexMusicOAuthRules.authorizationUrl(verifier, client, requested))
        val params = url.rawQuery.split("&").associate {
            val pair = it.split("=", limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals(client, params["client_id"])
        assertEquals(requested, params["scope"])
        assertEquals(YandexMusicOAuthRules.REDIRECT_URI, params["redirect_uri"])
        val body = YandexMusicOAuthRules.tokenRequestBody("0123456", verifier, client)
        assertTrue(body.contains("client_id=" + client))
        assertFalse(body.contains(YandexMusicOAuthRules.CLIENT_ID))
        assertFalse(body.contains("client_secret"))
        val required = setOf("music:api-public", "music:major-public-api")
        assertTrue(YandexMusicOAuthRules.acceptsReturnedScope(requested, required))
        assertTrue(YandexMusicOAuthRules.acceptsReturnedScope(null, required))
        assertFalse(YandexMusicOAuthRules.acceptsReturnedScope("music:api-public", required))
        assertFalse(YandexMusicOAuthRules.acceptsReturnedScope("music:major-public-api", required))
        assertFalse(YandexMusicOAuthRules.acceptsReturnedScope("login:info", required))
    }

    @Test fun rejectsInvalidCodesReducedScopesAndExpiredPendingSessions() {
        assertTrue(YandexMusicOAuthRules.validCode("0123456"))
        assertTrue(YandexMusicOAuthRules.validCode("aB3cD5eF7gH9jK2m"))
        assertTrue(YandexMusicOAuthRules.validCode("1234567890123456"))
        // Case must be preserved, including mixed and upper-case characters.
        for (input in listOf(
            "123456", "12345678", "abcdefg", "12 4567", "", "abcdefghijklmno",
            "abcdefghijklmnopq", "abcd-1234-efgh-56", "привет1234567890", "abCd\nD5eF7gH9jK2m"
        )) {
            assertFalse(YandexMusicOAuthRules.validCode(input))
        }
        assertTrue(YandexMusicOAuthRules.acceptsReturnedScope(null))
        assertTrue(YandexMusicOAuthRules.acceptsReturnedScope("music:api-public"))
        assertTrue(YandexMusicOAuthRules.acceptsReturnedScope("music:api-public other:read"))
        assertFalse(YandexMusicOAuthRules.acceptsReturnedScope("music:api-publicity"))
        assertFalse(YandexMusicOAuthRules.acceptsReturnedScope("login:info"))
        assertFalse(YandexMusicOAuthRules.acceptsReturnedScope(""))
        assertTrue(YandexMusicOAuthRules.pendingNotExpired(1000L, 601000L))
        assertFalse(YandexMusicOAuthRules.pendingNotExpired(1000L, 1000L))
        assertFalse(YandexMusicOAuthRules.pendingNotExpired(1000L, 601001L))
        assertFalse(YandexMusicOAuthRules.pendingNotExpired(-1L, 0L))
    }
}
