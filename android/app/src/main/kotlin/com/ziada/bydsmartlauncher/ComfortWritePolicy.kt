package com.ziada.bydsmartlauncher

internal data class VehicleAddress(val device: Int, val fid: Int)
internal data class VerifiedComfortAddress(
    val write: VehicleAddress,
    val readback: VehicleAddress,
    val invertedWindowPercentage: Boolean = false,
) {
    fun matches(mapping: ComfortMapping): Boolean =
        write.device == mapping.deviceType && readback.device == mapping.deviceType &&
            write.fid != 0 && readback.fid != 0 && write != readback &&
            (!invertedWindowPercentage || mapping.isWindow)

    fun convert(value: Int): Int = if (invertedWindowPercentage) 100 - value else value
}

internal data class VerifiedComfortProfile(
    val gear: VehicleAddress,
    val parkCode: Int,
    val speed: VehicleAddress,
    val celsiusUnit: VehicleAddress?,
    val controls: Map<ComfortMapping, VerifiedComfortAddress>,
) {
    fun address(mapping: ComfortMapping): VerifiedComfortAddress? =
        controls[mapping]?.takeIf { it.matches(mapping) }
}

/** Add exact fingerprint hashes only after recorded, independent on-car commissioning. */
internal object VerifiedComfortProfiles {
    private val profiles: Map<String, VerifiedComfortProfile> = emptyMap()

    fun forFingerprint(raw: String?): VerifiedComfortProfile? =
        FirmwareDiagnostics.fullFingerprintDigest(raw)?.let(profiles::get)
}

internal interface ComfortVehicleIo {
    fun readInt(address: VehicleAddress): Int?
    fun readSpeed(address: VehicleAddress): Float?
    /** Negative statuses are rejections; null means the outcome could not be determined. */
    fun writeInt(address: VehicleAddress, value: Int): Int?
}

internal data class ComfortWriteResult(val status: String, val readback: Int? = null) {
    fun asMap(): Map<String, Any?> = mapOf(
        "ok" to (status == "confirmed" || status == "already_at_target"),
        "status" to status,
        "readback" to readback,
    )
}

internal object ComfortWritePolicy {
    private const val CONFIRMATION_MS = 3_000L
    private const val PARK_SAMPLE_MS = 500L

    fun execute(
        profile: VerifiedComfortProfile,
        mapping: ComfortMapping,
        value: Int,
        confirmedAtMs: Long,
        nowMs: () -> Long,
        waitForReadback: () -> Unit,
        io: ComfortVehicleIo,
    ): ComfortWriteResult {
        fun confirmedRecently() = nowMs() - confirmedAtMs in 0..CONFIRMATION_MS
        if (value !in mapping.range) return ComfortWriteResult("invalid_value")
        val address = profile.address(mapping) ?: return ComfortWriteResult("mapping_not_verified")
        if (!confirmedRecently()) return ComfortWriteResult("confirmation_expired")
        if (mapping == ComfortMapping.AC_TEMPERATURE &&
            profile.celsiusUnit?.let(io::readInt) != 1) return ComfortWriteResult("celsius_required")

        val before = io.readInt(address.readback)?.takeIf { it in mapping.range }
            ?: return ComfortWriteResult("readback_unavailable")
        val target = address.convert(value)
        if (before == target) return ComfortWriteResult("already_at_target", value)

        val started = nowMs()
        val initialGear = io.readInt(profile.gear)
        val speed = io.readSpeed(profile.speed)
        val finalGear = io.readInt(profile.gear)
        if (profile.gear.device != 1011 || profile.speed.device != 1013 ||
            initialGear != profile.parkCode || finalGear != profile.parkCode ||
            speed != 0f || nowMs() - started !in 0..PARK_SAMPLE_MS) {
            return ComfortWriteResult("park_not_verified", address.convert(before))
        }
        if (!confirmedRecently()) return ComfortWriteResult("confirmation_expired")

        val result = io.writeInt(address.write, target)
        if (result == null) return ComfortWriteResult("write_outcome_unknown")
        if (result < 0) return ComfortWriteResult("write_rejected")
        var last: Int? = null
        repeat(if (mapping.isWindow) 8 else 4) {
            waitForReadback()
            val raw = io.readInt(address.readback)?.takeIf { it in mapping.range }
            if (raw != null) {
                last = address.convert(raw)
                val tolerance = if (mapping.isWindow) 3 else 0
                if (kotlin.math.abs(raw - target) <= tolerance && raw != before) {
                    return ComfortWriteResult("confirmed", last)
                }
            }
        }
        return ComfortWriteResult("sent_unconfirmed", last)
    }
}
