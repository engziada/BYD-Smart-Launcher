package com.ziada.bydsmartlauncher

/**
 * Observational guardrail for one-shot speed and gearbox reads.
 *
 * A valid Binder GET only proves that a parcel was returned. The numeric speed
 * scaling (including whether the units are km/h) and the gearbox enum meanings
 * are unknown for this owner's DiLink 5.0. Neither field can be used for
 * stationary checks, controls, blind spot, or automated actions.
 */
internal object MotionCandidatePolicy {
    fun interpret(speedRawFloat: Double?, gearRawInt: Int?): Map<String, Any?> {
        val speed = speedRawFloat?.takeIf { it.isFinite() && it in 0.0..300.0 }
        val gear = gearRawInt?.takeIf { it in 0..30 }
        return mapOf(
            "ok" to (speed != null && gear != null),
            "status" to if (speed != null && gear != null) "raw_candidates"
                else "incomplete_or_invalid_raw_values",
            "speedRawFloatCandidate" to speed,
            "gearRawIntCandidate" to gear,
            "speedUnitsVerified" to false,
            "gearEnumVerified" to false,
            "stationaryStateVerified" to false,
            "firmwareVerified" to false,
            "vehicleWriteAuthorized" to false,
            "automaticEventEligible" to false,
            "source" to "firmware_reflection_one_shot_get_only"
        )
    }
}
