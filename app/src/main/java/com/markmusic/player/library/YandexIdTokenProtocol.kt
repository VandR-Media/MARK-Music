package com.markmusic.player.library

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Yandex ID OAuth only. The resulting login:info token does NOT grant access
 * to the Yandex Music catalog or to its audio streams.
 *
 * https://yandex.ru/dev/id/doc/ru/codes/code-url
 */
internal object YandexIdTokenProtocol {
    const val TOKEN_ENDPOINT = "https://oauth.yandex.ru/token"
    const val USERINFO_ENDPOINT = "https://login.yandex.ru/info?format=json"
    const val APP_REDIRECT = "markaudio://oauth/yandex"
    const val ACCOUNT_SCOPE = "login:info"

    fun clientIdIsValid(value: String): Boolean =
        value.length in 16..128 && value.all {
            it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-'
        }

    fun exchangeBody(request: YandexIdPkce.Request, code: String): String {
        require(clientIdIsValid(request.clientId))
        require(code.isNotBlank() && code.length <= 1024)
        require(request.codeVerifier.length in 43..128)
        return listOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to request.clientId,
            "code_verifier" to request.codeVerifier
        ).joinToString("&") { pair -> encode(pair.first) + "=" + encode(pair.second) }
    }

    private fun encode(v: String): String =
        URLEncoder.encode(v, StandardCharsets.UTF_8.name())
}
