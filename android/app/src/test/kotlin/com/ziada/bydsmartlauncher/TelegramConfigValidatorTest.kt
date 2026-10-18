package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class TelegramConfigValidatorTest {
    @Test fun acceptsNormalBotTokenAndChatId() {
        assertTrue(TelegramConfigValidator.isValidToken("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef"))
        assertTrue(TelegramConfigValidator.isValidChatId("123456789"))
        assertTrue(TelegramConfigValidator.isValidChatId("-1001234567890"))
    }

    @Test fun rejectsMalformedCredentials() {
        assertFalse(TelegramConfigValidator.isValidToken(""))
        assertFalse(TelegramConfigValidator.isValidToken("bad-token"))
        assertFalse(TelegramConfigValidator.isValidToken("123456:with spaces"))
        assertFalse(TelegramConfigValidator.isValidChatId("0"))
        assertFalse(TelegramConfigValidator.isValidChatId("abc"))
        assertFalse(TelegramConfigValidator.isValidChatId("999999999999999999999999999"))
    }
}
