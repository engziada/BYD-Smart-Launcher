package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class ChargingCandidateStateTest {
    @Test fun chargingAcAndDcAreHypothesesWithNoAutomaticEvents() {
        for ((gun, expected) in listOf(2 to "ac_candidate", 3 to "dc_candidate", 4 to "ac_dc_candidate")) {
            val result = ChargingCandidateState.fromRaw(gun, 1, 3)
            assertEquals(expected, result["gunKindCandidate"])
            assertEquals("charging_candidate", result["chargeStateCandidate"])
            assertEquals(3, result["chargingTypeRaw"]) // kept uninterpreted
            assertEquals(false, result["firmwareVerified"])
            assertEquals(false, result["completionConfirmed"])
            assertEquals(false, result["automaticEventEligible"])
            assertEquals(false, result["vehicleWriteAuthorized"])
        }
    }

    @Test fun finishedOrPausedDoesNotConfirmChargeComplete() {
        val finished = ChargingCandidateState.fromRaw(3, 2, null)
        assertEquals("finished_candidate", finished["chargeStateCandidate"])
        assertEquals(false, finished["completionConfirmed"])
        assertEquals(false, finished["automaticEventEligible"])

        val paused = ChargingCandidateState.fromRaw(2, 13, 1)
        assertEquals("paused_candidate", paused["chargeStateCandidate"])
        assertEquals(false, paused["automaticEventEligible"])
    }

    @Test fun disconnectAndContradictorySignalsCannotBeMistakenForCharging() {
        assertEquals("unplugged_candidate",
            ChargingCandidateState.fromRaw(1, 2, null)["chargeStateCandidate"])
        assertEquals("conflicting_signals",
            ChargingCandidateState.fromRaw(1, 1, null)["chargeStateCandidate"])
        assertEquals("unknown",
            ChargingCandidateState.fromRaw(5, 1, null)["chargeStateCandidate"])
        for ((gun, bms) in listOf(null to 1, 3 to null, 0 to 1, 3 to 65535, 6 to 1)) {
            val result = ChargingCandidateState.fromRaw(gun, bms, null)
            assertEquals("unknown", result["chargeStateCandidate"])
            assertEquals(false, result["automaticEventEligible"])
        }
    }

    @Test fun chargingCatalogRequiresFirmwareSymbolsNotHardcodedAddresses() {
        val dump = """
            BYD_FID_PROBE_BEGIN_V1
            BYDAutoConstants.BYDAUTO_DEVICE_CHARGING=1009
            Charging.CHARGING_GUN_CONNECT_STATE=876611634
            Charging.CHARGING_BATTERRY_DEVICE_STATE=876611608
            Charging.CHARGING_TYPE=876611640
            BYD_FID_PROBE_END_V1
        """.trimIndent()
        val result = FidReadOnlyDiscovery.parse(dump)
        assertTrue(result.readyForChargingCandidateProbes)
        assertEquals("catalog_incomplete", result.status) // no SOC: charge probe is independent
        assertEquals(876611634, result.diagnostics()["gunFidCandidate"])
        assertFalse(result.readyForCandidateProbes)

        val noBms = dump.replace("Charging.CHARGING_BATTERRY_DEVICE_STATE=876611608\n", "")
        assertFalse(FidReadOnlyDiscovery.parse(noBms).readyForChargingCandidateProbes)
    }

    @Test fun duplicateAndMalformedChargingSymbolsAreRejected() {
        val duplicated = """
            BYD_FID_PROBE_BEGIN_V1
            BYDAutoConstants.BYDAUTO_DEVICE_CHARGING=1009
            Charging.CHARGING_GUN_CONNECT_STATE=876611634
            Charging.CHARGING_GUN_CONNECT_STATE=876611636
            Charging.CHARGING_BATTERRY_DEVICE_STATE=876611608
            BYD_FID_PROBE_END_V1
        """.trimIndent()
        assertEquals("probe_output_invalid", FidReadOnlyDiscovery.parse(duplicated).status)
        assertFalse(FidReadOnlyDiscovery.parse(duplicated).readyForChargingCandidateProbes)
    }
}
