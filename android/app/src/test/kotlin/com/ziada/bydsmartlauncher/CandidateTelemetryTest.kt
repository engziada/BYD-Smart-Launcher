package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class CandidateTelemetryTest {
    private fun parcel(hex: String) = "Result: Parcel(   00000000 $hex  '....' )"
    @Test fun fallbackDecodersPreserveUnits() {
        assertEquals(53.5, CandidateTelemetry.soc(parcel("42560000"))!!, 1e-4)
        assertEquals(1200.0, CandidateTelemetry.energyKwh(parcel("44960000"))!!, 1e-4)
        assertEquals(150.0, CandidateTelemetry.odometerKm(parcel("000005dc"))!!, 1e-4)
    }
    @Test fun badFidsAndParcelNeverBecomeRangeInputs() {
        assertNull(CandidateTelemetry.soc(parcel("BF800000")))
        assertNull(CandidateTelemetry.soc(parcel("7FC00000")))
        assertNull(CandidateTelemetry.soc(parcel("0000ffff")))
        assertNull(CandidateTelemetry.energyKwh(parcel("000fffff")))
        assertNull(CandidateTelemetry.soc(parcel("43480000"))) // 200%
        assertNull(CandidateTelemetry.energyKwh(null))
        assertNull(CandidateTelemetry.odometerKm(parcel("0000ffff")))
        assertNull(CandidateTelemetry.odometerKm(parcel("000fffff")))
        assertNull(CandidateTelemetry.odometerKm("not a parcel"))
    }
    @Test fun computesTentativeConsumptionAndRangeOnlyAfterTravel() {
        val window = ConsumptionWindow()
        assertNull(window.add(CandidateSample(80.0, 100.0, 200.0, 1000L)))
        assertNull(window.add(CandidateSample(79.0, 100.1, 200.6, 1500L)))
        assertEquals(20.0, window.add(CandidateSample(78.0, 100.4, 202.0, 2000L))!!, 1e-5)
        assertEquals(234.0, window.rangeKm(78.0, 60.0)!!, 1e-5)
        assertNull(window.rangeKm(78.0, null))
    }
    @Test fun counterResetAndImpossibleJumpDiscardHistory() {
        val window = ConsumptionWindow()
        window.add(CandidateSample(80.0, 100.0, 200.0, 1000L))
        assertNull(window.add(CandidateSample(80.0, 98.0, 201.0, 2000L)))
        assertEquals(1, window.stats()["samples"])
        assertNull(window.add(CandidateSample(70.0, 150.0, 360.0, 3000L)))
        assertEquals(1, window.stats()["samples"])
    }
    @Test fun impossibleConsumptionIsNotPublished() {
        val w = ConsumptionWindow()
        w.add(CandidateSample(80.0, 50.0, 500.0, 1000L))
        assertNull(w.add(CandidateSample(80.0, 60.0, 501.0, 2000L))) // 1000kWh/100
        assertNull(w.rangeKm(80.0, 60.0))
    }
    @Test fun parkedSameOdometerDoesNotGrowWindow() {
        val w = ConsumptionWindow()
        w.add(CandidateSample(80.0, 100.0, 200.0, 1000L))
        w.add(CandidateSample(80.0, 100.0, 200.0, 2000L))
        assertEquals(1,w.stats()["samples"])
    }
}
