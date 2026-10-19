package com.ziada.bydsmartlauncher

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.time.Instant

/**
 * Explicit, privacy-minimized export to Downloads on Android 10+.
 * Never export raw ADB keys, Telegram settings, VIN, network configuration,
 * arbitrary shell responses or route histories.
 */
internal class DiagnosticReportWriter(private val context: Context) {
    companion object {
        private val TRANSPORT_KEYS = setOf(
            "adb", "adbFailure", "androidRelease", "helper", "vehicleTelemetry", "writeEnabled"
        )
        private val FIRMWARE_KEYS = setOf(
            "ok", "model", "manufacturer", "androidSdk", "fingerprintHashPrefix",
            "autoservicePresent", "compatibleAndVerified", "vehicleWriteAuthorized"
        )
        private val FID_KEYS = setOf(
            "ok", "status", "source", "symbolCount", "deviceCount",
            "statisticDeviceCandidate", "socFidCandidate", "odometerFidCandidate",
            "chargingDeviceCandidate", "gunFidCandidate", "bmsFidCandidate", "chargeTypeFidCandidate",
            "energyFidCandidate", "lightDeviceCandidate", "turnSignalFidCandidate",
            "firmwareVerified", "helperPersistent", "vehicleWriteAuthorized",
            "socCatalogCandidatePercent", "odometerCatalogRawCandidate",
            "catalogReadsOk", "odometerCatalogScaleVerified"
        )
        private val CHARGING_KEYS = setOf(
            "ok", "status", "capturedAtEpochMs", "catalogResolved", "gunStateRaw", "gunKindCandidate",
            "bmsStateRaw", "bmsPhaseCandidate", "chargingTypeRaw",
            "chargeStateCandidate", "source", "firmwareVerified",
            "completionConfirmed", "automaticEventEligible", "vehicleWriteAuthorized"
        )
        private val TELEMETRY_KEYS = setOf(
            "ok", "socCandidatePercent", "energyCandidateKwh", "odometerCandidateKm",
            "sampleCount", "windowDistanceKm", "firmwareVerified",
            "odometerScaleAssumption", "source", "reason"
        )

        fun safeFields(source: Map<String, Any?>, allowed: Set<String>): JSONObject {
            val json = JSONObject()
            for (key in allowed.sorted()) {
                val value = source[key] ?: continue
                when (value) {
                    is Boolean -> json.put(key, value)
                    is Int -> json.put(key, value)
                    is Long -> json.put(key, value)
                    is Number -> if (value.toDouble().isFinite()) json.put(key, value.toDouble())
                    is String -> if (value.length <= 120 && value.none { it.isISOControl() }) {
                        json.put(key, value)
                    }
                }
            }
            return json
        }
    }

    fun save(
        transport: Map<String, Any?>,
        firmware: Map<String, Any?>,
        telemetry: Map<String, Any?>,
        fidDiscovery: Map<String, Any?> = emptyMap(),
        charging: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return mapOf("ok" to false, "error" to "android_10_required")
        }
        val document = JSONObject()
            .put("schema", 3)
            .put("exportedAtUtc", Instant.now().toString())
            .put("verification", "READ_ONLY_UNVERIFIED_NO_VEHICLE_WRITES")
            .put("transport", safeFields(transport, TRANSPORT_KEYS))
            .put("firmware", safeFields(firmware, FIRMWARE_KEYS))
            .put("telemetryCandidate", safeFields(telemetry, TELEMETRY_KEYS))
            .put("fidCatalogCandidate", safeFields(fidDiscovery, FID_KEYS))
            .put("chargingCandidate", safeFields(charging, CHARGING_KEYS))

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "byd-diagnostics-${System.currentTimeMillis()}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BYD-Smart-Launcher")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return mapOf("ok" to false, "error" to "storage_unavailable")
        try {
            val stream = resolver.openOutputStream(uri)
                ?: error("unable to open export file")
            stream.use { it.write(document.toString(2).toByteArray(Charsets.UTF_8)) }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return mapOf("ok" to true, "location" to "Downloads/BYD-Smart-Launcher")
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            return mapOf("ok" to false, "error" to "export_failed")
        }
    }
}
