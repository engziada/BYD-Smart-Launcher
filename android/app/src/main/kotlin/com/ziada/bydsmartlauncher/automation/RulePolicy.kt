package com.ziada.bydsmartlauncher.automation

/**
 * Strict validation for locally configured notification rules. Validation is pure and JVM-tested.
 * Rules remain inert until the verified vehicle event source and dispatcher are commissioned.
 */
internal object RulePolicy {
    const val MAX_RULES = 24
    val kinds = setOf("SOC_BELOW", "POWER_ON", "POWER_OFF")

    fun validate(kind: String, threshold: Double?): String? = when {
        kind !in kinds -> "invalid_trigger"
        kind == "SOC_BELOW" && (threshold == null || !threshold.isFinite() ||
            threshold !in 1.0..99.0) -> "invalid_soc_threshold"
        kind != "SOC_BELOW" && threshold != null -> "threshold_not_applicable"
        else -> null
    }

    fun validId(id: String): Boolean =
        id.length in 1..64 && id.all { it.isLetterOrDigit() || it == '-' }
}

internal data class SavedRule(
    val id: String,
    val kind: String,
    val thresholdPercent: Double?,
    val enabled: Boolean,
    val oncePerTrip: Boolean,
    val cooldownMs: Long,
) {
    fun asMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "kind" to kind,
        "thresholdPercent" to thresholdPercent,
        "enabled" to enabled,
        "oncePerTrip" to oncePerTrip,
        "cooldownMs" to cooldownMs,
        "action" to "NOTIFICATION_ONLY",
    )
}
