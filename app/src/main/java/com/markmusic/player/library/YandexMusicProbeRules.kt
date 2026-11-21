package com.markmusic.player.library

/**
 * Experimental capability check for the community-documented, not officially
 * published Yandex Music /account/status JSON endpoint.
 *
 * A good response proves ONLY this one metadata endpoint works with this token;
 * it does not prove access to playlists, audio streams or content licensing.
 */
internal enum class YandexMusicProbeStatus {
    ACCOUNT_ENDPOINT_RESPONDED,
    ACCESS_REJECTED,
    INVALID_RESPONSE,
    NOT_AVAILABLE,
    NOT_CONNECTED
}

internal object YandexMusicProbeRules {
    const val HOST = "api.music.yandex.net"
    const val PATH = "/account/status"

    fun classify(httpCode: Int, hasAccountUid: Boolean): YandexMusicProbeStatus = when {
        httpCode == 200 && hasAccountUid -> YandexMusicProbeStatus.ACCOUNT_ENDPOINT_RESPONDED
        httpCode == 200 -> YandexMusicProbeStatus.INVALID_RESPONSE
        httpCode == 401 || httpCode == 403 -> YandexMusicProbeStatus.ACCESS_REJECTED
        else -> YandexMusicProbeStatus.NOT_AVAILABLE
    }
}
