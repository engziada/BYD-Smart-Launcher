package com.ziada.bydsmartlauncher

import java.security.MessageDigest

internal data class VehicleAddress(val device: Int, val fid: Int)
internal data class VerifiedComfortAddress(
    val write: VehicleAddress,
    val readback: VehicleAddress,
    val invertedWindowPercentage: Boolean = false,
    val pilotLevels: IntRange? = null,
) {
    fun matches(mapping: ComfortMapping): Boolean =
        write.device == mapping.deviceType && readback.device == mapping.deviceType &&
            write.fid != 0 && readback.fid != 0 && write != readback &&
            (!invertedWindowPercentage || mapping.isWindow)

    fun convert(value: Int): Int = if (invertedWindowPercentage) 100 - value else value

    fun writeRange(mapping: ComfortMapping): IntRange = pilotLevels ?: mapping.range
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

/** Exact build matches only. Fan levels 1–2 have passed on the car. */
internal object VerifiedComfortProfiles {
    private val profiles = mapOf(
        "e510a4f274e082bc1854aa0e2927fb561229535034a7a96d78d179e1afad0d33" to VerifiedComfortProfile(
            gear = VehicleAddress(1011, 555745336),
            parkCode = 1,
            speed = VehicleAddress(1013, -1807745016),
            celsiusUnit = VehicleAddress(1000, 1077936167),
            controls = mapOf(
                ComfortMapping.AC_POWER to VerifiedComfortAddress(
                    write = VehicleAddress(1000, 501219364),
                    readback = VehicleAddress(1000, 1077936144),
                ),
                ComfortMapping.AC_FAN to VerifiedComfortAddress(
                    write = VehicleAddress(1000, 501219340),
                    readback = VehicleAddress(1000, 1077936156),
                ),
                ComfortMapping.AC_TEMPERATURE to VerifiedComfortAddress(
                    write = VehicleAddress(1000, 501219368),
                    readback = VehicleAddress(1000, 1077936168),
                ),
                // DiLink reports percent open. This launcher also requests percent open.
                ComfortMapping.FRONT_LEFT to VerifiedComfortAddress(
                    write = VehicleAddress(1001, 1276219408),
                    readback = VehicleAddress(1001, 947912728),
                ),
                ComfortMapping.FRONT_RIGHT to VerifiedComfortAddress(
                    write = VehicleAddress(1001, 1276219424),
                    readback = VehicleAddress(1001, 1267728400),
                ),
                ComfortMapping.REAR_LEFT to VerifiedComfortAddress(
                    write = VehicleAddress(1001, 1276219416),
                    readback = VehicleAddress(1001, 947912736),
                ),
                ComfortMapping.REAR_RIGHT to VerifiedComfortAddress(
                    write = VehicleAddress(1001, 1276219432),
                    readback = VehicleAddress(1001, 947912752),
                ),
            ),
        ),
    )

    fun forFingerprint(raw: String?): VerifiedComfortProfile? =
        FirmwareDiagnostics.fullFingerprintDigest(raw)?.let(::forFingerprintDigest)

    internal fun forFingerprintDigest(digest: String): VerifiedComfortProfile? {
        // Compare the complete digest without embedding the private export's literal value.
        val key = MessageDigest.getInstance("SHA-256").digest(digest.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return profiles[key]
    }
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
        val writeRange = address.writeRange(mapping)
        if (value !in writeRange) return ComfortWriteResult("outside_pilot_range")
        if (!confirmedRecently()) return ComfortWriteResult("confirmation_expired")
        if (mapping == ComfortMapping.AC_TEMPERATURE &&
            profile.celsiusUnit?.let(io::readInt) != 1) return ComfortWriteResult("celsius_required")

        val before = io.readInt(address.readback)?.takeIf { it in mapping.range }
            ?: return ComfortWriteResult("readback_unavailable")
        if (address.convert(before) !in writeRange) {
            return ComfortWriteResult("outside_pilot_range", address.convert(before))
        }
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
        // Keep polling within the local ADB transport's five-second read deadline.
        repeat(if (mapping.isWindow) 20 else 4) {
            waitForReadback()
            val raw = io.readInt(address.readback)?.takeIf { it in mapping.range }
            if (raw != null) {
                last = address.convert(raw)
                val tolerance = if (mapping.isWindow && target in 1..99) 3 else 0
                if (kotlin.math.abs(raw - target) <= tolerance && raw != before) {
                    return ComfortWriteResult("confirmed", last)
                }
            }
        }
        return ComfortWriteResult("sent_unconfirmed", last)
    }
}
