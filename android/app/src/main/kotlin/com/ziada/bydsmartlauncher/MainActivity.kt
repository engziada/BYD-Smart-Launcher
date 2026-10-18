package com.ziada.bydsmartlauncher

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * Read-only Flutter bridge. Stub until BYDMate v4.0.1 native transport is ported, licensed,
 * and validated on the specific BYD firmware. Never claim live telemetry from this stub.
 */
class MainActivity : FlutterActivity() {
    private val gateway = UnavailableVehicleGateway()
    private val methods = "com.ziada.byd_smart_launcher/vehicle"
    private val events = "com.ziada.byd_smart_launcher/telemetry"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methods).setMethodCallHandler { call, result ->
            when (call.method) {
                "getSnapshot" -> result.success(gateway.snapshot())
                "getDiagnostics" -> result.success(gateway.diagnostics())
                "getCapabilities" -> result.success(gateway.capabilities())
                else -> result.notImplemented()
            }
        }
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, events).setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                sink.success(gateway.snapshot())
            }
            override fun onCancel(arguments: Any?) {}
        })
    }
}

interface VehicleGateway {
    fun snapshot(): Map<String, Any?>
    fun diagnostics(): Map<String, Any?>
    fun capabilities(): Map<String, Any?>
}

class UnavailableVehicleGateway : VehicleGateway {
    override fun snapshot(): Map<String, Any?> = mapOf(
        "connected" to false,
        "adbState" to "not_configured",
        "helperState" to "unavailable",
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
    override fun diagnostics(): Map<String, Any?> = mapOf(
        "adb" to "not_configured", "car" to "not_connected", "writeEnabled" to false
    )
    override fun capabilities(): Map<String, Any?> = mapOf(
        "blindSpot" to false, "telegram" to false, "automations" to false,
        "controls" to false, "realRange" to false, "adb" to false
    )
}
