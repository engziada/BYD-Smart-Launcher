package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class MotionCandidatePolicyTest {
    @Test fun returnsReadOnlyRawValuesWithoutAnyPermissionOrUnitClaims() {
        val result = MotionCandidatePolicy.interpret(0.0, 1)
        assertEquals(true, result["ok"])
        assertEquals(0.0, result["speedRawFloatCandidate"])
        assertEquals(1, result["gearRawIntCandidate"])
        assertEquals("raw_candidates", result["status"])
        assertEquals(false, result["speedUnitsVerified"])
        assertEquals(false, result["gearEnumVerified"])
        assertEquals(false, result["stationaryStateVerified"])
        assertEquals(false, result["vehicleWriteAuthorized"])
        assertEquals(false, result["automaticEventEligible"])
    }

    @Test fun invalidParcelsAndOutOfRangeRawValuesDoNotBecomeControlData() {
        val bad = listOf(
            MotionCandidatePolicy.interpret(null, 2),
            MotionCandidatePolicy.interpret(1.0, null),
            MotionCandidatePolicy.interpret(-1.0, 2),
            MotionCandidatePolicy.interpret(301.0, 2),
            MotionCandidatePolicy.interpret(Double.NaN, 2),
            MotionCandidatePolicy.interpret(Double.POSITIVE_INFINITY, 2),
            MotionCandidatePolicy.interpret(0.0, -1),
            MotionCandidatePolicy.interpret(0.0, 65535)
        )
        for (v in bad) {
            assertEquals(false, v["ok"])
            assertEquals("incomplete_or_invalid_raw_values", v["status"])
            assertEquals(false, v["stationaryStateVerified"])
            assertEquals(false, v["vehicleWriteAuthorized"])
            assertEquals(false, v["automaticEventEligible"])
        }
    }

    @Test fun speedZeroAndPossibleParkCodeAreNeverConsideredVerifiedParked() {
        val result = MotionCandidatePolicy.interpret(0.0, 1)
        assertEquals(false, result["stationaryStateVerified"])
        assertEquals(false, result["vehicleWriteAuthorized"])
    }

    @Test fun catalogMotionSymbolsAreSeparatelyOptionalWithoutWeakeningSocGate() {
        fun frame(rows: String) =
            "BYD_FID_PROBE_BEGIN_V1\n$rows\nBYD_FID_PROBE_END_V1"
        val motionOnly = frame("""
            BYDAutoConstants.BYDAUTO_DEVICE_SPEED=1013
            BYDAutoConstants.BYDAUTO_DEVICE_GEARBOX=1011
            Speed.SPEED_AUTO_SPEED=-1807745016
            Gearbox.GEARBOX_AUTO_MODE_TYPE=555745336
        """.trimIndent())
        val parsed = FidReadOnlyDiscovery.parse(motionOnly)
        assertTrue(parsed.readyForMotionCandidateProbes)
        assertFalse(parsed.readyForCandidateProbes)
        assertEquals(1013, parsed.diagnostics()["speedDeviceCandidate"])
        assertEquals(1011, parsed.diagnostics()["gearboxDeviceCandidate"])
        assertEquals(-1807745016, parsed.diagnostics()["speedFidCandidate"])
        assertEquals(555745336, parsed.diagnostics()["gearFidCandidate"])

        assertFalse(FidReadOnlyDiscovery.parse(motionOnly.replace(
            "Speed.SPEED_AUTO_SPEED=-1807745016", ""
        )).readyForMotionCandidateProbes)
        assertEquals("probe_output_invalid", FidReadOnlyDiscovery.parse(frame(
            "Speed.SPEED_AUTO_SPEED=12\nSpeed.SPEED_AUTO_SPEED=13"
        )).status)
        assertEquals("probe_output_invalid", FidReadOnlyDiscovery.parse(frame(
            "Gearbox.GEARBOX_AUTO_MODE_TYPE=2147483648"
        )).status)
    }
}
