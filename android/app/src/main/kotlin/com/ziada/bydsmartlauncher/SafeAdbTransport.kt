package com.ziada.bydsmartlauncher

import android.content.Context
import android.os.SystemClock
import com.bydmate.app.data.autoservice.AdbConnectFailure
import com.bydmate.app.data.autoservice.AdbKeyStore
import com.bydmate.app.data.autoservice.AdbProtocolClient

/**
 * User-initiated, LOOPBACK ONLY ADB transport. No general-purpose exec or write channel is
 * exposed to Flutter. This does NOT prove access to BYD autoservice / vehicle telemetry.
 * BYDMate-derived ADB classes are in their own third-party namespace.
 */
internal class SafeAdbTransport(context: Context) {
    private val keyStore = AdbKeyStore(context.applicationContext)
    private val preferences = context.applicationContext.getSharedPreferences("range_preferences", Context.MODE_PRIVATE)
    private val consumption = ConsumptionWindow()

    private var client: AdbProtocolClient? = null

    @Volatile private var state: String = "not_connected"
    @Volatile private var failureReason: String? = null
    @Volatile private var androidVersion: String? = null

    /** Must run on a dedicated worker: ADB may show an approval dialog for up to 60 seconds. */
    @Synchronized
    fun connect(): Map<String, Any?> {
        if (state == "connected" && client?.isConnected() == true) return diagnostics()
        state = "connecting"
        failureReason = null
        androidVersion = null
        return try {
            val pair = keyStore.loadOrGenerate()
            val transport = AdbProtocolClient(
                keyPair = pair,
                host = "127.0.0.1",
                port = 5555,
                certificateProvider = { keyStore.loadOrGenerateCertificate() },
            )
            client?.disconnect()
            client = transport
            if (transport.connect()) {
                state = "connected"
            } else {
                state = "not_connected"
                failureReason = when (transport.lastConnectFailure) {
                    AdbConnectFailure.AUTH_REJECTED -> "authorization_rejected_or_timeout"
                    else -> "adb_port_unreachable"
                }
            }
            diagnostics()
        } catch (e: Exception) {
            state = "not_connected"
            failureReason = "transport_error"
            client?.disconnect()
            client = null
            diagnostics()
        }
    }

    /**
     * Optional operator-provided usable capacity, used ONLY for tentative range estimation.
     * This value is not guessed from a model name. Can be cleared using null.
     */
    @Synchronized
    fun configureCapacity(kwh: Double?): Map<String, Any?> {
        if (kwh == null) {
            preferences.edit().remove("usable_capacity_kwh").apply()
            return capacityStatus()
        }
        if (!kwh.isFinite() || kwh !in 5.0..200.0) {
            return mapOf("ok" to false, "error" to "invalid_capacity")
        }
        preferences.edit().putFloat("usable_capacity_kwh", kwh.toFloat()).apply()
        return capacityStatus()
    }

    fun capacityStatus(): Map<String, Any?> {
        val capacity = if (preferences.contains("usable_capacity_kwh")) {
            preferences.getFloat("usable_capacity_kwh", 0f).toDouble()
        } else null
        return mapOf(
            "ok" to true,
            "usableCapacityKwh" to capacity,
            "source" to "operator_provided",
            "firmwareVerified" to false,
        )
    }

    /**
     * A user-invoked, finite read-only diagnostic batch. Fallback addresses are NOT validated
     * for this vehicle. No vehicle snapshot is promoted to "live" based on these readings.
     */
    @Synchronized
    fun sampleTelemetry(): Map<String, Any?> {
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf("ok" to false, "reason" to "adb_not_connected")
        }
        val connection = client ?: return mapOf("ok" to false, "reason" to "adb_not_connected")
        val sample = CandidateSample(
            soc = CandidateTelemetry.soc(connection.exec(CandidateTelemetry.SOC)),
            energyKwh = CandidateTelemetry.energyKwh(connection.exec(CandidateTelemetry.TOTAL_ENERGY)),
            odometerKm = CandidateTelemetry.odometerKm(connection.exec(CandidateTelemetry.ODOMETER)),
            readAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        // One missed field invalidates the whole sample. Rejecting it never advances the window.
        val avg = consumption.add(sample)
        val capacity = (capacityStatus()["usableCapacityKwh"] as? Number)?.toDouble()
        return mapOf(
            "ok" to sample.valid,
            "socCandidatePercent" to sample.soc,
            "energyCandidateKwh" to sample.energyKwh,
            "odometerCandidateKm" to sample.odometerKm,
            "consumptionCandidateKwhPer100km" to avg,
            "rangeCandidateKm" to if (sample.valid) consumption.rangeKm(sample.soc, capacity) else null,
            "usableCapacityKwh" to capacity,
            "sampleCount" to consumption.stats()["samples"],
            "windowDistanceKm" to consumption.stats()["windowDistanceKm"],
            "firmwareVerified" to false,
            "source" to "BYDMate_v4.0.1_unverified_fallbacks",
            "odometerScaleAssumption" to "0.1_km_per_tick_LEOPARD3_NOT_VERIFIED_SONG",
            "reason" to if (sample.valid) "requires_firmware_validation" else "unsupported_or_incomplete_read",
        )
    }

    /** A single fixed read-only autoservice probe; returned SOC is UNVERIFIED for Song Plus. */
    @Synchronized
    fun probeSoc(): Map<String, Any?> {
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf("ok" to false, "reason" to "adb_not_connected")
        }
        val raw = client?.exec(SafeAutoserviceProbe.SOC_READ_COMMAND)
        val soc = SafeAutoserviceProbe.decodeSoc(raw)
        return mapOf(
            "ok" to (soc != null),
            "socCandidatePercent" to soc,
            "firmwareVerified" to false,
            "source" to "BYDMate_v4.0.1_fallback_FID",
            "reason" to if (soc == null) "unsupported_or_no_response" else "requires_firmware_validation",
        )
    }

    /** Limited, READ-ONLY environment probe. Never pass arbitrary UI-provided commands here. */
    @Synchronized
    fun probe(): Map<String, Any?> {
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf("ok" to false, "reason" to "adb_not_connected")
        }
        val release = client?.exec("getprop ro.build.version.release")
        return if (release != null && release.matches(Regex("[0-9A-Za-z._-]{1,30}"))) {
            androidVersion = release
            mapOf("ok" to true, "androidRelease" to release)
        } else {
            mapOf("ok" to false, "reason" to "probe_unavailable")
        }
    }

    @Synchronized
    fun disconnect(): Map<String, Any?> {
        client?.disconnect()
        client = null
        state = "not_connected"
        failureReason = null
        androidVersion = null
        consumption.reset()
        return diagnostics()
    }

    /** Only volatile diagnostics; deliberately avoids blocking on ADB locks. */
    fun diagnostics(): Map<String, Any?> = mapOf(
        "adb" to state,
        "adbFailure" to failureReason,
        "androidRelease" to androidVersion,
        "transportHost" to "127.0.0.1",
        "transportPort" to 5555,
        "helper" to "not_installed",
        "vehicleTelemetry" to "not_implemented",
        "writeEnabled" to false,
    )

    val transportState: String get() = state
}
