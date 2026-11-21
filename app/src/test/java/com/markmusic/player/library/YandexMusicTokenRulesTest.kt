package com.markmusic.player.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMusicTokenRulesTest {
    @Test fun acceptsOnlyOurSecondOAuthClientWithNonEmptyAccountId() {
        assertTrue(YandexMusicTokenRules.matchesRegisteredMusicClient(
            "b050d96e5d604629810dac08d122c698", "account-id"
        ))
        assertFalse(YandexMusicTokenRules.matchesRegisteredMusicClient(
            "cfaacee8ff6d4941a5966d00c53a61de", "account-id"
        ))
        assertFalse(YandexMusicTokenRules.matchesRegisteredMusicClient(
            "b050d96e5d604629810dac08d122c698", ""
        ))
        assertFalse(YandexMusicTokenRules.matchesRegisteredMusicClient("", "account-id"))
    }
}
