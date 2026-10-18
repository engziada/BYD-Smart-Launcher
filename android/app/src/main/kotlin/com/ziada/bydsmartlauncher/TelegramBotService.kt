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
    private val outbox = TelegramOutbox(appContext)

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
        "pendingMessages" to outbox.count(),
        "automaticRetryEnabled" to false,
        "deliverySemantics" to "at_least_once",
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
            if (saved) outbox.clear() // New destination must not inherit pending tests.
            mapOf("ok" to saved, "configured" to saved)
        } catch (_: Exception) {
            mapOf("ok" to false, "error" to "secure_storage_unavailable")
        }
    }

    @Synchronized
    fun clear(): Map<String, Any?> {
        val cleared = prefs.edit().remove(PREF_CREDENTIALS).commit()
        val removedPending = outbox.clear()
        return mapOf("ok" to (cleared && removedPending), "configured" to false)
    }

    /**
     * Explicit test only. Persist the fixed diagnostic event BEFORE contacting the network.
     * A failure remains queued, and the user may press "Retry" later. No background
     * delivery and no real car-triggered messages are enabled in this milestone.
     */
    @Synchronized
    fun sendTest(): Map<String, Any?> {
        if (read() == null) return mapOf("ok" to false, "error" to "not_configured")
        if (!outbox.enqueueDiagnostic(System.currentTimeMillis())) {
            return mapOf("ok" to false, "error" to "queue_storage_error")
        }
        return retryPending()
    }

    @Synchronized
    fun outboxStatus(): Map<String, Any?> = mapOf(
        "ok" to true,
        "pendingMessages" to outbox.count(),
        "nextRetryAtMs" to outbox.pending()?.nextAllowedMs,
        "automaticRetryEnabled" to false,
    )

    @Synchronized
    fun clearPending(): Map<String, Any?> {
        val cleared = outbox.clear()
        return mapOf("ok" to cleared, "pendingMessages" to outbox.count())
    }

    /** Attempts at most ONE message, respecting backoff and using no arbitrary message text. */
    @Synchronized
    fun retryPending(): Map<String, Any?> {
        val pending = outbox.pending() ?: return mapOf(
            "ok" to true, "sent" to false, "pendingMessages" to 0
        )
        val credentials = read() ?: return mapOf(
            "ok" to false, "error" to "not_configured",
            "pendingMessages" to outbox.count()
        )
        val now = System.currentTimeMillis()
        if (pending.nextAllowedMs > now) {
            return mapOf(
                "ok" to false, "error" to "retry_backoff",
                "retryAfterSeconds" to ((pending.nextAllowedMs - now + 999L) / 1000L),
                "pendingMessages" to outbox.count(),
            )
        }
        val failure = sendTelegramText(credentials.first, credentials.second)
        if (failure == null) {
            val ack = outbox.markSent(pending.id)
            return mapOf(
                "ok" to ack, "sent" to ack, "pendingMessages" to outbox.count(),
                "error" to if (ack) null else "queue_ack_error",
            )
        }
        outbox.markFailed(pending.id, now)
        return mapOf(
            "ok" to false, "sent" to false, "error" to failure,
            "pendingMessages" to outbox.count(),
            "retryAfterSeconds" to (TelegramRetryPolicy.delayMs(pending.attempts + 1) / 1000L),
        )
    }

    /** Null on confirmed API success, non-sensitive failure code otherwise. */
    private fun sendTelegramText(token: String, chatId: String): String? {
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
                "&text=" + URLEncoder.encode("BYD Smart Launcher: Telegram diagnostic test.", "UTF-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code != 200) return "telegram_http_$code"
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(4096)
            }
            if (JSONObject(response).optBoolean("ok", false)) null else "telegram_api_rejected"
        } catch (_: Exception) {
            "telegram_network_error"
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val KEY_ALIAS = "byd_smart_launcher_telegram_v1"
        const val PREF_CREDENTIALS = "credentials_v1"
    }
}
