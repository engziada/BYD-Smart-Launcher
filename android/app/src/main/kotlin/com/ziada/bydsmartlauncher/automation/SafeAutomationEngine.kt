package com.ziada.bydsmartlauncher.automation

/**
 * A fail-closed, side-effect-free automation evaluator. It produces intents only.
 * NO Telegram or vehicle control commands are dispatched by this class.
 * The caller must provide actual VERIFIED vehicle telemetry (never raw FID candidates).
 */
internal enum class TriggerKind { SOC_BELOW, POWER_ON, POWER_OFF }
internal enum class ActionKind { NOTIFICATION_ONLY }
internal data class AutomationRule(
    val id: String,
    val kind: TriggerKind,
    val thresholdPercent: Double? = null,
    val enabled: Boolean = true,
    val oncePerTrip: Boolean = true,
    val cooldownMs: Long = 60_000L,
    val action: ActionKind = ActionKind.NOTIFICATION_ONLY
)
internal data class VerifiedSignal(
    val timestampMs: Long,
    val verified: Boolean,
    val socPercent: Double? = null,
    val powerOn: Boolean? = null,
    val tripId: String? = null
)
internal data class ActionIntent(val ruleId: String, val kind: ActionKind)

/**
 * Event edges never fire from first sight of a state: a restarted app that sees a parked/on car
 * cannot invent a new power event. Power state is shared across all rules, so multiple rules
 * evaluate the SAME transition consistently. A real implementation must persist last state,
 * rule journals and trip identifiers across process death before enabling actions.
 */
internal class SafeAutomationEngine {
    private data class RuleState(
        var lastMatched: Boolean? = null,
        var lastFiredAt: Long? = null,
        var lastTripId: String? = null
    )

    private val states = mutableMapOf<String, RuleState>()
    private var lastPower: Boolean? = null
    private var lastTimestamp: Long? = null

    fun reset() {
        states.clear()
        lastPower = null
        lastTimestamp = null
    }

    fun evaluate(rules: List<AutomationRule>, input: VerifiedSignal): List<ActionIntent> {
        if (!input.verified || input.timestampMs < 0L) return emptyList()
        if (lastTimestamp?.let { input.timestampMs <= it } == true) return emptyList()
        val last = lastPower
        val powerChanged = last != null && input.powerOn != null && last != input.powerOn
        val outputs = mutableListOf<ActionIntent>()
        for (rule in rules) {
            if (rule.id.isBlank() || !rule.enabled || rule.action != ActionKind.NOTIFICATION_ONLY ||
                rule.cooldownMs < 0) continue
            val state = states.getOrPut(rule.id) { RuleState() }
            val matches = when (rule.kind) {
                TriggerKind.SOC_BELOW -> {
                    val soc = input.socPercent
                    val threshold = rule.thresholdPercent
                    soc != null && soc.isFinite() && soc in 0.0..100.0 &&
                        threshold != null && threshold.isFinite() && threshold in 0.0..100.0 &&
                        soc <= threshold
                }
                TriggerKind.POWER_ON -> powerChanged && input.powerOn == true
                TriggerKind.POWER_OFF -> powerChanged && input.powerOn == false
            }
            // SOC uses a false->true transition. Power triggers are already edges.
            val edge = when (rule.kind) {
                TriggerKind.SOC_BELOW -> matches && state.lastMatched == false
                else -> matches
            }
            state.lastMatched = matches
            if (!edge) continue

            // Unidentified trip must not produce a once-per-trip action.
            if (rule.oncePerTrip && input.tripId.isNullOrBlank()) continue
            if (rule.oncePerTrip && state.lastTripId == input.tripId) continue
            val firedAt = state.lastFiredAt
            if (firedAt != null && input.timestampMs - firedAt < rule.cooldownMs) continue
            outputs += ActionIntent(rule.id, rule.action)
            state.lastFiredAt = input.timestampMs
            state.lastTripId = input.tripId
        }
        if (input.powerOn != null) lastPower = input.powerOn
        lastTimestamp = input.timestampMs
        return outputs
    }
}
