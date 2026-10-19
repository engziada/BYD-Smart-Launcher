package com.ziada.bydsmartlauncher

import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import com.ziada.bydsmartlauncher.automation.LocalAutomationRepository

/**
 * Native bridge: ADB handshake and limited read-only Android-version probe. Still NO BYD
 * autoservice connection, no camera pipeline, and no vehicle control.
 */
class MainActivity : FlutterActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adb by lazy { SafeAdbTransport(applicationContext) }
    private val telegram by lazy { TelegramBotService(applicationContext) }
    private val ruleStore by lazy { LocalAutomationRepository(applicationContext) }
    private val methods = "com.ziada.byd_smart_launcher/vehicle"
    private val events = "com.ziada.byd_smart_launcher/telemetry"
    @Volatile private var lastFirmwareProbe: Map<String, Any?> = emptyMap()
    @Volatile private var lastTelemetrySample: Map<String, Any?> = emptyMap()

    private fun snapshot(): Map<String, Any?> = mapOf(
        "connected" to false, // ADB != verified live BYD car telemetry
        "adbState" to adb.transportState,
        "helperState" to "not_installed",
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
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methods)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSnapshot" -> result.success(snapshot())
                    "getDiagnostics" -> result.success(adb.diagnostics())
                    "diagnosticsExport" -> {
                        worker.execute {
                            val response = try {
                                DiagnosticReportWriter(applicationContext).save(
                                    adb.diagnostics(), lastFirmwareProbe, lastTelemetrySample
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
                    "adbProbeFirmware", "telemetrySample" -> {
                        worker.execute {
                            val response = when (call.method) {
                                "adbConnect" -> adb.connect()
                                "adbDisconnect" -> adb.disconnect()
                                "adbProbeSoc" -> adb.probeSoc()
                                "adbProbeFirmware" -> adb.probeFirmware().also { lastFirmwareProbe = it }
                                "telemetrySample" -> adb.sampleTelemetry().also { lastTelemetrySample = it }
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
    }

    override fun onDestroy() {
        worker.execute { adb.disconnect() }
        worker.shutdown()
        super.onDestroy()
    }
}
