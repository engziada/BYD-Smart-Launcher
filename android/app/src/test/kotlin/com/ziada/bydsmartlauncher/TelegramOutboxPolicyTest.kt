package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class TelegramOutboxPolicyTest {
    @Test fun dedupesRepeatedManualTestRequests() {
        val first = TelegramRetryPolicy.dedupe(emptyList(), 1000L)
        assertEquals(1, first.size)
        assertEquals("TEST", first[0].kind)
        assertEquals(1, TelegramRetryPolicy.dedupe(first, 2000L).size)
        assertEquals(1000L, TelegramRetryPolicy.dedupe(first, 2000L)[0].createdAtMs)
    }

    @Test fun exponentialBackoffHasAnUpperBound() {
        assertEquals(15_000L, TelegramRetryPolicy.delayMs(1))
        assertEquals(30_000L, TelegramRetryPolicy.delayMs(2))
        assertEquals(60_000L, TelegramRetryPolicy.delayMs(3))
        assertEquals(3_600_000L, TelegramRetryPolicy.delayMs(100))
    }

    @Test fun rejectsInjectedKindsOrInvalidQueueData() {
        val valid = PendingTelegram("diagnostic-test", "TEST", 0, 10L, 0L)
        assertTrue(TelegramRetryPolicy.valid(valid))
        assertFalse(TelegramRetryPolicy.valid(valid.copy(kind = "POWER_OFF")))
        assertFalse(TelegramRetryPolicy.valid(valid.copy(id = "../invalid")))
        assertFalse(TelegramRetryPolicy.valid(valid.copy(attempts = -1)))
        assertFalse(TelegramRetryPolicy.valid(valid.copy(nextAllowedMs = -1L)))
    }
}
