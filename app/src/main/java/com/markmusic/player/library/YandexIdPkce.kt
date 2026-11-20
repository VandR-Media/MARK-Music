package com.markmusic.player.library

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Authorization-code PKCE preparation for a separately registered Yandex ID app.
 *
 * IMPORTANT: This does not grant access to Yandex Music's audio catalog.
 * No real OAuth request is started until an OAuth client ID and a registered
 * redirect URI exist, and the final token-exchange/storage implementation is
 * reviewed. Do not put client secrets or account credentials in Git.
 */
internal object YandexIdPkce {
    private const val TEN_MINUTES_MS = 10 * 60 * 1000L
    private const val AUTHORIZATION_ENDPOINT = "https://oauth.yandex.ru/authorize"
    private val rng = SecureRandom()

    data class Request(
        val clientId: String,
        val redirectUri: String,
        val scopes: List<String>,
        val state: String,
        val codeVerifier: String,
        val codeChallenge: String,
        val createdAtMillis: Long
    ) {
        fun authorizationUrl(): String {
            val params = listOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scopes.joinToString(" "),
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256"
            )
            return AUTHORIZATION_ENDPOINT + "?" + params.joinToString("&") {
                encode(it.first) + "=" + encode(it.second)
            }
        }

        fun validateRedirect(url: String, nowMillis: Long): CallbackResult {
            if (nowMillis < createdAtMillis || nowMillis - createdAtMillis > TEN_MINUTES_MS) {
                return CallbackResult.Rejected(CallbackError.EXPIRED)
            }
            val callback = runCatching { URI(url) }.getOrNull()
                ?: return CallbackResult.Rejected(CallbackError.WRONG_REDIRECT)
            val allowed = URI(redirectUri)
            if (callback.scheme != allowed.scheme ||
                callback.rawAuthority != allowed.rawAuthority ||
                callback.rawPath != allowed.rawPath ||
                callback.rawFragment != null
            ) {
                return CallbackResult.Rejected(CallbackError.WRONG_REDIRECT)
            }
            val fields = runCatching {
                callback.rawQuery?.split("&")?.mapNotNull { part ->
                    val pos = part.indexOf('=')
                    if (pos < 0) null else decode(part.substring(0, pos)) to
                        decode(part.substring(pos + 1))
                }?.groupBy({ it.first }, { it.second }) ?: emptyMap()
            }.getOrNull() ?: return CallbackResult.Rejected(CallbackError.BAD_RESPONSE)

            // Duplicate OAuth parameters are ambiguous and must be rejected.
            if (fields.any { (key, values) ->
                key in setOf("code", "state", "error") && values.size != 1
            }) return CallbackResult.Rejected(CallbackError.BAD_RESPONSE)

            val returnedState = fields["state"]?.singleOrNull()
                ?: return CallbackResult.Rejected(CallbackError.STATE_MISMATCH)
            if (!MessageDigest.isEqual(
                    state.toByteArray(StandardCharsets.UTF_8),
                    returnedState.toByteArray(StandardCharsets.UTF_8)
                )
            ) return CallbackResult.Rejected(CallbackError.STATE_MISMATCH)
            if (fields.containsKey("error")) {
                return CallbackResult.Rejected(CallbackError.ACCESS_DENIED)
            }
            val code = fields["code"]?.singleOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return CallbackResult.Rejected(CallbackError.BAD_RESPONSE)
            return CallbackResult.Code(code)
        }
    }

    sealed interface CallbackResult {
        data class Code(val value: String) : CallbackResult
        data class Rejected(val reason: CallbackError) : CallbackResult
    }

    enum class CallbackError {
        WRONG_REDIRECT, EXPIRED, STATE_MISMATCH, ACCESS_DENIED, BAD_RESPONSE
    }

    fun prepare(
        clientId: String,
        redirectUri: String,
        scopes: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Request {
        require(clientId.isNotBlank() && clientId.length <= 256)
        require(scopes.isNotEmpty() && scopes.all { it.isNotBlank() })
        val redirect = URI(redirectUri)
        require(redirect.scheme != null && redirect.host != null) {
            "Register a valid absolute OAuth redirect URI for the app."
        }
        require(redirect.rawQuery == null && redirect.rawFragment == null)
        val verifier = randomUrlSafe(48)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                verifier.toByteArray(StandardCharsets.US_ASCII)
            )
        )
        return Request(
            clientId = clientId,
            redirectUri = redirectUri,
            scopes = scopes.toList(),
            state = randomUrlSafe(24),
            codeVerifier = verifier,
            codeChallenge = challenge,
            createdAtMillis = nowMillis
        )
    }

    private fun randomUrlSafe(bytes: Int): String =
        ByteArray(bytes).also(rng::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
