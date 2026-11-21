package com.markmusic.player.library

/**
 * Third, user-facing Yandex OAuth app registered for MARK Audio Android.
 * Unlike the legacy verification-code/API-type app, this one is explicitly
 * configured in Yandex OAuth as "Для авторизации пользователей".
 *
 * Client IDs are public; never embed Client secret.
 */
internal object YandexMusicUserOAuthRules {
    const val CLIENT_ID = "a74b75bb16a04004bed54af5fa52b574"

    fun matchesAccount(
        returnedClientId: String,
        returnedUserId: String,
        signedInYandexUserId: String?
    ): Boolean = returnedClientId == CLIENT_ID &&
        returnedUserId.isNotBlank() &&
        !signedInYandexUserId.isNullOrBlank() &&
        returnedUserId == signedInYandexUserId
}
