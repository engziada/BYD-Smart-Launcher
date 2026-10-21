package com.ziada.bydsmartlauncher

/**
 * Pure, fail-closed stream acceptance policy. A valid ADB packet is not a
 * verified vehicle observation, and no value here can authorize a vehicle
 * write, automation action or charge-complete event.
 *
 * An invalid sample clears the displayed readings immediately. Three
 * consecutive invalid/stale samples stop the native stream.
 */
internal class CandidateStreamPolicy(
    private val maxConsecutiveFailures: Int = 3,
    private val maxAgeMs: Long = 15_000L
) {
    private var previousSampleEpochMs = -1L
    private var failures = 0
    private var sequence = 0L

    fun reset() {
        previousSampleEpochMs = -1L
        failures = 0
        sequence = 0L
    }

    fun inspect(raw: Map<String, Any?>, nowEpochMs: Long): Map<String, Any?> {
        val sampleTime = (raw["capturedAtEpochMs"] as? Number)?.toLong()
        val soc = (raw["socCandidatePercent"] as? Number)?.toDouble()
        val mileage = (raw["odometerCandidateKm"] as? Number)?.toDouble()
        val valid = raw["ok"] == true &&
            raw["catalogMatched"] == true &&
            sampleTime != null && sampleTime > previousSampleEpochMs &&
            nowEpochMs >= sampleTime && nowEpochMs - sampleTime <= maxAgeMs &&
            soc != null && soc.isFinite() && soc in 0.0..100.0 &&
            mileage != null && mileage.isFinite() && mileage in 0.0..1_000_000.0 &&
            raw["firmwareVerified"] == false &&
            raw["vehicleWriteAuthorized"] == false

        if (!valid) {
            failures++
            val stopped = failures >= maxConsecutiveFailures
            return mapOf(
                "streamState" to if (stopped) "stopped" else "stale",
                "reason" to ((raw["reason"] as? String)?.takeIf { it.length <= 60 }
                    ?: "invalid_or_stale_candidate"),
                "failureStreak" to failures,
                "sequence" to sequence,
                "firmwareVerified" to false,
                "vehicleWriteAuthorized" to false,
                "automaticEventEligible" to false
            )
        }

        previousSampleEpochMs = sampleTime!!
        failures = 0
        sequence++
        return mapOf(
            "streamState" to "candidate",
            "ok" to true,
            "socCandidatePercent" to soc,
            "odometerCandidateKm" to mileage,
            "capturedAtEpochMs" to sampleTime,
            "sequence" to sequence,
            "failureStreak" to 0,
            "source" to "firmware_reflected_owner_fid_candidates",
            "odometerScaleCandidate" to "0.1km_per_tick_same_owner_observed_fid",
            "catalogMatched" to true,
            "firmwareVerified" to false,
            "vehicleWriteAuthorized" to false,
            "automaticEventEligible" to false
        )
    }
}
