package com.ziada.bydsmartlauncher

/**
 * Bot API input validation. Pure functions so tests never require bot secrets, network access,
 * or an Android Keystore. A Telegram token must NEVER appear in a status or exception.
 */
internal object TelegramConfigValidator {
    private val token = Regex("^[0-9]{6,12}:[A-Za-z0-9_-]{20,100}$")
    private val chat = Regex("^-?[0-9]{1,19}$")

    fun isValidToken(value: String): Boolean = token.matches(value.trim())

    fun isValidChatId(value: String): Boolean {
        val s = value.trim()
        return chat.matches(s) && s.toLongOrNull()?.let { it != 0L } == true
    }
}
