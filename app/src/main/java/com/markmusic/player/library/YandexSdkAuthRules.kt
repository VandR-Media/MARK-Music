package com.markmusic.player.library

/**
 * Pure validation for token/profile results returned by the official LoginSDK.
 * Profile identity must be present, and an explicit client ID must match.
 */
internal object YandexSdkAuthRules {
    fun isTrustedProfile(
        login: String,
        userId: String,
        returnedClientId: String,
        expectedClientId: String
    ): Boolean {
        return login.isNotBlank() && userId.isNotBlank() &&
            expectedClientId.isNotBlank() &&
            (returnedClientId.isBlank() || returnedClientId == expectedClientId)
    }

    fun expiryTimeMillis(nowMillis: Long, expiresInSeconds: Long): Long? {
        if (nowMillis < 0L || expiresInSeconds <= 0L) return null
        val capped = expiresInSeconds.coerceAtMost(365L * 24L * 60L * 60L)
        val delta = capped * 1000L
        if (nowMillis > Long.MAX_VALUE - delta) return null
        return nowMillis + delta
    }
}
