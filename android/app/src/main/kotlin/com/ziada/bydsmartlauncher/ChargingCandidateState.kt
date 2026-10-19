package com.ziada.bydsmartlauncher

/**
 * Interpretation hypotheses from BYDMate v4.0.1 Leopard 3 documentation.
 * Not validated on this owner's Song Plus DiLink 5.0.
 *
 * This classifier is intentionally observational: no transition events, automatic
 * notifications, charge completion claims, or vehicle operations are authorized.
 */
internal object ChargingCandidateState {
    private val knownGunKinds = mapOf(
        1 to "none_candidate",
        2 to "ac_candidate",
        3 to "dc_candidate",
        4 to "ac_dc_candidate",
        5 to "vtol_candidate"
    )
    private val knownBmsPhases = mapOf(
        1 to "charging_candidate",
        2 to "finished_candidate",
        13 to "paused_candidate"
    )

    fun fromRaw(gun: Int?, bms: Int?, type: Int?): Map<String, Any?> {
        val gunKind = gun?.let { knownGunKinds[it] } ?: "unknown"
        val bmsPhase = bms?.let { knownBmsPhases[it] } ?: "unknown"
        val readingsRecognized = gunKind != "unknown" && bmsPhase != "unknown"

        // A disconnected gun alongside "charging" is contradictory. A connected
        // gun with finished BMS may mean done OR something else; never send an alert.
        val hypothesis = when {
            !readingsRecognized -> "unknown"
            gun == 1 && bms == 1 -> "conflicting_signals"
            gun == 1 -> "unplugged_candidate"
            gun != null && gun in 2..4 && bms == 1 -> "charging_candidate"
            gun in 2..4 && bms == 2 -> "finished_candidate"
            gun in 2..4 && bms == 13 -> "paused_candidate"
            else -> "unknown"
        }

        return mapOf(
            "ok" to readingsRecognized,
            "gunStateRaw" to gun,
            "gunKindCandidate" to gunKind,
            "bmsStateRaw" to bms,
            "bmsPhaseCandidate" to bmsPhase,
            "chargingTypeRaw" to type,
            "chargeStateCandidate" to hypothesis,
            "source" to "one_shot_read_only_firmware_catalog",
            "firmwareVerified" to false,
            "completionConfirmed" to false,
            "automaticEventEligible" to false,
            "vehicleWriteAuthorized" to false
        )
    }
}
