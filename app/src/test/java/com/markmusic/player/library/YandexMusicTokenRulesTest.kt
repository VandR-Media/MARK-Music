package com.markmusic.player.library

import org.junit.Assert.assertEquals
import org.junit.Test

class YandexMusicTokenRulesTest {
    private val musicClient = "b050d96e5d604629810dac08d122c698"

    @Test fun recognizesSameUserAcrossTwoAuthorizedYandexApps() {
        assertEquals(
            YandexMusicIdentity.SAME_ACCOUNT,
            YandexMusicTokenRules.classify(musicClient, "12345", "12345")
        )
    }

    @Test fun catchesOAuthBrowserSigningIntoAnotherYandexAccount() {
        assertEquals(
            YandexMusicIdentity.DIFFERENT_ACCOUNT,
            YandexMusicTokenRules.classify(musicClient, "12345", "23456")
        )
    }

    @Test fun doesNotClaimSameAccountWhenLoginSdkIsDisconnected() {
        assertEquals(
            YandexMusicIdentity.ACCOUNT_NOT_COMPARED,
            YandexMusicTokenRules.classify(musicClient, "12345", null)
        )
        assertEquals(
            YandexMusicIdentity.ACCOUNT_NOT_COMPARED,
            YandexMusicTokenRules.classify(musicClient, "12345", "")
        )
    }

    @Test fun neverAcceptsOtherOAuthClientsOrEmptyProfile() {
        for (client in listOf("", "cfaacee8ff6d4941a5966d00c53a61de")) {
            assertEquals(
                YandexMusicIdentity.INVALID_CLIENT_OR_PROFILE,
                YandexMusicTokenRules.classify(client, "12345", "12345")
            )
        }
        assertEquals(
            YandexMusicIdentity.INVALID_CLIENT_OR_PROFILE,
            YandexMusicTokenRules.classify(musicClient, "", "12345")
        )
    }
}
