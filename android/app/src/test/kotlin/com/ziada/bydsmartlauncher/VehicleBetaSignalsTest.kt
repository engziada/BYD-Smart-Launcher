package com.ziada.bydsmartlauncher

import com.ziada.bydsmartlauncher.automation.VerifiedChargeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleBetaSignalsTest {
    @Test fun cameraRequiresDriveSpeedAndOneSignal() {
        assertEquals(SidePreview.LEFT, sidePreview(4.0, 40.0, 2.0))
        assertEquals(SidePreview.RIGHT, sidePreview(4.0, 40.0, 4.0))
        assertNull(sidePreview(2.0, 40.0, 2.0))
        assertNull(sidePreview(4.0, 29.0, 2.0))
        assertNull(sidePreview(4.0, 40.0, 6.0))
        assertNull(sidePreview(null, 40.0, 2.0))
    }

    @Test fun chargeEventsRejectUnknownAndContradictoryInputs() {
        assertEquals(VerifiedChargeState.CHARGING, chargeState(3.0, 1.0))
        assertEquals(VerifiedChargeState.FINISHED, chargeState(3.0, 2.0))
        assertEquals(VerifiedChargeState.IDLE, chargeState(1.0, 15.0))
        assertNull(chargeState(1.0, 1.0))
        assertNull(chargeState(3.0, 15.0))
        assertNull(chargeState(3.0, null))
    }

    @Test fun newPowerStateRequiresTwoSamplesAndResetsAfterConnectionLoss() {
        val power = RepeatedSignal<Boolean>()
        assertNull(power.observe(true))
        assertEquals(true, power.observe(true))
        assertEquals(true, power.observe(false))
        assertEquals(true, power.observe(null))
        assertEquals(true, power.observe(false))
        assertEquals(false, power.observe(false))
        power.reset()
        assertNull(power.observe(false))
    }

    @Test fun dropsTruncatedAdbBatchesAndKeepsFieldsSeparate() {
        val sample = "Result: Parcel(00000000 00000002)\nBYD_BETA_READ_END\n" +
            "Result: Parcel(00000000 00000001)\nBYD_BETA_READ_END\n"
        val fields = listOf(BetaSignal.POWER, BetaSignal.GUN)
        assertEquals(mapOf(BetaSignal.POWER to 2.0, BetaSignal.GUN to 1.0), BetaSignal.parse(fields, sample))
        assertTrue(BetaSignal.parse(fields, sample.substringBeforeLast("BYD_BETA_READ_END")).isEmpty())
    }
}
