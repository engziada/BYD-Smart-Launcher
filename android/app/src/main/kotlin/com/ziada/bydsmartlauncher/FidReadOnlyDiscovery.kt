package com.ziada.bydsmartlauncher

import com.bydmate.app.data.nativestack.FidCatalog

/**
 * Parses only the limited, read-only one-shot shell output owned by this APK.
 * Never treats a catalog address as verified: units, source semantics and
 * vehicle permissions must still be independently confirmed on the car.
 */
internal object FidReadOnlyDiscovery {
    private const val BEGIN = "BYD_FID_PROBE_BEGIN_V1"
    private const val END = "BYD_FID_PROBE_END_V1"
    private const val MAIN = "com.ziada.bydsmartlauncher.FidReflectionProbe"
    private const val MAX_OUTPUT = 8192
    private val allowedFields = setOf(
        "BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC",
        "BYDAutoConstants.BYDAUTO_DEVICE_LIGHT",
        "Statistic.STATISTIC_ELEC_PERCENTAGE",
        "Statistic.STATISTIC_TOTAL_MILEAGE",
        "Statistic.STATISTIC_TOTAL_ELEC_CONSUMPTION",
        "Light.LIGHT_TURN_SIGNAL_LIGHT"
    )
    private val oneLine = Regex("""^([A-Za-z][A-Za-z0-9_.]*)=(-?[0-9]{1,11})$""")

    /** Fixed entrypoint, fixed arguments, no user-supplied shell commands. */
    fun command(apkPath: String): String? {
        // Installed APK paths vary by Android release; allow the usual randomized /data/app names
        // but no whitespace, quote, semicolon, dollar sign, backtick, or shell metacharacters.
        if (!apkPath.startsWith("/data/app/") || !apkPath.endsWith("/base.apk") ||
            apkPath.length !in 24..350 ||
            apkPath.any { !(it.isLetterOrDigit() && it.code < 128) &&
                it !in "/._+-=~:%" }) return null
        return "CLASSPATH='$apkPath' app_process /system/bin $MAIN"
    }

    data class Result(val status: String, val catalog: FidCatalog = FidCatalog.EMPTY) {
        val readyForCandidateProbes: Boolean get() =
            status == "catalog_candidate" &&
            catalog.deviceOf("STATISTIC") != null &&
            catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE") != null &&
            catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE") != null

        fun diagnostics(): Map<String, Any?> = mapOf(
            "ok" to readyForCandidateProbes,
            "status" to status,
            "source" to "one_shot_read_only_firmware_reflection",
            "symbolCount" to catalog.totalSymbols,
            "deviceCount" to catalog.devices.size,
            "statisticDeviceCandidate" to catalog.deviceOf("STATISTIC"),
            "socFidCandidate" to catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE"),
            "odometerFidCandidate" to catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE"),
            "energyFidCandidate" to catalog.fidOf("Statistic.STATISTIC_TOTAL_ELEC_CONSUMPTION"),
            "lightDeviceCandidate" to catalog.deviceOf("LIGHT"),
            "turnSignalFidCandidate" to catalog.fidOf("Light.LIGHT_TURN_SIGNAL_LIGHT"),
            "firmwareVerified" to false,
            "helperPersistent" to false,
            "vehicleWriteAuthorized" to false
        )
    }

    fun parse(raw: String?): Result {
        if (raw == null) return Result("probe_unavailable")
        if (raw.length > MAX_OUTPUT) return Result("probe_output_oversized")
        val lines = raw.lineSequence().map { it.trim() }.toList()
        val begin = lines.indexOf(BEGIN)
        val end = lines.indexOf(END)
        if (begin < 0 || end <= begin || lines.lastIndexOf(BEGIN) != begin ||
            lines.lastIndexOf(END) != end) return Result("probe_markers_invalid")

        val data = lines.subList(begin + 1, end)
        if (data.any { it == "BYD_FID_PROBE_UNAVAILABLE" }) return Result("firmware_classes_unavailable")
        if (data.size > allowedFields.size) return Result("probe_output_unexpected")

        val seen = HashSet<String>()
        for (line in data) {
            val match = oneLine.matchEntire(line) ?: return Result("probe_output_invalid")
            val key = match.groupValues[1]
            if (key !in allowedFields || !seen.add(key) ||
                match.groupValues[2].toLongOrNull()?.let { it in Int.MIN_VALUE..Int.MAX_VALUE } != true) {
                return Result("probe_output_invalid")
            }
        }

        val catalog = FidCatalog.parse(data.joinToString("\n"))
        val status = if (catalog.deviceOf("STATISTIC") != null &&
            catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE") != null &&
            catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE") != null
        ) "catalog_candidate" else "catalog_incomplete"
        return Result(status, catalog)
    }
}
