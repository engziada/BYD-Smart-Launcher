package com.ziada.bydsmartlauncher

/**
 * Conservative READ-ONLY compatibility probe; BYDMate 4.0.1 defaults validated on Leopard 3,
 * NOT verified for Song Plus OS 2602. A successful value is only a candidate diagnostic.
 * Adapted protocol identifiers from BYDMate v4.0.1 (PolyForm Noncommercial 1.0.0).
 */
internal object SafeAutoserviceProbe {
    const val SOC_READ_COMMAND = "service call autoservice 7 i32 1014 i32 1246777400"

    private val parcel = Regex("""Parcel\(\s*00000000\s+([0-9a-fA-F]{8})""")

    fun decodeSoc(raw: String?): Double? {
        if (raw == null) return null
        val hex = parcel.find(raw)?.groupValues?.getOrNull(1) ?: return null
        val bits = hex.toLongOrNull(16)?.toInt() ?: return null
        val reading = java.lang.Float.intBitsToFloat(bits)
        // Exclude -1 sentinel and all NaNs/infinities/wrong values.
        return reading.takeIf { it.isFinite() && it in 0.0f..100.0f }?.toDouble()
    }
}
