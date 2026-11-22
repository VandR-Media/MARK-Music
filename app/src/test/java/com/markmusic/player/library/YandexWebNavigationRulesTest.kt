package com.markmusic.player.library

import org.junit.Assert.*
import org.junit.Test

class YandexWebNavigationRulesTest {
    @Test fun onlyOfficialHttpsTopLevelHostsAreAllowed() {
        assertTrue(YandexWebNavigationRules.isAllowed("https://music.yandex.ru/"))
        assertTrue(YandexWebNavigationRules.isAllowed("https://passport.yandex.ru/auth"))
        assertTrue(YandexWebNavigationRules.isAllowed("https://oauth.yandex.ru/authorize"))
        assertFalse(YandexWebNavigationRules.isAllowed("http://music.yandex.ru/"))
        assertFalse(YandexWebNavigationRules.isAllowed("https://music.yandex.ru.attacker.org/"))
        assertFalse(YandexWebNavigationRules.isAllowed("https://fake-yandex.ru/"))
        assertFalse(YandexWebNavigationRules.isAllowed("javascript:alert(1)"))
        assertFalse(YandexWebNavigationRules.isAllowed("file:///sdcard/test"))
        assertFalse(YandexWebNavigationRules.isAllowed("https://user:pass@music.yandex.ru/"))
        assertFalse(YandexWebNavigationRules.isAllowed("https://music.yandex.ru:444/"))
    }

    @Test fun externalNavigationRequiresHttps() {
        assertTrue(YandexWebNavigationRules.isExternalHttps("https://example.com/"))
        assertFalse(YandexWebNavigationRules.isExternalHttps("intent://link"))
        assertFalse(YandexWebNavigationRules.isExternalHttps("http://example.com"))
    }
}
