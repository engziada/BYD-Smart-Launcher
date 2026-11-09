package com.ziada.bydsmartlauncher.automation

import java.security.MessageDigest

/** Converts only explicitly verified native events into fixed-message outbox entries. */
internal class VehicleAlertRouter {
    private val engine = SafeAutomationEngine()
    private var previousAt: Long? = null

    fun reset() {
        engine.reset()
        previousAt = null
    }

    fun observe(
        input: VerifiedSignal,
        nowMs: Long,
        rules: List<AutomationRule>,
        enqueue: (String, String, Long) -> Boolean
    ) {
        if (!input.verified || input.timestampMs !in (nowMs - 15_000)..nowMs || rules.isEmpty()) {
            engine.reset()
            previousAt = null
            return
        }
        val previous = previousAt
        if (previous != null && input.timestampMs <= previous) return
        if (previous != null && input.timestampMs - previous > 15_000) engine.reset()
        previousAt = input.timestampMs
        val triggers = rules.associate { it.id to it.kind }
        for (intent in engine.evaluate(rules, input)) {
            val kind = triggers[intent.ruleId] ?: continue
            val key = "${intent.ruleId}:${kind.name}:${input.timestampMs}"
            val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            enqueue(hash, kind.name, nowMs)
        }
    }
}
