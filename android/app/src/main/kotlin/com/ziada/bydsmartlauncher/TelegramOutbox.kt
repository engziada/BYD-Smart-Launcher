package com.ziada.bydsmartlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Crash-persistent, bounded delivery queue. Each item identifies a fixed text,
 * with no chat, token, position, VIN or raw telemetry in storage.
 *
 * This outbox is AT-LEAST-ONCE, not exactly-once: process death after Telegram accepts a
 * message but before ack can result in resending.
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
    const val LIMIT = 32
    private val eventId = Regex("[a-f0-9]{64}")
    val vehicleKinds = setOf("POWER_ON", "POWER_OFF", "CHARGE_STARTED", "CHARGE_FINISHED", "SOC_BELOW")
    fun delayMs(attempts: Int): Long {
        val bounded = (attempts - 1).coerceIn(0, 8)
        return (15_000L shl bounded).coerceAtMost(3_600_000L)
    }
    fun valid(item: PendingTelegram): Boolean =
        ((item.id == DIAGNOSTIC_ID && item.kind == DIAGNOSTIC_KIND) ||
            (eventId.matches(item.id) && item.kind in vehicleKinds)) &&
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
    fun enqueueVerified(id: String, kind: String, nowMs: Long): Boolean {
        val item = PendingTelegram(id, kind, 0, nowMs, 0L)
        if (!TelegramRetryPolicy.valid(item) || kind !in TelegramRetryPolicy.vehicleKinds) return false
        val queued = load()
        if (queued.any { it.id == id } || id in acknowledgements()) return true
        return store(queued + item)
    }

    @Synchronized
    fun pending(): PendingTelegram? = load().minByOrNull { it.nextAllowedMs }

    @Synchronized
    fun markSent(id: String): Boolean {
        val remaining = load().filterNot { it.id == id }
        if (id == TelegramRetryPolicy.DIAGNOSTIC_ID) return store(remaining)
        val recent = (acknowledgements().filterNot { it == id } + id).takeLast(128)
        return store(remaining, prefs.edit().putString("acknowledged", recent.joinToString(",")))
    }

    @Synchronized
    fun markFailed(id: String, nowMs: Long): Boolean = store(load().map { item ->
        if (item.id != id) item else {
            val attempts = (item.attempts + 1).coerceAtMost(10000)
            item.copy(attempts = attempts, nextAllowedMs = nowMs + TelegramRetryPolicy.delayMs(attempts))
        }
    })

    @Synchronized
    fun clear(): Boolean = prefs.edit().remove("messages").remove("acknowledged").commit()

    @Synchronized
    fun clearVehicleEvents(): Boolean = store(load().filter { it.kind == TelegramRetryPolicy.DIAGNOSTIC_KIND })

    private fun acknowledgements(): List<String> =
        prefs.getString("acknowledged", "").orEmpty().split(',').filter { it.length == 64 }.takeLast(128)

    private fun load(): List<PendingTelegram> {
        return try {
            val raw = prefs.getString("messages", "[]") ?: "[]"
            if (raw.length > 12_000) return emptyList()
            val values = JSONArray(raw)
            if (values.length() > TelegramRetryPolicy.LIMIT) return emptyList()
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

    private fun store(
        messages: List<PendingTelegram>,
        editor: android.content.SharedPreferences.Editor = prefs.edit()
    ): Boolean {
        if (messages.size > TelegramRetryPolicy.LIMIT ||
            messages.any { !TelegramRetryPolicy.valid(it) }) return false
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
        return editor.putString("messages", array.toString()).commit()
    }
}
