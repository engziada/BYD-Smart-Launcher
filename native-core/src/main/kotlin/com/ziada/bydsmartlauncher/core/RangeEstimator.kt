package com.ziada.bydsmartlauncher.core

/** Prototype independent estimator. Live consumption must come from validated BMS data. */
data class RangeInput(
    val socPercent: Int?,
    val usableCapacityKwh: Double?,
    val avgKwhPer100Km: Double?,
    val carriedKwh: Double = 0.0
)

data class RangeEstimate(
    val remainingKwh: Double,
    val rangeKm: Double,
    val avgKwhPer100Km: Double
)

/** Missing or invalid values yield unavailable, never a fabricated number. */
object RangeEstimator {
    fun estimate(input: RangeInput): RangeEstimate? {
        val soc = input.socPercent ?: return null
        val capacity = input.usableCapacityKwh ?: return null
        val avg = input.avgKwhPer100Km ?: return null
        if (soc !in 1..100 || !capacity.isFinite() || capacity !in 1.0..1000.0) return null
        if (!avg.isFinite() || avg <= 0.0 || avg > 150.0) return null
        val carry = input.carriedKwh
        if (!carry.isFinite() || carry < 0.0 || carry > capacity * 0.03) return null
        val remaining = capacity * soc / 100.0 - carry
        if (remaining <= 0.0 || !remaining.isFinite()) return null
        val km = remaining / avg * 100.0
        if (!km.isFinite()) return null
        return RangeEstimate(remaining, km, avg)
    }
}
