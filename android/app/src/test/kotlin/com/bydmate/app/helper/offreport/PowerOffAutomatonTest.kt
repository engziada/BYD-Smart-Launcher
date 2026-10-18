package com.bydmate.app.helper.offreport

import org.junit.Assert.*
import org.junit.Test

class PowerOffAutomatonTest {
    @Test fun neverFiresWithoutObservedPowerOn() {
        val a = PowerOffAutomaton()
        assertFalse(a.onEvent(100, 0))
        assertFalse(a.onEvent(100, -10013))
        assertFalse(a.primed)
    }

    @Test fun firesOncePerOnOffCycle() {
        val a = PowerOffAutomaton()
        assertFalse(a.onEvent(100, 2))
        assertTrue(a.primed)
        assertTrue(a.onEvent(100, 0))
        assertFalse(a.onEvent(100, 0))
        assertFalse(a.primed)
        assertFalse(a.onEvent(100, 2))
        assertTrue(a.onEvent(100, 0))
    }

    @Test fun directReadingPrimesWithoutPrematureOff() {
        val a = PowerOffAutomaton()
        a.seed(100, 1)
        a.seed(100, 0)
        assertTrue(a.primed)
        assertTrue(a.onEvent(100, 0))
    }

    @Test fun badPowerReadDoesNotTriggerOff() {
        val a = PowerOffAutomaton()
        a.onEvent(100, 2)
        assertFalse(a.onEvent(100, -1))
        assertFalse(a.onEvent(100, -10013))
        assertTrue(a.primed)
    }

    @Test fun generationTracksEventsAndNotSeeds() {
        val a = PowerOffAutomaton()
        val initial = a.generation
        a.seed(100, 2)
        assertEquals(initial, a.generation)
        a.onEvent(100, 0)
        assertEquals(initial + 1, a.generation)
    }
}
