package com.markmusic.player.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMusicUserOAuthRulesTest {
    @Test fun newClientIsExactlyTheUserAuthorizationApp() {
        assertTrue(YandexMusicUserOAuthRules.CLIENT_ID.matches(Regex("[a-f0-9]{32}")))
        assertTrue(YandexMusicUserOAuthRules.CLIENT_ID != YandexMusicOAuthRules.CLIENT_ID)
    }

    @Test fun doesNotAcceptAnyOtherClientOrDifferentYandexUser() {
        val id = "u123"
        assertTrue(YandexMusicUserOAuthRules.matchesAccount(
            YandexMusicUserOAuthRules.CLIENT_ID, id, id
        ))
        assertFalse(YandexMusicUserOAuthRules.matchesAccount(
            YandexMusicOAuthRules.CLIENT_ID, id, id
        ))
        assertFalse(YandexMusicUserOAuthRules.matchesAccount(
            YandexMusicUserOAuthRules.CLIENT_ID, id, "another-user"
        ))
        assertFalse(YandexMusicUserOAuthRules.matchesAccount(
            YandexMusicUserOAuthRules.CLIENT_ID, "", id
        ))
        assertFalse(YandexMusicUserOAuthRules.matchesAccount(
            YandexMusicUserOAuthRules.CLIENT_ID, id, null
        ))
    }
}
