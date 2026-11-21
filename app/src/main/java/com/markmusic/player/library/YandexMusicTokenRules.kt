package com.markmusic.player.library

/**
 * The documented Yandex ID /info response contains client_id and id even
 * when the OAuth token was issued for another Yandex service.
 * This checks token ownership, NOT access to Yandex Music catalog or streams.
 */
internal object YandexMusicTokenRules {
    fun matchesRegisteredMusicClient(clientId: String, userId: String): Boolean =
        clientId == YandexMusicOAuthRules.CLIENT_ID && userId.isNotBlank()
}
