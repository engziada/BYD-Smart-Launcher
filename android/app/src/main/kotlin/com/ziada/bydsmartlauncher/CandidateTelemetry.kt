package com.ziada.bydsmartlauncher

import kotlin.math.roundToInt

/**
 * Fixed, read-only fallback addresses from BYDMate v4.0.1 FidRegistry.
 * License: PolyForm Noncommercial 1.0.0; see THIRD_PARTY_NOTICES.md.
 *
 * These addresses were validated on Leopard 3, NOT on Song Plus OS 2602.
 * They are diagnostic candidates, NEVER certified vehicle telemetry.
 */
internal object CandidateTelemetry {
    const val SOC = "service call autoservice 7 i32 1014 i32 1246777400"
    const val TOTAL_ENERGY = "service call autoservice 7 i32 1014 i32 1032871984"
    const val ODOMETER = "service call autoservice 5 i32 1014 i32 1246765072"

    private val parcel = Regex("""Parcel\(\s*00000000\s+([0-9a-fA-F]{8})""")

    private fun word(output: String?): Int? {
        if (output == null || output.length > 16384) return null
        val hex = parcel.find(output)?.groupValues?.getOrNull(1) ?: return null
        return hex.toLongOrNull(16)?.toInt()
    }

    fun float(output: String?): Double? {
        val bits = word(output) ?: return null
        val value = java.lang.Float.intBitsToFloat(bits)
        return value.toDouble().takeIf { it.isFinite() && value != -1f }
    }

    fun int(output: String?): Int? {
        val n = word(output) ?: return null
        return n.takeUnless { it == 65535 || it == 1048575 || it == -10013 || it == -10011 }
    }

    fun soc(output: String?): Double? = float(output)?.takeIf { it in 0.0..100.0 }

    fun energyKwh(output: String?): Double? =
        float(output)?.takeIf { it in 0.0..10_000_000.0 }

    /** This fallback counter is in 0.1 km. DO NOT apply the scale to a catalog FID. */
    fun odometerKm(output: String?): Double? =
        int(output)?.takeIf { it in 0..10_000_000 }?.div(10.0)
}

internal data class CandidateSample(
    val soc: Double?,
    val energyKwh: Double?,
    val odometerKm: Double?,
    val readAtElapsedMs: Long,
) {
    val valid: Boolean get() = soc != null && energyKwh != null && odometerKm != null
}

/**
 * Samples read on demand; this deliberately has no autonomous polling and no driving controls.
 * Only after enough positive progress can a tentative consumption/range figure be calculated.
 */
internal class ConsumptionWindow(
    private val maxTravelKm: Double = 25.0,
    private val minimumTravelKm: Double = 1.0,
) {
    private val observations = ArrayDeque<CandidateSample>()

    fun reset() = observations.clear()

    fun add(sample: CandidateSample): Double? {
        if (!sample.valid) return null
        val prev = observations.lastOrNull()
        if (prev != null) {
            val dist = sample.odometerKm!! - prev.odometerKm!!
            val energy = sample.energyKwh!! - prev.energyKwh!!
            // Reject counter reset / jumps / old or reordered observations; discard series.
            if (dist < 0.0 || energy < 0.0 || dist > 75.0 ||
                energy > 75.0 || sample.readAtElapsedMs <= prev.readAtElapsedMs) {
                reset()
            } else if (dist < 0.05) {
                return consumption() // Avoid buffer growth while parked.
            }
        }
        observations.addLast(sample)
        while (observations.size > 2 &&
            observations.last().odometerKm!! - observations.first().odometerKm!! > maxTravelKm) {
            observations.removeFirst()
        }
        return consumption()
    }

    fun consumption(): Double? {
        if (observations.size < 2) return null
        val first = observations.first()
        val last = observations.last()
        val dist = last.odometerKm!! - first.odometerKm!!
        val energy = last.energyKwh!! - first.energyKwh!!
        if (dist < minimumTravelKm || energy < 0.0) return null
        val avg = energy / dist * 100.0
        return avg.takeIf { it.isFinite() && it in 4.0..65.0 }
    }

    fun rangeKm(soc: Double?, usableCapacityKwh: Double?): Double? {
        val avg = consumption() ?: return null
        val c = usableCapacityKwh ?: return null
        val s = soc ?: return null
        if (!c.isFinite() || c !in 5.0..200.0 || !s.isFinite() || s !in 0.0..100.0) return null
        return (c * s / 100.0 * 100.0 / avg).takeIf { it.isFinite() && it in 0.0..2000.0 }
    }

    fun stats(): Map<String, Any?> {
        val first = observations.firstOrNull()
        val last = observations.lastOrNull()
        return mapOf(
            "windowDistanceKm" to if (first == null || last == null) null
                else last.odometerKm!! - first.odometerKm!!,
            "samples" to observations.size,
            "avgCandidateKwhPer100km" to consumption(),
        )
    }
}
