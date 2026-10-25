package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class ManualStatusPolicyTest {
    private fun fresh(now: Long = 100_000L) = mapOf<String, Any?>(
        "ok" to true,
        "catalogMatched" to true,
        "sessionValid" to true,
        "streamState" to "candidate",
        "firmwareVerified" to false,
        "vehicleWriteAuthorized" to false,
        "capturedAtEpochMs" to now - 1_000L,
        "socCandidatePercent" to 76.0,
        "odometerCandidateKm" to 42315.7
    )

    @Test fun canCreateExplicitPrivateMessageFromFreshCandidateOnly() {
        val text = ManualStatusPolicy.message(fresh(), 100_000L)
        assertNotNull(text)
        assertTrue(text!!.contains("76%"))
        assertTrue(text.contains("42,315.7 km"))
        assertTrue(text.contains("manually requested"))
        assertTrue(text.contains("not independently OEM-certified"))
        assertFalse(text.contains("VIN"))
        assertFalse(text.contains("charging complete"))
    }

    @Test fun rejectsStaleFakeOrFromAnotherSession() {
        val now = 100_000L
        val cases = listOf(
            fresh() + ("capturedAtEpochMs" to (now - 15_001L)),
            fresh() + ("capturedAtEpochMs" to (now + 1L)),
            fresh() + ("sessionValid" to false),
            fresh() + ("streamState" to "stopped"),
            fresh() + ("streamState" to "stale"),
            fresh() + ("catalogMatched" to false),
            fresh() + ("ok" to false),
            fresh() + ("firmwareVerified" to true),
            fresh() + ("vehicleWriteAuthorized" to true),
            fresh() + ("socCandidatePercent" to 110.0),
            fresh() + ("socCandidatePercent" to Double.NaN),
            fresh() + ("odometerCandidateKm" to Double.POSITIVE_INFINITY),
            fresh() + ("odometerCandidateKm" to -1.0),
            fresh() - "capturedAtEpochMs"
        )
        cases.forEach { assertNull(ManualStatusPolicy.message(it, now)) }
        assertNotNull(ManualStatusPolicy.message(
            fresh() + ("capturedAtEpochMs" to (now - 15_000L)), now))
    }
}
