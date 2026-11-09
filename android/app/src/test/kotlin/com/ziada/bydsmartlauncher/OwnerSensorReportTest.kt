package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class OwnerSensorReportTest {
    private fun frame(payload: String) = "${OwnerSensorReport.BEGIN}\n$payload\n${OwnerSensorReport.END}"

    @Test fun parsesOnlyBoundedAllowlistedSensorFields() {
        assertEquals(mapOf("avmSurfaceApi" to 1, "frontLeftKpa" to 241),
            OwnerSensorReport.parse(frame("avmSurfaceApi=1\nfrontLeftKpa=241")))
        assertNull(OwnerSensorReport.parse(frame("avmSurfaceApi=1\nfrontLeftKpa=65535")))
        assertNull(OwnerSensorReport.parse(frame("avmSurfaceApi=1\nfrontLeftKpa=241\nfrontLeftKpa=242")))
        assertNull(OwnerSensorReport.parse(frame("avmSurfaceApi=1\nother=1")))
        assertNull(OwnerSensorReport.parse(frame("frontLeftKpa=241")))
    }
}
