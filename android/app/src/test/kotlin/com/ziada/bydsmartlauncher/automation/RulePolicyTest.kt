package com.ziada.bydsmartlauncher.automation

import org.junit.Assert.*
import org.junit.Test

class RulePolicyTest {
    @Test fun acceptsCorrectNotificationRules() {
        assertNull(RulePolicy.validate("SOC_BELOW", 25.0))
        assertNull(RulePolicy.validate("POWER_ON", null))
        assertNull(RulePolicy.validate("POWER_OFF", null))
    }
    @Test fun rejectsUnsafeOrUndefinedRuleKinds() {
        assertEquals("invalid_trigger", RulePolicy.validate("COMMAND_SET_WINDOWS", null))
        assertEquals("invalid_soc_threshold", RulePolicy.validate("SOC_BELOW", null))
        assertEquals("invalid_soc_threshold", RulePolicy.validate("SOC_BELOW", Double.NaN))
        assertEquals("invalid_soc_threshold", RulePolicy.validate("SOC_BELOW", 0.0))
        assertEquals("invalid_soc_threshold", RulePolicy.validate("SOC_BELOW", 100.0))
        assertEquals("threshold_not_applicable", RulePolicy.validate("POWER_ON", 23.0))
    }
    @Test fun onlyReasonableIdsAreAccepted() {
        assertTrue(RulePolicy.validId("3f41430a-2d7d-4c86-a8ae-946b7485a101"))
        assertFalse(RulePolicy.validId(""))
        assertFalse(RulePolicy.validId("../../etc/passwd"))
        assertFalse(RulePolicy.validId("anything!"))
        assertEquals(24, RulePolicy.MAX_RULES)
    }
}
