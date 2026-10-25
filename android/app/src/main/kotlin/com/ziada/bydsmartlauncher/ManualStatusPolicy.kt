package com.ziada.bydsmartlauncher

import java.util.Locale

/**
 * A PRIVATE, EXPLICITLY REQUESTED snapshot message.
 *
 * Only a fresh sample from the native owner-FID-gated candidate stream is
 * permitted. The sender never advertises unverified data as an OEM-certified
 * measurement. No background sending, vehicle commands or arbitrary text.
 */
internal object ManualStatusPolicy {
    private const val MAX_AGE_MS = 15_000L

    fun message(sample: Map<String, Any?>, nowEpochMs: Long): String? {
        val soc = (sample["socCandidatePercent"] as? Number)?.toDouble()
            ?: return null
        val odometer = (sample["odometerCandidateKm"] as? Number)?.toDouble()
            ?: return null
        val captured = (sample["capturedAtEpochMs"] as? Number)?.toLong()
            ?: return null
        if (sample["ok"] != true || sample["catalogMatched"] != true ||
            sample["sessionValid"] != true ||
            sample["streamState"] != "candidate" ||
            sample["firmwareVerified"] != false ||
            sample["vehicleWriteAuthorized"] != false ||
            captured <= 0L || captured > nowEpochMs ||
            nowEpochMs - captured > MAX_AGE_MS ||
            !soc.isFinite() || soc !in 0.0..100.0 ||
            !odometer.isFinite() || odometer !in 0.0..1_000_000.0
        ) return null

        return String.format(Locale.US,
            "BYD Smart Launcher • manually requested car status\n" +
                "Battery SOC (candidate): %.0f%%\n" +
                "Odometer (candidate): %,.1f km\n" +
                "Read-only DiLink stream • fresh on request.\n" +
                "These readings are not independently OEM-certified. " +
                "No vehicle control or charging state was inferred.",
            soc, odometer
        )
    }
}
