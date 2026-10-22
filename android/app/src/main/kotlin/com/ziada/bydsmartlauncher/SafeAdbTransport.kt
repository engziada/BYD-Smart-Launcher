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
    private val appContext = context.applicationContext
    private val keyStore = AdbKeyStore(context.applicationContext)
    private val preferences = context.applicationContext.getSharedPreferences("range_preferences", Context.MODE_PRIVATE)
    private val consumption = ConsumptionWindow()

    @Volatile private var client: AdbProtocolClient? = null
    /** A transport-level check only. Does NOT confirm vehicle telemetry or writes. */
    fun hasConnectedSession(): Boolean =
        state == "connected" && client?.isConnected() == true
    /** In-memory only. Never persisted as a write capability or reused across reconnects. */
    @Volatile private var nativeStreamCatalog: StreamCatalog? = null
    private data class StreamCatalog(val device: Int, val socFid: Int, val odometerFid: Int)


    @Volatile private var state: String = "not_connected"
    @Volatile private var failureReason: String? = null
    @Volatile private var androidVersion: String? = null

    /** Must run on a dedicated worker: ADB may show an approval dialog for up to 60 seconds. */
    @Synchronized
    fun connect(): Map<String, Any?> {
        if (state == "connected" && client?.isConnected() == true) return diagnostics()
        state = "connecting"
        nativeStreamCatalog = null
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
     * P2: operator-invoked, read-only, firmware-derived FID handshake.
     *
     * The public BYDMate Song Plus fixture differs from this owner's actual
     * DiLink5.0 FIDs. Until scales can be independently decoded, require the
     * exact three values already observed on the owner's real unit. Any other
     * firmware is explicitly unsupported, without falling back to guessing.
     */
    @Synchronized
    fun prepareNativeCandidateStream(): Map<String, Any?> {
        nativeStreamCatalog = null
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf("ok" to false, "reason" to "adb_not_connected")
        }
        val cmd = FidReadOnlyDiscovery.command(appContext.packageCodePath)
            ?: return mapOf("ok" to false, "reason" to "invalid_apk_path")
        return try {
            val active = client ?: return mapOf("ok" to false, "reason" to "adb_not_connected")
            val discovery = FidReadOnlyDiscovery.parse(active.exec(cmd))
            if (!discovery.readyForCandidateProbes) {
                return mapOf("ok" to false, "reason" to "firmware_catalog_unavailable")
            }
            val device = discovery.catalog.deviceOf("STATISTIC")
            val socFid = discovery.catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE")
            val odometerFid = discovery.catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE")
            val model = FirmwareDiagnostics.property(active.exec("getprop ro.product.model"))
            val manufacturer = FirmwareDiagnostics.property(
                active.exec("getprop ro.product.manufacturer")
            )
            val sdk = FirmwareDiagnostics.sdkInt(active.exec("getprop ro.build.version.sdk"))
            // Same reflected FIDs can occur on another platform. Check firmware
            // family AND device/symbols before applying the observed mileage scale.
            if (!OwnerFirmwareReadGate.accepts(
                    model, manufacturer, sdk, device, socFid, odometerFid
                )) {
                return mapOf("ok" to false, "reason" to "firmware_fids_or_scale_unvalidated")
            }
            nativeStreamCatalog = StreamCatalog(device!!, socFid!!, odometerFid!!)
            mapOf(
                "ok" to true, "catalogMatched" to true,
                "reason" to "owner_observed_fids_only",
                "firmwareVerified" to false,
                "vehicleWriteAuthorized" to false
            )
        } catch (_: Exception) {
            mapOf("ok" to false, "reason" to "firmware_reflection_failed")
        }
    }

    /**
     * P2 native read-only candidate tick. Exactly TWO fixed autoservice GETs;
     * no energy counter, no control/permission/daemon/SET transactions.
     */
    @Synchronized
    fun nativeCandidateTick(): Map<String, Any?> {
        val catalog = nativeStreamCatalog
            ?: return mapOf("ok" to false, "reason" to "catalog_not_prepared")
        if (state != "connected" || client?.isConnected() != true) {
            nativeStreamCatalog = null
            return mapOf("ok" to false, "reason" to "adb_disconnected")
        }
        return try {
            val active = client ?: return mapOf("ok" to false, "reason" to "adb_disconnected")
            val soc = CandidateTelemetry.soc(active.exec(
                "service call autoservice 7 i32 ${catalog.device} i32 ${catalog.socFid}"
            ))
            val odometer = CandidateTelemetry.int(active.exec(
                "service call autoservice 5 i32 ${catalog.device} i32 ${catalog.odometerFid}"
            ))?.takeIf { it in 0..10_000_000 }?.div(10.0)

            mapOf(
                "ok" to (soc != null && odometer != null),
                "catalogMatched" to true,
                "socCandidatePercent" to soc,
                "odometerCandidateKm" to odometer,
                "capturedAtEpochMs" to System.currentTimeMillis(),
                "reason" to if (soc != null && odometer != null)
                    "requires_ongoing_oem_validation" else "invalid_candidate_value",
                "firmwareVerified" to false,
                "vehicleWriteAuthorized" to false
            )
        } catch (_: Exception) {
            mapOf("ok" to false, "reason" to "native_candidate_read_failed")
        }
    }

    /** No background helper or subprocess; invalidate FID session on Stop. */
    fun stopNativeCandidateStream() {
        nativeStreamCatalog = null
    }

    /**
     * P1/P2 research: operator-invoked SINGLE motion observation.
     *
     * The upstream BYDMate FID catalog demonstrates getFloat speed and getInt
     * gearbox contracts but their meanings are not validated on this Song Plus.
     * All FIDs are reflected from the running firmware; no Leopard 3 or fixture
     * fallback. This method never produces "parked", a km/h label or permission
     * for any vehicle control.
     */
    @Synchronized
    fun probeMotionCandidates(): Map<String, Any?> {
        fun unavailable(reason: String): Map<String, Any?> = mapOf(
            "ok" to false,
            "status" to reason,
            "speedUnitsVerified" to false,
            "gearEnumVerified" to false,
            "stationaryStateVerified" to false,
            "vehicleWriteAuthorized" to false,
            "automaticEventEligible" to false
        )
        if (state != "connected" || client?.isConnected() != true) {
            return unavailable("adb_not_connected")
        }
        val command = FidReadOnlyDiscovery.command(appContext.packageCodePath)
            ?: return unavailable("invalid_apk_path")
        return try {
            val active = client ?: return unavailable("adb_not_connected")
            val discovery = FidReadOnlyDiscovery.parse(active.exec(command))
            if (!discovery.readyForCandidateProbes ||
                !discovery.readyForMotionCandidateProbes) {
                return unavailable("motion_fids_not_present")
            }
            val catalog = discovery.catalog
            val model = FirmwareDiagnostics.property(active.exec("getprop ro.product.model"))
            val manufacturer = FirmwareDiagnostics.property(
                active.exec("getprop ro.product.manufacturer")
            )
            val sdk = FirmwareDiagnostics.sdkInt(active.exec("getprop ro.build.version.sdk"))
            val ownerGate = OwnerFirmwareReadGate.accepts(
                model, manufacturer, sdk,
                catalog.deviceOf("STATISTIC"),
                catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE"),
                catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE")
            )
            val speedDevice = catalog.deviceOf("SPEED")
            val gearboxDevice = catalog.deviceOf("GEARBOX")
            val speedFid = catalog.fidOf("Speed.SPEED_AUTO_SPEED")
            val gearFid = catalog.fidOf("Gearbox.GEARBOX_AUTO_MODE_TYPE")
            // Only research the two known read-only BYD device groups. Firmware
            // must supply the FID values itself (they vary between builds).
            if (!ownerGate || speedDevice != 1013 || gearboxDevice != 1011 ||
                speedFid == null || gearFid == null) {
                return unavailable("motion_firmware_unverified")
            }

            val speed = CandidateTelemetry.float(active.exec(
                "service call autoservice 7 i32 $speedDevice i32 $speedFid"
            ))
            val gear = CandidateTelemetry.int(active.exec(
                "service call autoservice 5 i32 $gearboxDevice i32 $gearFid"
            ))
            MotionCandidatePolicy.interpret(speed, gear)
                .plus("capturedAtEpochMs" to System.currentTimeMillis())
        } catch (_: Exception) {
            unavailable("motion_probe_failed")
        }
    }

    /**
     * Foreground MVP dashboard sample: only the two previously OEM-matched
     * read-only FIDs (SOC and odometer). Intentionally independent of the
     * unresolved cumulative energy counter, trip window, and charging state.
     * Never marks a candidate as verified telemetry.
     */
    @Synchronized
    fun sampleDashboardCandidates(): Map<String, Any?> {
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf(
                "ok" to false, "reason" to "adb_not_connected",
                "firmwareVerified" to false, "vehicleWriteAuthorized" to false
            )
        }
        val active = client ?: return mapOf(
            "ok" to false, "reason" to "adb_not_connected",
            "firmwareVerified" to false, "vehicleWriteAuthorized" to false
        )
        val soc = CandidateTelemetry.soc(active.exec(CandidateTelemetry.SOC))
        val odometer = CandidateTelemetry.odometerKm(active.exec(CandidateTelemetry.ODOMETER))
        return mapOf(
            "ok" to (soc != null && odometer != null),
            "socCandidatePercent" to soc,
            "odometerCandidateKm" to odometer,
            "capturedAtEpochMs" to System.currentTimeMillis(),
            "source" to "read_only_oem_matched_candidate_fids",
            "firmwareVerified" to false,
            "vehicleWriteAuthorized" to false,
            "reason" to if (soc != null && odometer != null) {
                "requires_continuous_device_validation"
            } else "missing_or_invalid_candidate"
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
        // Preserve raw research window diagnostics only. This counter's
        // physical units and whether DC charging advances it remain unverified
        // on the owner's car; deriving kWh/100km or range would be misleading.
        consumption.add(sample)
        val capacity = (capacityStatus()["usableCapacityKwh"] as? Number)?.toDouble()
        return mapOf(
            "ok" to sample.valid,
            "socCandidatePercent" to sample.soc,
            "energyCandidateKwh" to sample.energyKwh,
            "odometerCandidateKm" to sample.odometerKm,
            "consumptionCandidateKwhPer100km" to null,
            "rangeCandidateKm" to null,
            "usableCapacityKwh" to capacity,
            "sampleCount" to consumption.stats()["samples"],
            "windowDistanceKm" to consumption.stats()["windowDistanceKm"],
            "firmwareVerified" to false,
            "source" to "BYDMate_v4.0.1_unverified_fallbacks",
            "odometerScaleAssumption" to "0.1_km_per_tick_LEOPARD3_NOT_VERIFIED_SONG",
            "reason" to if (sample.valid) "requires_firmware_validation" else "unsupported_or_incomplete_read",
        )
    }

    /**
     * A one-shot read-only shell classpath probe: inspect firmware constants using
     * this signed APK. No background process, daemon, Binder registration or writes.
     * Every result is a candidate until independently confirmed on this car.
     */
    @Synchronized
    fun discoverFidCatalog(): Map<String, Any?> {
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf("ok" to false, "status" to "adb_not_connected",
                "firmwareVerified" to false, "vehicleWriteAuthorized" to false)
        }
        val command = FidReadOnlyDiscovery.command(appContext.packageCodePath)
            ?: return mapOf("ok" to false, "status" to "invalid_apk_path",
                "firmwareVerified" to false, "vehicleWriteAuthorized" to false)
        return try {
            val active = client ?: return mapOf("ok" to false, "status" to "adb_not_connected")
            val discovered = FidReadOnlyDiscovery.parse(active.exec(command))
            val info = discovered.diagnostics().toMutableMap()
            if (!discovered.readyForCandidateProbes) return info

            // Dynamic SDK addresses are NOT permission or unit validation. These are
            // one-shot GET transactions only (5 = getInt, 7 = getFloat).
            val device = discovered.catalog.deviceOf("STATISTIC")!!
            val socFid = discovered.catalog.fidOf("Statistic.STATISTIC_ELEC_PERCENTAGE")!!
            val mileageFid = discovered.catalog.fidOf("Statistic.STATISTIC_TOTAL_MILEAGE")!!
            val soc = CandidateTelemetry.soc(active.exec(
                "service call autoservice 7 i32 $device i32 $socFid"))
            val mileageRaw = CandidateTelemetry.int(active.exec(
                "service call autoservice 5 i32 $device i32 $mileageFid"))

            info["socCatalogCandidatePercent"] = soc
            // Raw integer only. The current fallback uses 0.1 km/tick; catalog
            // addresses on some Song Plus firmwares use 1 km/tick. NO guessed scale.
            info["odometerCatalogRawCandidate"] = mileageRaw
            info["catalogReadsOk"] = soc != null && mileageRaw != null
            info["odometerCatalogScaleVerified"] = false
            info
        } catch (_: Exception) {
            mapOf("ok" to false, "status" to "firmware_probe_failed",
                "firmwareVerified" to false, "vehicleWriteAuthorized" to false)
        }
    }

    /**
     * Explicitly requested charging-state snapshot. Reflects only allowlisted
     * BYD SDK constants and makes at most three autoservice GET int calls.
     * Each sample starts fresh: no stale FID cache, background poll, events or
     * inferred charge-completed notifications.
     */
    @Synchronized
    fun sampleChargingCandidates(): Map<String, Any?> {
        fun unavailable(status: String): Map<String, Any?> = mapOf(
            "ok" to false, "status" to status,
            "chargeStateCandidate" to "unknown",
            "firmwareVerified" to false,
            "automaticEventEligible" to false,
            "vehicleWriteAuthorized" to false
        )
        if (state != "connected" || client?.isConnected() != true) {
            return unavailable("adb_not_connected")
        }
        val command = FidReadOnlyDiscovery.command(appContext.packageCodePath)
            ?: return unavailable("invalid_apk_path")
        return try {
            val active = client ?: return unavailable("adb_not_connected")
            val reflected = FidReadOnlyDiscovery.parse(active.exec(command))
            if (!reflected.readyForChargingCandidateProbes) {
                return unavailable("charging_fids_unavailable")
            }
            val catalog = reflected.catalog
            val device = catalog.deviceOf("CHARGING") ?: return unavailable("charging_device_missing")
            val gunFid = catalog.fidOf("Charging.CHARGING_GUN_CONNECT_STATE")
                ?: return unavailable("charging_gun_fid_missing")
            val bmsFid = catalog.fidOf("Charging.CHARGING_BATTERRY_DEVICE_STATE")
                ?: return unavailable("charging_bms_fid_missing")
            val typeFid = catalog.fidOf("Charging.CHARGING_TYPE")
            val gunRaw = CandidateTelemetry.int(active.exec(
                "service call autoservice 5 i32 $device i32 $gunFid"
            ))
            val bmsRaw = CandidateTelemetry.int(active.exec(
                "service call autoservice 5 i32 $device i32 $bmsFid"
            ))
            val typeRaw = typeFid?.let { fid ->
                CandidateTelemetry.int(active.exec(
                    "service call autoservice 5 i32 $device i32 $fid"
                ))
            }
            ChargingCandidateState.fromRaw(gunRaw, bmsRaw, typeRaw)
                .plus("status" to "candidate_sampled")
                .plus("catalogResolved" to true)
                .plus("capturedAtEpochMs" to System.currentTimeMillis())
        } catch (_: Exception) {
            unavailable("charging_probe_failed")
        }
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

    /** Operator-invoked, FIXED read-only property and service liveness checks. */
    @Synchronized
    fun probeFirmware(): Map<String, Any?> {
        if (state != "connected" || client?.isConnected() != true) {
            return mapOf("ok" to false, "reason" to "adb_not_connected")
        }
        val active = client ?: return mapOf("ok" to false, "reason" to "adb_not_connected")
        val model = FirmwareDiagnostics.property(active.exec("getprop ro.product.model"))
        val manufacturer = FirmwareDiagnostics.property(active.exec("getprop ro.product.manufacturer"))
        val sdk = FirmwareDiagnostics.sdkInt(active.exec("getprop ro.build.version.sdk"))
        val fingerprint = FirmwareDiagnostics.fingerprintDigest(
            active.exec("getprop ro.build.fingerprint")
        )
        val serviceAvailable = FirmwareDiagnostics.servicePresent(
            active.exec("service check autoservice")
        )
        return mapOf(
            "ok" to (model != null || sdk != null),
            "model" to model,
            "manufacturer" to manufacturer,
            "androidSdk" to sdk,
            "fingerprintHashPrefix" to fingerprint,
            "autoservicePresent" to serviceAvailable,
            "compatibleAndVerified" to false,
            "vehicleWriteAuthorized" to false,
            "hint" to "compare_properties_with_stock_firmware_then_test_FID_catalog",
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
        nativeStreamCatalog = null
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
        "vehicleTelemetry" to "native_candidate_stream_preview_not_verified",
        "writeEnabled" to false,
    )

    val transportState: String get() = state
}
