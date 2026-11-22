package com.markmusic.player.library

/**
 * API Yandex ID /info returns client_id and id for Yandex OAuth tokens
 * issued to any Yandex service. Compare BOTH client and account identity.
 *
 * This is not a Yandex Music playback or catalog authorization check.
 */
internal enum class YandexMusicIdentity {
    SAME_ACCOUNT,
    ACCOUNT_NOT_COMPARED,
    DIFFERENT_ACCOUNT,
    INVALID_CLIENT_OR_PROFILE
}

internal object YandexMusicTokenRules {
    fun classify(
        clientId: String,
        tokenUserId: String,
        signedInYandexUserId: String?,
        expectedClientId: String = YandexMusicOAuthRules.CLIENT_ID
    ): YandexMusicIdentity {
        if (clientId != expectedClientId || tokenUserId.isBlank()) {
            return YandexMusicIdentity.INVALID_CLIENT_OR_PROFILE
        }
        if (signedInYandexUserId.isNullOrBlank()) {
            return YandexMusicIdentity.ACCOUNT_NOT_COMPARED
        }
        return if (tokenUserId == signedInYandexUserId) {
            YandexMusicIdentity.SAME_ACCOUNT
        } else {
            YandexMusicIdentity.DIFFERENT_ACCOUNT
        }
    }
}
