package com.ziada.bydsmartlauncher.automation

import org.junit.Assert.*
import org.junit.Test

class SafeAutomationEngineTest {
    @Test fun unverifiedSamplesNeverGenerateActionsOrPrimeState() {
        val engine = SafeAutomationEngine()
        val rule = AutomationRule("low", TriggerKind.SOC_BELOW, thresholdPercent = 25.0)
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(1, false, 40.0, true, "trip1")).isEmpty())
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(2, false, 10.0, true, "trip1")).isEmpty())
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(3, true, 10.0, true, "trip1")).isEmpty())
    }

    @Test fun socCrossingFiresOnlyOncePerTrip() {
        val engine = SafeAutomationEngine()
        val rule = AutomationRule("battery", TriggerKind.SOC_BELOW, thresholdPercent = 25.0, cooldownMs = 0L)
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(1, true, 30.0, true, "t1")).isEmpty())
        assertEquals(1, engine.evaluate(listOf(rule), VerifiedSignal(2, true, 24.0, true, "t1")).size)
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(3, true, 23.0, true, "t1")).isEmpty())
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(4, true, 30.0, true, "t1")).isEmpty())
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(5, true, 20.0, true, "t1")).isEmpty())
        engine.evaluate(listOf(rule), VerifiedSignal(6, true, 40.0, true, "t2"))
        assertEquals(1, engine.evaluate(listOf(rule), VerifiedSignal(7, true, 20.0, true, "t2")).size)
    }

    @Test fun powerOffOccursOnActualEdgeAndNotOnAppStartOrRepetition() {
        val engine = SafeAutomationEngine()
        val off = AutomationRule("off", TriggerKind.POWER_OFF, oncePerTrip = false, cooldownMs = 0L)
        assertTrue(engine.evaluate(listOf(off), VerifiedSignal(1, true, powerOn = false)).isEmpty())
        assertTrue(engine.evaluate(listOf(off), VerifiedSignal(2, true, powerOn = true)).isEmpty())
        assertEquals(1, engine.evaluate(listOf(off), VerifiedSignal(3, true, powerOn = false)).size)
        assertTrue(engine.evaluate(listOf(off), VerifiedSignal(4, true, powerOn = false)).isEmpty())
        engine.evaluate(listOf(off), VerifiedSignal(5, true, powerOn = true))
        assertEquals(1, engine.evaluate(listOf(off), VerifiedSignal(6, true, powerOn = false)).size)
    }

    @Test fun repeatedTimestampOrMissingTripCannotFire() {
        val engine = SafeAutomationEngine()
        val rule = AutomationRule("soc", TriggerKind.SOC_BELOW, thresholdPercent = 25.0)
        engine.evaluate(listOf(rule), VerifiedSignal(10, true, 60.0, tripId = null))
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(10, true, 10.0, tripId = "A")).isEmpty())
        assertTrue(engine.evaluate(listOf(rule), VerifiedSignal(11, true, 10.0, tripId = null)).isEmpty())
    }

    @Test fun invalidThresholdAndImplausibleSocCannotFire() {
        val e = SafeAutomationEngine()
        val rule = AutomationRule("bad", TriggerKind.SOC_BELOW, thresholdPercent = Double.NaN)
        e.evaluate(listOf(rule), VerifiedSignal(1, true, 35.0, tripId = "t"))
        assertTrue(e.evaluate(listOf(rule), VerifiedSignal(2, true, 10.0, tripId = "t")).isEmpty())
        val validRule = rule.copy(thresholdPercent = 25.0)
        e.reset()
        e.evaluate(listOf(validRule), VerifiedSignal(3, true, -20.0, tripId = "t"))
        assertEquals(1, e.evaluate(listOf(validRule), VerifiedSignal(4, true, 20.0, tripId = "t")).size)
    }

    @Test fun cooldownAndDisabledRuleAreRespected() {
        val e = SafeAutomationEngine()
        val rule = AutomationRule("off", TriggerKind.POWER_OFF, oncePerTrip = false, cooldownMs = 10_000L)
        e.evaluate(listOf(rule), VerifiedSignal(1, true, powerOn = true))
        assertEquals(1, e.evaluate(listOf(rule), VerifiedSignal(2, true, powerOn = false)).size)
        e.evaluate(listOf(rule), VerifiedSignal(3, true, powerOn = true))
        assertTrue(e.evaluate(listOf(rule), VerifiedSignal(4, true, powerOn = false)).isEmpty())
        assertTrue(e.evaluate(listOf(rule.copy(enabled = false)), VerifiedSignal(10_100, true, powerOn = true)).isEmpty())
    }
}
