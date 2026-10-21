package com.ziada.bydsmartlauncher

/**
 * Narrow read-only acceptance of ONE observed DiLink5 firmware/FID family.
 * This is an address/scale identity check, not certification of data freshness
 * or permission to actuate. Adding another model requires separate OEM evidence.
 */
internal object OwnerFirmwareReadGate {
    fun accepts(
        model: String?, manufacturer: String?, sdk: Int?,
        device: Int?, socFid: Int?, odometerFid: Int?
    ): Boolean =
        model == "DiLink5.0 For BYD AUTO" &&
        manufacturer == "BYD AUTO" &&
        sdk == 32 &&
        device == 1014 &&
        socFid == 1246777400 &&
        odometerFid == 1246765072
}
