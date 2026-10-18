package com.ziada.bydsmartlauncher.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Device-local, versioned rule drafts with fail-closed persistence.
 * The global master switch defaults OFF. Enabling a rule only changes its saved definition:
 * NO automatic events are received and NO notification or vehicle command is dispatched.
 */
internal class LocalAutomationRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("automation_rules_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun status(): Map<String, Any?> = mapOf(
        "ok" to true,
        "masterEnabled" to prefs.getBoolean("master_enabled", false),
        "executionConnected" to false,
        "verifiedVehicleEvents" to false,
        "rules" to load().map { it.asMap() },
        "limit" to RulePolicy.MAX_RULES,
    )

    @Synchronized
    fun add(kind: String, threshold: Double?): Map<String, Any?> {
        val error = RulePolicy.validate(kind, threshold)
        if (error != null) return mapOf("ok" to false, "error" to error)
        val existing = load()
        if (existing.size >= RulePolicy.MAX_RULES) return mapOf("ok" to false, "error" to "rule_limit")
        val rule = SavedRule(
            id = UUID.randomUUID().toString(),
            kind = kind,
            thresholdPercent = threshold,
            enabled = false,
            oncePerTrip = true,
            cooldownMs = 60_000L,
        )
        if (!save(existing + rule)) return mapOf("ok" to false, "error" to "storage_error")
        return status()
    }

    @Synchronized
    fun toggle(id: String, enabled: Boolean): Map<String, Any?> {
        if (!RulePolicy.validId(id)) return mapOf("ok" to false, "error" to "invalid_id")
        val existing = load()
        if (existing.none { it.id == id }) return mapOf("ok" to false, "error" to "rule_not_found")
        if (!save(existing.map { if (it.id == id) it.copy(enabled = enabled) else it })) {
            return mapOf("ok" to false, "error" to "storage_error")
        }
        return status()
    }

    @Synchronized
    fun remove(id: String): Map<String, Any?> {
        if (!RulePolicy.validId(id)) return mapOf("ok" to false, "error" to "invalid_id")
        val existing = load()
        if (existing.none { it.id == id }) return mapOf("ok" to false, "error" to "rule_not_found")
        if (!save(existing.filterNot { it.id == id })) return mapOf("ok" to false, "error" to "storage_error")
        return status()
    }

    @Synchronized
    fun setMaster(enabled: Boolean): Map<String, Any?> {
        if (!prefs.edit().putBoolean("master_enabled", enabled).commit()) {
            return mapOf("ok" to false, "error" to "storage_error")
        }
        return status()
    }

    private fun load(): List<SavedRule> = try {
        val json = JSONArray(prefs.getString("rules_json", "[]") ?: "[]")
        if (json.length() > RulePolicy.MAX_RULES) return emptyList()
        val result = mutableListOf<SavedRule>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            val kind = o.optString("kind")
            val threshold = if (o.isNull("thresholdPercent")) null else o.optDouble("thresholdPercent")
            val id = o.optString("id")
            if (!RulePolicy.validId(id) || RulePolicy.validate(kind, threshold) != null) return emptyList()
            if (result.any { it.id == id }) return emptyList()
            val cooldown = o.optLong("cooldownMs", 60_000L)
            if (cooldown !in 0L..86_400_000L) return emptyList()
            result += SavedRule(
                id = id,
                kind = kind,
                thresholdPercent = threshold,
                enabled = o.optBoolean("enabled", false),
                oncePerTrip = o.optBoolean("oncePerTrip", true),
                cooldownMs = cooldown,
            )
        }
        result
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(rules: List<SavedRule>): Boolean {
        val json = JSONArray()
        rules.forEach { rule ->
            json.put(JSONObject().apply {
                put("id", rule.id)
                put("kind", rule.kind)
                put("thresholdPercent", rule.thresholdPercent ?: JSONObject.NULL)
                put("enabled", rule.enabled)
                put("oncePerTrip", rule.oncePerTrip)
                put("cooldownMs", rule.cooldownMs)
            })
        }
        return prefs.edit().putString("rules_json", json.toString()).commit()
    }
}
