package com.ziada.bydsmartlauncher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Explicitly opt-in Telegram bot. No background traffic and NO vehicle events in this phase.
 * Bot credentials are encrypted with an on-device AndroidKeystore AES-GCM key and never returned
 * to Flutter or logged. Device backups are disabled for the app.
 */
internal class TelegramBotService(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("telegram_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun read(): Pair<String, String>? {
        return try {
        val stored = prefs.getString(PREF_CREDENTIALS, null) ?: return null
        val decoded = Base64.decode(stored, Base64.NO_WRAP)
        if (decoded.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decoded.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(decoded.copyOfRange(12, decoded.size)), Charsets.UTF_8))
        val token = json.getString("token")
        val chatId = json.getString("chatId")
        if (TelegramConfigValidator.isValidToken(token) && TelegramConfigValidator.isValidChatId(chatId)) {
            token to chatId
        } else null
        } catch (_: Exception) {
            null // e.g. restored ciphertext with lost Keystore key; never disclose secrets.
        }
    }

    @Synchronized
    fun status(): Map<String, Any?> = mapOf(
        "configured" to (read() != null),
        "notificationsEnabled" to false, // No vehicle triggers in this phase.
        "powerStateIntegration" to "not_implemented",
    )

    @Synchronized
    fun configure(token: String, chatId: String): Map<String, Any?> {
        if (!TelegramConfigValidator.isValidToken(token)) return mapOf("ok" to false, "error" to "invalid_token")
        if (!TelegramConfigValidator.isValidChatId(chatId)) return mapOf("ok" to false, "error" to "invalid_chat_id")
        return try {
            val json = JSONObject()
                .put("token", token.trim())
                .put("chatId", chatId.trim())
                .toString()
                .toByteArray(Charsets.UTF_8)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val encrypted = iv + cipher.doFinal(json)
            val saved = prefs.edit().putString(
                PREF_CREDENTIALS, Base64.encodeToString(encrypted, Base64.NO_WRAP)
            ).commit()
            mapOf("ok" to saved, "configured" to saved)
        } catch (_: Exception) {
            mapOf("ok" to false, "error" to "secure_storage_unavailable")
        }
    }

    @Synchronized
    fun clear(): Map<String, Any?> {
        prefs.edit().remove(PREF_CREDENTIALS).apply()
        return mapOf("ok" to true, "configured" to false)
    }

    /**
     * Only called by a direct user press on "Send test". No auto retries or network requests
     * on configuration, app launch, or car start. The URL is fixed to Telegram's official host.
     */
    @Synchronized
    fun sendTest(): Map<String, Any?> {
        val (token, chatId) = read()
            ?: return mapOf("ok" to false, "error" to "not_configured")
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val payload = "chat_id=" + URLEncoder.encode(chatId, "UTF-8") +
                "&text=" + URLEncoder.encode("BYD Smart Launcher: Telegram test successful.", "UTF-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status == 200) mapOf("ok" to true)
            else mapOf("ok" to false, "error" to "telegram_http_$status")
        } catch (_: Exception) {
            mapOf("ok" to false, "error" to "telegram_network_error")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val KEY_ALIAS = "byd_smart_launcher_telegram_v1"
        const val PREF_CREDENTIALS = "credentials_v1"
    }
}
