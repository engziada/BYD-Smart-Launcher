package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class CandidateStreamPolicyTest {
    private fun good(soc: Double = 75.0, odo: Double = 42069.9, time: Long = 1_000L) =
        mapOf<String, Any?>(
            "ok" to true,
            "catalogMatched" to true,
            "socCandidatePercent" to soc,
            "odometerCandidateKm" to odo,
            "capturedAtEpochMs" to time,
            "firmwareVerified" to false,
            "vehicleWriteAuthorized" to false
        )

    @Test fun acceptsFreshFirmwareResolvedSamplesOnlyAsUnverifiedCandidates() {
        val p = CandidateStreamPolicy()
        val first = p.inspect(good(), 1_200L)
        assertEquals("candidate", first["streamState"])
        assertEquals(75.0, first["socCandidatePercent"])
        assertEquals(42069.9, first["odometerCandidateKm"])
        assertEquals(1L, first["sequence"])
        assertEquals(false, first["firmwareVerified"])
        assertEquals(false, first["vehicleWriteAuthorized"])
        assertEquals(false, first["automaticEventEligible"])
        val next = p.inspect(good(soc = 74.0, odo = 42070.4, time = 6_000L), 6_100L)
        assertEquals("candidate", next["streamState"])
        assertEquals(2L, next["sequence"])
    }

    @Test fun rejectsOldDuplicateFutureInvalidAndUnresolvedSamples() {
        val cases = listOf(
            good(time = 1L),                          // age > 15 sec
            good(time = 21_000L),                     // time from future
            good(soc = 101.0, time = 2_000L),
            good(soc = Double.NaN, time = 2_000L),
            good(odo = -1.0, time = 2_000L),
            good(time = 2_000L) + ("catalogMatched" to false),
            good(time = 2_000L) + ("firmwareVerified" to true),
            good(time = 2_000L) + ("vehicleWriteAuthorized" to true),
            good(time = 2_000L) - "odometerCandidateKm"
        )
        for (sample in cases) {
            val p = CandidateStreamPolicy()
            val verdict = p.inspect(sample, 5_000L)
            assertEquals("stale", verdict["streamState"])
            assertFalse(verdict.containsKey("socCandidatePercent"))
            assertFalse(verdict.containsKey("odometerCandidateKm"))
            assertEquals(false, verdict["automaticEventEligible"])
        }
        val p = CandidateStreamPolicy()
        assertEquals("candidate", p.inspect(good(time = 100L), 110L)["streamState"])
        assertEquals("stale", p.inspect(good(time = 100L), 111L)["streamState"])
    }

    @Test fun neverKeepsStaleValuesAndStopsAfterThreeConsecutiveFailures() {
        val p = CandidateStreamPolicy()
        assertEquals("candidate", p.inspect(good(), 1_000L)["streamState"])
        for (count in 1..3) {
            val verdict = p.inspect(mapOf("ok" to false, "reason" to "adb_disconnected"), 1_500L)
            assertEquals(if (count == 3) "stopped" else "stale", verdict["streamState"])
            assertEquals(count, verdict["failureStreak"])
            assertNull(verdict["socCandidatePercent"])
            assertEquals(false, verdict["vehicleWriteAuthorized"])
        }
    }

    @Test fun recoveredFreshSampleResetsFailureCounterButNotSequence() {
        val p = CandidateStreamPolicy()
        p.inspect(good(time = 1_000L), 1_001L)
        p.inspect(mapOf("ok" to false), 1_500L)
        val recovered = p.inspect(good(time = 6_000L), 6_000L)
        assertEquals("candidate", recovered["streamState"])
        assertEquals(0, recovered["failureStreak"])
        assertEquals(2L, recovered["sequence"])
        p.reset()
        val restarted = p.inspect(good(time = 100L), 100L)
        assertEquals(1L, restarted["sequence"])
    }
}
