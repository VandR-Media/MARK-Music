package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexSdkAuthRulesTest {
    private val client = "cfaacee8ff6d4941a5966d00c53a61de"

    @Test fun acceptsVerifiedAccountWithMatchingClientId() {
        assertTrue(YandexSdkAuthRules.isTrustedProfile("login", "123", client, client))
        // Some Yandex profile responses omit the client_id field.
        assertTrue(YandexSdkAuthRules.isTrustedProfile("login", "123", "", client))
    }

    @Test fun rejectsMissingIdentityOrWrongOAuthApplication() {
        assertFalse(YandexSdkAuthRules.isTrustedProfile("", "123", client, client))
        assertFalse(YandexSdkAuthRules.isTrustedProfile("login", "", client, client))
        assertFalse(YandexSdkAuthRules.isTrustedProfile("login", "123", "another", client))
        assertFalse(YandexSdkAuthRules.isTrustedProfile("login", "123", "", ""))
    }

    @Test fun expiryIsBoundedAndCannotOverflow() {
        assertEquals(61_000L, YandexSdkAuthRules.expiryTimeMillis(1_000L, 60L))
        assertNull(YandexSdkAuthRules.expiryTimeMillis(1_000L, 0L))
        assertNull(YandexSdkAuthRules.expiryTimeMillis(1_000L, -1L))
        assertNull(YandexSdkAuthRules.expiryTimeMillis(Long.MAX_VALUE, 60L))
        assertEquals(
            365L * 24L * 60L * 60L * 1000L,
            YandexSdkAuthRules.expiryTimeMillis(0L, Long.MAX_VALUE)
        )
    }
}
