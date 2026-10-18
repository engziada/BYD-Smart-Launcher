package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class SafeAutoserviceProbeTest {
    @Test fun parsesNormalParcel() {
        // 53.5f = 0x42560000
        assertEquals(53.5, SafeAutoserviceProbe.decodeSoc("Result: Parcel(00000000 42560000   '....BV..')")!!, 0.0001)
    }

    @Test fun parsesAndroid14PaddedParcel() {
        assertEquals(50.0, SafeAutoserviceProbe.decodeSoc("Parcel(    00000000 42480000   ...)")!!, 0.0001)
    }

    @Test fun rejectsMissingMalformedAndSentinels() {
        assertNull(SafeAutoserviceProbe.decodeSoc(null))
        assertNull(SafeAutoserviceProbe.decodeSoc(""))
        assertNull(SafeAutoserviceProbe.decodeSoc("Parcel(00000001 42560000)"))
        assertNull(SafeAutoserviceProbe.decodeSoc("Parcel(00000000 BF800000)")) // -1.0f
        assertNull(SafeAutoserviceProbe.decodeSoc("Parcel(00000000 7FC00000)")) // NaN
        assertNull(SafeAutoserviceProbe.decodeSoc("Parcel(00000000 43480000)")) // 200f
    }
}
