package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class FirmwareDiagnosticsTest {
    @Test fun supportsSafeModelNamesAndRejectsMultilineOutput() {
        assertEquals("BYD Song Plus", FirmwareDiagnostics.property(" BYD Song Plus\n"))
        assertNull(FirmwareDiagnostics.property(""))
        assertEquals("foo", FirmwareDiagnostics.property("foo\nbar"))
        assertNull(FirmwareDiagnostics.property("bad\u0000data"))
        assertNull(FirmwareDiagnostics.property("a".repeat(81)))
    }

    @Test fun serviceCheckIsConservative() {
        assertEquals(true, FirmwareDiagnostics.servicePresent("Service autoservice: found"))
        assertEquals(false, FirmwareDiagnostics.servicePresent("Service autoservice: not found"))
        assertNull(FirmwareDiagnostics.servicePresent("Service autoservice: permission denied"))
        assertNull(FirmwareDiagnostics.servicePresent("broken output"))
    }

    @Test fun sdkHasBoundsAndFingerprintNeverReturnsRaw() {
        assertEquals(34, FirmwareDiagnostics.sdkInt("34"))
        assertNull(FirmwareDiagnostics.sdkInt("-1"))
        assertNull(FirmwareDiagnostics.sdkInt("99999999999"))
        val fingerprint = "BYD/example:14/ABCD/user:release-keys"
        val hash = FirmwareDiagnostics.fingerprintDigest(fingerprint)
        assertNotNull(hash)
        assertEquals(20, hash!!.length)
        assertFalse(hash.contains(fingerprint))
    }
}
