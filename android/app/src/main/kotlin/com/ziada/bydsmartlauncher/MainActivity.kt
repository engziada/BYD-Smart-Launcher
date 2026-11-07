package com.ziada.bydsmartlauncher

import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.view.KeyEvent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import com.ziada.bydsmartlauncher.automation.LocalAutomationRepository

/**
 * Native ADB diagnostics + foreground, operator-enabled firmware-gated READ-only candidate
 * stream. No vehicle writes, persistent helper, camera access or verified safety interlocks.
 */
class MainActivity : FlutterActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adb by lazy { SafeAdbTransport(applicationContext) }
    private val telegram by lazy { TelegramBotService(applicationContext) }
    private val ruleStore by lazy { LocalAutomationRepository(applicationContext) }
    private val voice by lazy { OfflineVoiceAssistant(this, ::voiceAnswer) }
    private val candidateStream by lazy {
        NativeReadOnlyStream(adb, worker, mainHandler) { sample ->
            lastTelemetrySample = sample + mapOf("sessionValid" to true)
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

    /** Invalidate only EXISTING prior observations, not an empty report section. */
    private fun historical(sample: Map<String, Any?>): Map<String, Any?> =
        if (sample.isEmpty()) sample else sample + mapOf("sessionValid" to false)

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
                        result.success(null)
                    }
                    "text" -> {
                        val command = call.arguments as? String
                        if (command != null && command.length in 1..120) voice.submitText(command)
                        result.success(null)
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
                                    candidateStream.currentStatus(), lastMotionSample
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
                        "automations" to false, "realRange" to false, "controls" to false
                    ))
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
                                else -> ruleStore.setMaster(call.argument<Boolean>("enabled") ?: false)
                            }
                            mainHandler.post { result.success(response) }
                        }
                    }
                    "telegramStatus" -> result.success(telegram.status())
                    "telegramOutboxStatus" -> result.success(telegram.outboxStatus())
                    "rangeCapacityStatus" -> result.success(adb.capacityStatus())
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

    private fun voiceAnswer(command: VoiceCommand, arabic: Boolean): String {
        fun localized(en: String, ar: String) = if (arabic) ar else en
        val current = candidateStream.currentStatus()
        val capturedAt = (current["capturedAtEpochMs"] as? Number)?.toLong()
        val age = capturedAt?.let { System.currentTimeMillis() - it }
        val fresh = adb.transportState == "connected" && current["streamState"] == "candidate" &&
            age != null && age in 0..15_000L
        fun read(key: String): Double? = if (fresh) (current[key] as? Number)?.toDouble() else null
        val unavailable = localized("No current reading. Start the read-only stream.",
            "مفيش قراءة حديثة. شغّل بث قراءة بيانات العربية.")
        return when (command) {
            VoiceCommand.BATTERY -> read("socCandidatePercent")?.let {
                localized("Unverified battery reading: $it percent.",
                    "قراءة البطارية غير مؤكدة: $it في المية.")
            } ?: unavailable
            VoiceCommand.ODOMETER -> read("odometerCandidateKm")?.let {
                localized("Unverified mileage: $it kilometers.", "قراءة العداد غير مؤكدة: $it كيلومتر.")
            } ?: unavailable
            VoiceCommand.TYRES -> localized("Tyre pressure is unavailable.", "قراءة ضغط الكاوتش غير متاحة.")
            VoiceCommand.COMFORT -> localized("Vehicle controls are unavailable.", "التحكم في العربية غير متاح.")
            VoiceCommand.UNKNOWN -> localized("Try battery level or odometer.", "جرّب نسبة البطارية أو قراءة العداد.")
        }
    }

    override fun onStart() {
        super.onStart()
        voice.setForeground(true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST && voice.canStart) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) voice.start()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == OfflineVoiceAssistant.MIC_REQUEST) {
            voice.onPermissionResult(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    override fun onStop() {
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
