package com.ziada.bydsmartlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A small crash-persistent outbox, initially supporting ONLY an explicit, fixed-content
 * diagnostic TEST. No car events or arbitrary message body accepted from Flutter.
 * Stored content is a type enum, never token, chatId, location, VIN or car data.
 *
 * This outbox is AT-LEAST-ONCE, not exactly-once: process death after Telegram accepts a
 * message but before ack can result in resending. No background retry is implemented yet.
 */
internal data class PendingTelegram(
    val id: String,
    val kind: String,
    val attempts: Int,
    val createdAtMs: Long,
    val nextAllowedMs: Long,
)

internal object TelegramRetryPolicy {
    const val DIAGNOSTIC_ID = "diagnostic-test"
    const val DIAGNOSTIC_KIND = "TEST"
    fun delayMs(attempts: Int): Long {
        val bounded = (attempts - 1).coerceIn(0, 8)
        return (15_000L shl bounded).coerceAtMost(3_600_000L)
    }
    fun valid(item: PendingTelegram): Boolean =
        item.id == DIAGNOSTIC_ID && item.kind == DIAGNOSTIC_KIND &&
            item.attempts in 0..10000 && item.createdAtMs >= 0 &&
            item.nextAllowedMs >= 0

    fun dedupe(items: List<PendingTelegram>, nowMs: Long): List<PendingTelegram> {
        if (items.any { it.id == DIAGNOSTIC_ID }) return items
        return items + PendingTelegram(DIAGNOSTIC_ID, DIAGNOSTIC_KIND, 0, nowMs, 0L)
    }
}

internal class TelegramOutbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("telegram_outbox_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun count(): Int = load().size

    @Synchronized
    fun enqueueDiagnostic(nowMs: Long): Boolean =
        store(TelegramRetryPolicy.dedupe(load(), nowMs))

    @Synchronized
    fun pending(): PendingTelegram? = load().firstOrNull()

    @Synchronized
    fun markSent(id: String): Boolean = store(load().filterNot { it.id == id })

    @Synchronized
    fun markFailed(id: String, nowMs: Long): Boolean = store(load().map { item ->
        if (item.id != id) item else {
            val attempts = (item.attempts + 1).coerceAtMost(10000)
            item.copy(attempts = attempts, nextAllowedMs = nowMs + TelegramRetryPolicy.delayMs(attempts))
        }
    })

    @Synchronized
    fun clear(): Boolean = prefs.edit().remove("messages").commit()

    private fun load(): List<PendingTelegram> {
        return try {
            val raw = prefs.getString("messages", "[]") ?: "[]"
            if (raw.length > 12_000) return emptyList()
            val values = JSONArray(raw)
            if (values.length() > 1) return emptyList()
            val result = mutableListOf<PendingTelegram>()
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                val record = PendingTelegram(
                    id = item.getString("id"),
                    kind = item.getString("kind"),
                    attempts = item.getInt("attempts"),
                    createdAtMs = item.getLong("createdAtMs"),
                    nextAllowedMs = item.getLong("nextAllowedMs"),
                )
                if (!TelegramRetryPolicy.valid(record)) return emptyList()
                result += record
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun store(messages: List<PendingTelegram>): Boolean {
        if (messages.size > 1 || messages.any { !TelegramRetryPolicy.valid(it) }) return false
        val array = JSONArray()
        for (item in messages) {
            array.put(JSONObject().apply {
                put("id", item.id)
                put("kind", item.kind)
                put("attempts", item.attempts)
                put("createdAtMs", item.createdAtMs)
                put("nextAllowedMs", item.nextAllowedMs)
            })
        }
        return prefs.edit().putString("messages", array.toString()).commit()
    }
}
