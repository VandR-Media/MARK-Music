package com.markmusic.player.library

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

/**
 * Public, owner-registered Yandex OAuth client for the Music API.
 * Separate from the LoginSDK client (login:info).
 * For API-type OAuth applications Yandex fixes the redirect URI to verification_code.
 */
internal object YandexMusicOAuthRules {
    const val CLIENT_ID = "b050d96e5d604629810dac08d122c698"
    const val SCOPE = "music:api-public"
    const val REDIRECT_URI = "https://oauth.yandex.ru/verification_code"
    const val PENDING_TTL_MILLIS = 10L * 60L * 1000L

    fun verifier(randomBytes: ByteArray): String {
        require(randomBytes.size >= 32) { "PKCE entropy must be >= 256 bits" }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    fun challenge(codeVerifier: String): String {
        require(codeVerifier.length in 43..128 && codeVerifier.matches(Regex("[A-Za-z0-9_\\-]+")))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun authorizationUrl(codeVerifier: String, clientId: String = CLIENT_ID, requestedScope: String = SCOPE): String =
        "https://oauth.yandex.ru/authorize?" +
            "response_type=code" +
            "&client_id=" + encode(clientId) +
            "&scope=" + encode(requestedScope) +
            "&redirect_uri=" + encode(REDIRECT_URI) +
            "&code_challenge=" + encode(challenge(codeVerifier)) +
            "&code_challenge_method=S256"

    /**
     * 0.7.20 Huawei device report: Yandex displayed a 16-character alphanumeric
     * authorization code. Yandex's official OAuth glossary explicitly specifies a 16-character
     * code; an error-code table elsewhere still mentions seven digits.
     * Both are accepted locally;
     * the OAuth server remains authoritative when exchanging the code.
     * Never normalize case or include these values in logs.
     */
    fun validCode(code: String): Boolean =
        code.matches(Regex("(?:[0-9]{7}|[A-Za-z0-9]{16})"))

    fun tokenRequestBody(code: String, codeVerifier: String, clientId: String = CLIENT_ID): String {
        require(validCode(code))
        challenge(codeVerifier) // Validate format before sending verifier to OAuth.
        return "grant_type=authorization_code" +
            "&code=" + encode(code) +
            "&client_id=" + encode(clientId) +
            "&code_verifier=" + encode(codeVerifier)
    }

    /**
     * Yandex omits the scope property if all registered/requested rights were granted.
     * A present scope property means reduced rights and must contain the Music scope.
     */
    fun acceptsReturnedScope(scope: String?, requiredScopes: Set<String> = setOf(SCOPE)): Boolean =
        scope == null || scope.split(Regex("\\s+")).toSet().containsAll(requiredScopes)

    fun pendingNotExpired(nowMillis: Long, expiresAt: Long): Boolean =
        nowMillis >= 0L && expiresAt > nowMillis && expiresAt - nowMillis <= PENDING_TTL_MILLIS
}
