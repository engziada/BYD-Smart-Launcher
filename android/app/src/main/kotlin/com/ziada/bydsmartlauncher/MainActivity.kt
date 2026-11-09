package com.ziada.bydsmartlauncher

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import com.ziada.bydsmartlauncher.automation.LocalAutomationRepository
import com.ziada.bydsmartlauncher.automation.VehicleAlertRouter
import com.ziada.bydsmartlauncher.automation.VerifiedChargeState
import com.ziada.bydsmartlauncher.automation.VerifiedSignal

/**
 * Native ADB diagnostics, candidate telemetry and the foreground comfort control bridge.
 */
class MainActivity : FlutterActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adb by lazy { SafeAdbTransport(applicationContext) }
    private val telegram by lazy { TelegramBotService.shared(applicationContext) }
    private val ruleStore by lazy { LocalAutomationRepository(applicationContext) }
    private val measuredTrips by lazy { MeasuredTripHistory(applicationContext) }
    private val alerts = VehicleAlertRouter()
    private val voice by lazy { OfflineVoiceAssistant(this, ::voiceAnswer) }
    private val wheelVoiceHandler: (KeyEvent) -> Boolean = ::handleVoiceKey
    private val candidateStream by lazy {
        NativeReadOnlyStream(adb, worker, mainHandler) { sample ->
            lastTelemetrySample = sample + mapOf("sessionValid" to true)
            observeVehicleAlerts(sample)
        }
    }
    private val methods = "com.ziada.byd_smart_launcher/vehicle"
    private val events = "com.ziada.byd_smart_launcher/telemetry"
    private val nativeCandidateEvents = "com.ziada.byd_smart_launcher/native_candidate_stream"
    @Volatile private var lastFirmwareProbe: Map<String, Any?> = emptyMap()
    @Volatile private var lastTelemetrySample: Map<String, Any?> = emptyMap()
    @Volatile private var lastFidDiscovery: Map<String, Any?> = emptyMap()
    @Volatile private var lastChargingSample: Map<String, Any?> = emptyMap()
    @Volatile private var lastMotionSample: Map<String, Any?> = emptyMap()
    @Volatile private var lastOwnerSensors: Map<String, Any?> = emptyMap()
    @Volatile private var lastComfortMappings: Map<String, Any?> = emptyMap()
    @Volatile private var comfortWriteHistory: List<Map<String, Any?>> = emptyList()
    @Volatile private var comfortGeneration = 0L
    private var resumed = false
    private var comfortDialog: AlertDialog? = null
    private var comfortWriteInFlight = false

    /** Invalidate only EXISTING prior observations, not an empty report section. */
    private fun historical(sample: Map<String, Any?>): Map<String, Any?> =
        if (sample.isEmpty()) sample else sample + mapOf("sessionValid" to false)

    private fun observeVehicleAlerts(sample: Map<String, Any?>) {
        // CandidateStreamPolicy never sets these two gates. A firmware-verified source
        // must provide event eligibility as well as independently validated state.
        if (sample["firmwareVerified"] != true || sample["automaticEventEligible"] != true) return
        val time = (sample["capturedAtEpochMs"] as? Number)?.toLong() ?: return
        val charge = VerifiedChargeState.entries.firstOrNull { it.name == sample["verifiedChargeState"] }
        val signal = VerifiedSignal(
            timestampMs = time,
            verified = true,
            socPercent = (sample["verifiedSocPercent"] as? Number)?.toDouble(),
            powerOn = sample["verifiedPowerOn"] as? Boolean,
            tripId = sample["verifiedTripId"] as? String,
            chargeState = charge,
        )
        worker.execute {
            alerts.observe(signal, System.currentTimeMillis(), ruleStore.enabledRules(), telegram::queueVerifiedVehicleEvent)
        }
    }

    private fun snapshot(): Map<String, Any?> = mapOf(
        "connected" to false, // ADB != verified live BYD car telemetry
        "adbState" to adb.transportState,
        "helperState" to adb.helperState,
        "socPercent" to null,
        "speedKmh" to null,
        "gear" to null,
        "powerKw" to null,
        "turnSignal" to null,
        "rangeEstimateKm" to null,
        "avgKwhPer100km" to null,
        "capturedAtEpochMs" to System.currentTimeMillis(),
        "source" to "unavailable"
    )

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.ziada.byd_smart_launcher/voice")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "start" -> {
                        voice.start()
                        result.success(null)
                    }
                    "language" -> {
                        voice.setLanguage(call.arguments == "ar")
                        result.success(null)
                    }
                    "demo" -> {
                        voice.setDemo(call.arguments == true)
                        if (call.arguments == true) cancelComfortConfirmation()
                        result.success(null)
                    }
                    "text" -> {
                        val command = call.arguments as? String
                        if (command != null && command.length in 1..120) voice.submitText(command)
                        result.success(null)
                    }
                    "wheelSettings" -> {
                        try {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            result.success(null)
                        } catch (_: android.content.ActivityNotFoundException) {
                            result.error("settings_unavailable", null, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methods)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSnapshot" -> result.success(snapshot())
                    "getDiagnostics" -> result.success(adb.diagnostics().plus(
                        "streamState" to candidateStream.currentStatus()["streamState"]
                    ))
                    "nativeCandidateStreamStatus" -> result.success(candidateStream.currentStatus())
                    "nativeCandidateStreamStart" -> result.success(candidateStream.start())
                    "nativeCandidateStreamStop" -> result.success(candidateStream.stop())
                    "diagnosticsExport" -> {
                        worker.execute {
                            val response = try {
                                DiagnosticReportWriter(applicationContext).save(
                                    adb.diagnostics(), lastFirmwareProbe, lastTelemetrySample,
                                    lastFidDiscovery, lastChargingSample,
                                    candidateStream.currentStatus(), lastMotionSample, lastOwnerSensors,
                                    lastComfortMappings, comfortWriteHistory
                                )
                            } catch (_: Exception) {
                                mapOf("ok" to false, "error" to "export_failed")
                            }
                            mainHandler.post { result.success(response) }
                        }
                    }
                    "getCapabilities" -> result.success(mapOf(
                        "adb" to (adb.transportState == "connected"),
                        "blindSpot" to false, "telegram" to false,
                        "automations" to false, "realRange" to false,
                        "controls" to ComfortMapping.entries.any(adb::comfortWriteAvailable)
                    ))
                    "comfortWrite" -> {
                        val mapping = ComfortMapping.entries.firstOrNull { it.id == call.argument<String>("id") }
                        val value = call.argument<Any>("value") as? Int
                        if (mapping == null || value == null) result.success(ComfortWriteResult("invalid_value").asMap())
                        else confirmComfortWrite(mapping, value, arabic = false) { result.success(it.asMap()) }
                    }
                    "ownerSensors", "comfortMappings" -> worker.execute {
                        val sensors = call.method == "ownerSensors"
                        val reply = runCatching {
                            if (sensors) adb.sampleOwnerSensors(includeVehicleCatalog = true)
                            else adb.probeComfortMappings()
                        }.getOrElse { mapOf("ok" to false, "status" to "vehicle_probe_failed") }
                        if (sensors) lastOwnerSensors = reply else lastComfortMappings = reply
                        mainHandler.post { result.success(reply) }
                    }
                    "automationStatus" -> result.success(ruleStore.status())
                    "automationAdd", "automationToggle", "automationRemove", "automationMasterSet" -> {
                        worker.execute {
                            val response = when (call.method) {
                                "automationAdd" -> ruleStore.add(
                                    call.argument<String>("kind") ?: "",
                                    call.argument<Number>("thresholdPercent")?.toDouble()
                                )
                                "automationToggle" -> ruleStore.toggle(
                                    call.argument<String>("id") ?: "",
                                    call.argument<Boolean>("enabled") ?: false
                                )
                                "automationRemove" -> ruleStore.remove(call.argument<String>("id") ?: "")
                                else -> {
                                    val enabled = call.argument<Boolean>("enabled") == true
                                    ruleStore.setMaster(enabled).also {
                                        if (!enabled && it["ok"] == true) telegram.clearVehicleEvents()
                                    }
                                }
                            }
                            mainHandler.post { result.success(response) }
                        }
                    }
                    "telegramStatus", "telegramOutboxStatus", "telegramSetAutomaticRetry" -> worker.execute {
                        val reply = when (call.method) {
                            "telegramStatus" -> telegram.status()
                            "telegramOutboxStatus" -> telegram.outboxStatus()
                            else -> telegram.setAutomaticRetry(call.argument<Boolean>("enabled") == true)
                        }
                        mainHandler.post { result.success(reply) }
                    }
                    "rangeCapacityStatus" -> result.success(adb.capacityStatus())
                    "measuredTripsStatus", "measuredTripsRecord", "measuredTripsClear" -> worker.execute {
                        val reply = when (call.method) {
                            "measuredTripsRecord" -> measuredTrips.record(
                                call.argument<Number>("distanceKm")?.toDouble(),
                                call.argument<Number>("drivingKwh")?.toDouble(),
                                call.argument<Boolean>("eligible") == true,
                            )
                            "measuredTripsClear" -> measuredTrips.clear()
                            else -> measuredTrips.status()
                        }
                        mainHandler.post { result.success(reply) }
                    }
                    "rangeCapacitySet" -> {
                        val value = call.argument<Number>("kwh")?.toDouble()
                        result.success(adb.configureCapacity(value))
                    }
                    "planningConsumptionStatus" ->
                        result.success(adb.planningConsumptionStatus())
                    "planningConsumptionSet" -> {
                        val value = call.argument<Number>("kwhPer100Km")?.toDouble()
                        result.success(adb.planningConsumptionSet(value))
                    }
                    "telegramSendStatus" -> {
                        // Snapshot the accepted stream state on the main thread.
                        val active = candidateStream.currentStatus()["streamState"] == "candidate"
                        val connected = adb.hasConnectedSession()
                        val sample = lastTelemetrySample.toMap()
                        if (!active || !connected) {
                            result.success(mapOf(
                                "ok" to false, "sent" to false,
                                "error" to "active_fresh_stream_required"
                            ))
                        } else {
                            worker.execute {
                                val response = try {
                                    telegram.sendManualStatus(sample, System.currentTimeMillis())
                                } catch (_: Exception) {
                                    mapOf("ok" to false, "sent" to false, "error" to "send_failed")
                                }
                                mainHandler.post { result.success(response) }
                            }
                        }
                    }
                    "telegramSave", "telegramClear", "telegramTest",
                    "telegramRetry", "telegramClearPending" -> {
                        worker.execute {
                            val response = when (call.method) {
                                "telegramSave" -> telegram.configure(
                                    call.argument<String>("token") ?: "",
                                    call.argument<String>("chatId") ?: ""
                                )
                                "telegramClear" -> telegram.clear()
                                "telegramRetry" -> telegram.retryPending()
                                "telegramClearPending" -> telegram.clearPending()
                                else -> telegram.sendTest()
                            }
                            mainHandler.post { result.success(response) }
                        }
                    }
                    "adbConnect", "adbDisconnect", "adbProbe", "adbProbeSoc",
                    "adbProbeFirmware", "telemetrySample", "dashboardSample", "adbProbeFids",
                    "chargingSample", "motionProbe" -> {
                        if (AdbSessionChangePolicy.shouldStopStream(
                                call.method, adb.hasConnectedSession()
                            )) {
                            candidateStream.stop("adb_session_changed")
                        }
                        worker.execute {
                            val response = when (call.method) {
                                "adbConnect" -> {
                                    val reused = adb.hasConnectedSession()
                                    if (!reused) {
                                        // Keep the historical observations for export, but
                                        // explicitly invalidate them across actual sessions.
                                        lastChargingSample = historical(lastChargingSample)
                                        lastMotionSample = historical(lastMotionSample)
                                        lastTelemetrySample = historical(lastTelemetrySample)
                                    }
                                    adb.connect() + mapOf("sessionReused" to reused)
                                }
                                "adbDisconnect" -> {
                                    lastChargingSample = historical(lastChargingSample)
                                    lastMotionSample = historical(lastMotionSample)
                                    lastTelemetrySample = historical(lastTelemetrySample)
                                    adb.disconnect()
                                }
                                "adbProbeSoc" -> adb.probeSoc()
                                "adbProbeFirmware" -> adb.probeFirmware().also { lastFirmwareProbe = it }
                                "adbProbeFids" -> adb.discoverFidCatalog().also { lastFidDiscovery = it }
                                "chargingSample" -> adb.sampleChargingCandidates().also {
                                    lastChargingSample = it + mapOf("sessionValid" to true)
                                }
                                "motionProbe" -> adb.probeMotionCandidates().also {
                                    lastMotionSample = it + mapOf("sessionValid" to true)
                                }
                                "telemetrySample" -> adb.sampleTelemetry().also {
                                    lastTelemetrySample = it + mapOf("sessionValid" to true)
                                }
                                "dashboardSample" -> adb.sampleDashboardCandidates().also {
                                    lastTelemetrySample = it + mapOf("sessionValid" to true)
                                }
                                else -> adb.probe()
                            }
                            mainHandler.post { result.success(response) }
                        }
                    }
                    else -> result.notImplemented()
                }
            }
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, events)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    sink.success(snapshot())
                }
                override fun onCancel(arguments: Any?) {}
            })
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, nativeCandidateEvents)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    candidateStream.listen(sink)
                }
                override fun onCancel(arguments: Any?) {
                    candidateStream.cancel()
                }
            })
    }

    private fun voiceAnswer(phrase: String, arabic: Boolean, respond: (String) -> Unit) {
        fun localized(en: String, ar: String) = if (arabic) ar else en
        val comfort = ComfortVoiceRequest.parse(phrase)
        if (comfort != null) {
            val mapping = comfort.mapping
            val label = if (arabic) mapping.arabicLabel else mapping.label
            fun spokenState(value: Int?) = when {
                value == null -> localized("State unavailable", "الحالة غير متاحة")
                mapping == ComfortMapping.AC_POWER -> if (value == 1) {
                    localized("On", "شغّال")
                } else localized("Off", "مقفول")
                else -> value.toString()
            }
            fun requestWrite(value: Int) = confirmComfortWrite(mapping, value, arabic) {
                respond(when (it.status) {
                    "confirmed", "already_at_target" -> "$label: ${spokenState(it.readback)}."
                    "firmware_not_verified" -> localized("This control needs on-car verification.", "التحكم ده محتاج تحقق على العربية.")
                    "celsius_required" -> localized("Set the factory climate display to Celsius first.", "خلّي شاشة التكييف على المئوي الأول.")
                    "outside_pilot_range" -> localized("The value is outside the allowed range.", "القيمة برا النطاق المسموح.")
                    "cancelled" -> localized("Command cancelled.", "تم إلغاء الأمر.")
                    "write_outcome_unknown", "sent_unconfirmed" -> localized("Check the result on the car.", "راجع النتيجة على شاشة العربية.")
                    else -> localized("The control request was blocked or refused.", "أمر التحكم ما اتنفذش.")
                })
            }
            val target = comfort.target
            if (target != null) {
                requestWrite(target)
            } else {
                val generation = comfortGeneration
                worker.execute {
                    val readback = runCatching { adb.probeComfortMappings(mapping) }.getOrDefault(emptyMap())
                    val value = readback["${mapping.id}_state"] as? Int
                    mainHandler.post {
                        if (!resumed || generation != comfortGeneration) return@post
                        when {
                            value == null -> respond(localized("The control state is unavailable.", "قراءة الحالة غير متاحة."))
                            comfort.lowerFan && readback["${mapping.id}_writable"] != true -> respond(localized(
                                "This control needs on-car verification.", "التحكم ده محتاج تحقق على العربية."
                            ))
                            comfort.lowerFan && value > (readback["${mapping.id}_writeMin"] as? Int ?: mapping.range.first) ->
                                requestWrite(value - 1)
                            else -> respond(localized("$label reading: ${spokenState(value)}.", "قراءة $label: ${spokenState(value)}."))
                        }
                    }
                }
            }
            return
        }
        val command = VoiceCommand.parse(phrase)
        if (command in setOf(VoiceCommand.TYRES, VoiceCommand.CABIN_TEMP, VoiceCommand.OUTSIDE_TEMP)) {
            worker.execute {
                val data = runCatching { adb.sampleOwnerSensors(includeVehicleCatalog = command != VoiceCommand.TYRES) }
                    .getOrDefault(emptyMap())
                val response = if (command == VoiceCommand.TYRES) {
                    val wheels = OwnerSensorReport.wheels.map { data[it] as? Int }
                    if (wheels.any { it != null }) localized(
                        "Unverified tyre pressures in kilopascals. Front left ${wheels[0] ?: "unavailable"}, front right ${wheels[1] ?: "unavailable"}, rear left ${wheels[2] ?: "unavailable"}, rear right ${wheels[3] ?: "unavailable"}.",
                        "قراءات ضغط غير مؤكدة بالكيلوباسكال. قدام شمال ${wheels[0] ?: "غير متاح"}، قدام يمين ${wheels[1] ?: "غير متاح"}، ورا شمال ${wheels[2] ?: "غير متاح"}، ورا يمين ${wheels[3] ?: "غير متاح"}."
                    ) else localized("Tyre pressure is unavailable.", "قراءة ضغط الكاوتش غير متاحة.")
                } else {
                    val key = if (command == VoiceCommand.CABIN_TEMP) "cabinCelsius" else "outsideCelsius"
                    val value = data[key] as? Int
                    if (value != null) localized(
                        "Unverified temperature: $value degrees Celsius.",
                        "قراءة الحرارة غير مؤكدة: $value درجة مئوية."
                    ) else localized("Temperature is unavailable.", "قراءة الحرارة غير متاحة.")
                }
                mainHandler.post { respond(response) }
            }
            return
        }
        val current = candidateStream.currentStatus()
        val capturedAt = (current["capturedAtEpochMs"] as? Number)?.toLong()
        val age = capturedAt?.let { System.currentTimeMillis() - it }
        val fresh = adb.transportState == "connected" && current["streamState"] == "candidate" &&
            age != null && age in 0..15_000L
        fun read(key: String): Double? = if (fresh) (current[key] as? Number)?.toDouble() else null
        val unavailable = localized("No current reading. Start the read-only stream.",
            "مفيش قراءة حديثة. شغّل بث قراءة بيانات العربية.")
        respond(when (command) {
            VoiceCommand.BATTERY -> read("socCandidatePercent")?.let {
                localized("Unverified battery reading: $it percent.",
                    "قراءة البطارية غير مؤكدة: $it في المية.")
            } ?: unavailable
            VoiceCommand.ODOMETER -> read("odometerCandidateKm")?.let {
                localized("Unverified mileage: $it kilometers.", "قراءة العداد غير مؤكدة: $it كيلومتر.")
            } ?: unavailable
            VoiceCommand.COMFORT -> localized("Vehicle controls are unavailable.", "التحكم في العربية غير متاح.")
            else -> localized("Try battery level, odometer, or tyre pressure.",
                "جرّب نسبة البطارية أو قراءة العداد أو ضغط الكاوتش.")
        })
    }

    private fun confirmComfortWrite(
        mapping: ComfortMapping, value: Int, arabic: Boolean, reply: (ComfortWriteResult) -> Unit,
    ) {
        val range = adb.comfortWriteRange(mapping)
        val blocked = when {
            !resumed || !voice.canStart -> "foreground_required"
            comfortDialog != null || comfortWriteInFlight -> "control_busy"
            value !in mapping.range -> "invalid_value"
            range == null -> "firmware_not_verified"
            value !in range -> "outside_pilot_range"
            else -> null
        }
        if (blocked != null) {
            reply(ComfortWriteResult(blocked))
            return
        }
        var approved = false
        val label = if (arabic) mapping.arabicLabel else mapping.label
        val target = when {
            mapping.isWindow -> if (arabic) "$value% فتح" else "$value% open"
            mapping == ComfortMapping.AC_TEMPERATURE -> "$value °C"
            mapping == ComfortMapping.AC_POWER -> if (value == 1) {
                if (arabic) "تشغيل" else "On"
            } else if (arabic) "إيقاف" else "Off"
            else -> value.toString()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (arabic) "تأكيد أمر التحكم" else "Confirm vehicle control")
            .setMessage(when {
                mapping.isWindow && arabic -> "$label: $target. اتأكد إن العربية في وضع P وإن الإيدين والأجسام بعيدة عن الشباك."
                mapping.isWindow -> "$label: $target. Confirm Park and keep hands and objects clear of the window."
                arabic -> "$label: $target. اتأكد إن العربية في وضع P وإن التغيير آمن."
                else -> "$label: $target. Confirm the car is in Park and the change is safe."
            })
            .setNegativeButton(if (arabic) "إلغاء" else "Cancel", null)
            .setPositiveButton(if (arabic) "تأكيد" else "Confirm") { _, _ ->
                approved = true
                val generation = comfortGeneration
                val confirmedAt = SystemClock.elapsedRealtime()
                comfortWriteInFlight = true
                worker.execute {
                    val outcome = runCatching {
                        adb.writeComfort(mapping, value, confirmedAt) { generation == comfortGeneration }
                    }.getOrElse { ComfortWriteResult("write_outcome_unknown") }
                    val receipt = outcome.asMap() + mapOf(
                        "id" to mapping.id, "requestedValue" to value, "atMs" to System.currentTimeMillis()
                    )
                    comfortWriteHistory = (comfortWriteHistory + listOf(receipt)).takeLast(24)
                    mainHandler.post {
                        comfortWriteInFlight = false
                        reply(outcome)
                    }
                }
            }
            .create()
        dialog.setOnDismissListener {
            if (comfortDialog === dialog) comfortDialog = null
            if (!approved) reply(ComfortWriteResult("cancelled"))
        }
        comfortDialog = dialog
        dialog.show()
    }

    private fun cancelComfortConfirmation() {
        comfortGeneration++
        comfortDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        cancelComfortConfirmation()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        voice.setForeground(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WheelVoiceService.foregroundHandler = wheelVoiceHandler
        else if (WheelVoiceService.foregroundHandler === wheelVoiceHandler) {
            WheelVoiceService.foregroundHandler = null
        }
    }

    private fun handleVoiceKey(event: KeyEvent): Boolean {
        if (!voice.canStart) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) voice.start()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (WheelVoiceService.isVoiceKey(event.keyCode) && handleVoiceKey(event)) ||
            super.dispatchKeyEvent(event)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == OfflineVoiceAssistant.MIC_REQUEST) {
            voice.onPermissionResult(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    override fun onStop() {
        if (WheelVoiceService.foregroundHandler === wheelVoiceHandler) {
            WheelVoiceService.foregroundHandler = null
        }
        voice.setForeground(false)
        candidateStream.stop("app_not_foreground")
        super.onStop()
    }

    override fun onDestroy() {
        voice.close()
        candidateStream.stop("activity_destroyed")
        worker.execute { adb.disconnect() }
        worker.shutdown()
        super.onDestroy()
    }
}
