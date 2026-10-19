package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class FidReadOnlyDiscoveryTest {
    private fun frame(lines: String) =
        "BYD_FID_PROBE_BEGIN_V1\n$lines\nBYD_FID_PROBE_END_V1"

    @Test fun commandOnlyUsesOurInstalledApkAndFixedReadOnlyEntrypoint() {
        val path = "/data/app/~~abC+de=/com.ziada.bydsmartlauncher-xyz==/base.apk"
        val command = FidReadOnlyDiscovery.command(path)
        assertEquals(
            "CLASSPATH='$path' app_process /system/bin " +
                "com.ziada.bydsmartlauncher.FidReflectionProbe", command
        )
        assertNull(FidReadOnlyDiscovery.command("/sdcard/Download/test.apk"))
        assertNull(FidReadOnlyDiscovery.command("/data/app/' ; id #/base.apk"))
        assertNull(FidReadOnlyDiscovery.command("/data/app/$(id)/base.apk"))
        assertNull(FidReadOnlyDiscovery.command("/data/app/broken package/base.apk"))
    }

    @Test fun discoverMapsFirmwareSymbolsButNeverGrantsVehicleWritePrivileges() {
        val result = FidReadOnlyDiscovery.parse(frame(
            """
            BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
            BYDAutoConstants.BYDAUTO_DEVICE_LIGHT=1004
            Statistic.STATISTIC_ELEC_PERCENTAGE=1246777400
            Statistic.STATISTIC_TOTAL_MILEAGE=987654321
            Statistic.STATISTIC_TOTAL_ELEC_CONSUMPTION=1032871984
            Light.LIGHT_TURN_SIGNAL_LIGHT=950009900
            """.trimIndent()
        ))
        assertTrue(result.readyForCandidateProbes)
        val info = result.diagnostics()
        assertEquals("catalog_candidate", info["status"])
        assertEquals(987654321, info["odometerFidCandidate"])
        assertEquals(4, info["symbolCount"]) // four symbols, two devices
        assertEquals(false, info["firmwareVerified"])
        assertEquals(false, info["vehicleWriteAuthorized"])
        assertEquals(false, info["helperPersistent"])
    }

    @Test fun rejectMalformedDuplicatedTruncatedAndOversizedOutput() {
        assertEquals("probe_unavailable", FidReadOnlyDiscovery.parse(null).status)
        assertEquals("probe_markers_invalid",
            FidReadOnlyDiscovery.parse("BYD_FID_PROBE_BEGIN_V1").status)
        assertEquals("firmware_classes_unavailable",
            FidReadOnlyDiscovery.parse(frame("BYD_FID_PROBE_UNAVAILABLE")).status)
        assertEquals("probe_output_invalid", FidReadOnlyDiscovery.parse(frame(
            "Statistic.STATISTIC_ELEC_PERCENTAGE=100\nStatistic.STATISTIC_ELEC_PERCENTAGE=200"
        )).status)
        assertEquals("probe_output_invalid", FidReadOnlyDiscovery.parse(frame(
            "CustomClass.QUERY=456"
        )).status)
        assertEquals("probe_output_invalid", FidReadOnlyDiscovery.parse(frame(
            "Statistic.STATISTIC_ELEC_PERCENTAGE=2147483648"
        )).status)
        assertEquals("probe_output_oversized",
            FidReadOnlyDiscovery.parse("X".repeat(8193)).status)
    }

    @Test fun partialCatalogIsNotEligibleForCandidateProbes() {
        val result = FidReadOnlyDiscovery.parse(frame(
            "Statistic.STATISTIC_ELEC_PERCENTAGE=1246777400"
        ))
        assertEquals("catalog_incomplete", result.status)
        assertFalse(result.readyForCandidateProbes)
        assertEquals(false, result.diagnostics()["ok"])
    }
}
