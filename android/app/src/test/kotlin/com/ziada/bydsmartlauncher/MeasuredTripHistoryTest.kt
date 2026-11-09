package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class MeasuredTripHistoryTest {
    @Test fun consumptionUsesTotalEnergyOverTotalDistance() {
        val status = MeasuredTripHistory.summarize(listOf(MeasuredTrip(10.0, 3.0), MeasuredTrip(90.0, 9.0)))
        assertEquals(12.0, status["consumption"] as Double, 0.0001)
        assertEquals(2, status["tripCount"])
        assertNull(MeasuredTripHistory.summarize(emptyList())["consumption"])
    }

    @Test fun rejectsInvalidMeasurements() {
        assertFalse(MeasuredTrip(0.0, 1.0).valid)
        assertFalse(MeasuredTrip(10.0, Double.NaN).valid)
        assertFalse(MeasuredTrip(10.0, -2.0).valid)
        assertTrue(MeasuredTrip(10.0, 2.0).valid)
    }
}
