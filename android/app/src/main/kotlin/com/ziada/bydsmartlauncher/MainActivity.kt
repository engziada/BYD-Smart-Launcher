package com.ziada.bydsmartlauncher

import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/**
 * Native bridge: ADB handshake and limited read-only Android-version probe. Still NO BYD
 * autoservice connection, no camera pipeline, and no vehicle control.
 */
class MainActivity : FlutterActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adb by lazy { SafeAdbTransport(applicationContext) }
    private val methods = "com.ziada.byd_smart_launcher/vehicle"
    private val events = "com.ziada.byd_smart_launcher/telemetry"

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
                    "getCapabilities" -> result.success(mapOf(
                        "adb" to (adb.transportState == "connected"),
                        "blindSpot" to false, "telegram" to false,
                        "automations" to false, "realRange" to false, "controls" to false
                    ))
                    "adbConnect", "adbDisconnect", "adbProbe", "adbProbeSoc" -> {
                        worker.execute {
                            val response = when (call.method) {
                                "adbConnect" -> adb.connect()
                                "adbDisconnect" -> adb.disconnect()
                                "adbProbeSoc" -> adb.probeSoc()
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
