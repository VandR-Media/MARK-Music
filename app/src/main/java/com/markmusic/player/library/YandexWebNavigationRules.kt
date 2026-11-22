package com.markmusic.player.library

import java.net.URI

/**
 * Strict HTTPS navigation allowlist for the optional official-site WebView.
 * No auth cookies or tokens from another browser/app are imported.
 */
internal object YandexWebNavigationRules {
    const val START_URL = "https://music.yandex.ru/"

    private val allowedHosts = setOf(
        "music.yandex.ru",
        "oauth.yandex.ru",
        "passport.yandex.ru",
        "login.yandex.ru",
        "id.yandex.ru",
        "yandex.ru",
        "music.yandex.com",
        "passport.yandex.com",
        "yandex.com"
    )

    fun isAllowed(address: String): Boolean {
        val uri = parseHttps(address) ?: return false
        return uri.host.lowercase() in allowedHosts
    }

    fun isExternalHttps(address: String): Boolean =
        parseHttps(address) != null

    private fun parseHttps(address: String): URI? =
        runCatching {
            URI(address).takeIf {
                it.scheme.equals("https", ignoreCase = true) &&
                    it.host != null &&
                    it.userInfo == null &&
                    (it.port == -1 || it.port == 443)
            }
        }.getOrNull()
}
