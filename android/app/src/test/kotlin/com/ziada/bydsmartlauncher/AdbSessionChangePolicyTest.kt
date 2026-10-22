package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class AdbSessionChangePolicyTest {
    @Test fun repeatedConnectOnLiveAdbMustNotStopReadOnlyStream() {
        assertFalse(AdbSessionChangePolicy.shouldStopStream("adbConnect", true))
        assertTrue(AdbSessionChangePolicy.shouldStopStream("adbConnect", false))
        assertTrue(AdbSessionChangePolicy.shouldStopStream("adbDisconnect", true))
        assertTrue(AdbSessionChangePolicy.shouldStopStream("adbDisconnect", false))
        assertFalse(AdbSessionChangePolicy.shouldStopStream("motionProbe", true))
        assertFalse(AdbSessionChangePolicy.shouldStopStream("chargingSample", true))
    }

    @Test fun firstExternalStopCauseSurvivesFlutterCleanup() {
        assertEquals("adb_session_changed",
            AdbSessionChangePolicy.effectiveStopReason(
                false, "stopped", "adb_session_changed", "operator_stopped"))
        assertEquals("catalog_setup_failed",
            AdbSessionChangePolicy.effectiveStopReason(
                false, "stopped", "catalog_setup_failed", "listener_detached"))
        assertEquals("operator_stopped",
            AdbSessionChangePolicy.effectiveStopReason(
                true, "candidate", null, "operator_stopped"))
        assertEquals("app_not_foreground",
            AdbSessionChangePolicy.effectiveStopReason(
                false, "idle", "explicit_start_required", "app_not_foreground"))
    }
}
