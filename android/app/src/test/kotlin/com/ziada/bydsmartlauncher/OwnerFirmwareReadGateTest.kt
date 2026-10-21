package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class OwnerFirmwareReadGateTest {
    private fun accepts(
        model: String? = "DiLink5.0 For BYD AUTO",
        manufacturer: String? = "BYD AUTO",
        sdk: Int? = 32,
        device: Int? = 1014,
        soc: Int? = 1246777400,
        odo: Int? = 1246765072
    ) = OwnerFirmwareReadGate.accepts(model, manufacturer, sdk, device, soc, odo)

    @Test fun acceptsOnlyOwnerObservedDiLink5ReadOnlyIdentity() {
        assertTrue(accepts())
        assertFalse(accepts(model = "DiLink4.0 For BYD AUTO"))
        assertFalse(accepts(model = null))
        assertFalse(accepts(manufacturer = "OTHER"))
        assertFalse(accepts(sdk = 29))
        assertFalse(accepts(sdk = null))
        assertFalse(accepts(device = 999))
        assertFalse(accepts(soc = null))
        assertFalse(accepts(odo = null))
    }

    @Test fun upstreamSongPlusFixtureHasDifferentAddressesAndIsRejected() {
        // BYDMate v4.0.1 app/src/test/resources/fid-catalog-songplus.txt,
        // NOT values returned by the owner's 2025 DiLink5.0 device.
        assertFalse(accepts(soc = 1033543720, odo = 1033543696))
        assertFalse(accepts(soc = 1033543720))
        assertFalse(accepts(odo = 1033543696))
    }
}
