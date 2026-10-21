package com.ziada.bydsmartlauncher

import android.os.Handler
import io.flutter.plugin.common.EventChannel
import java.util.concurrent.ExecutorService

/**
 * P2 read-only native candidate stream. No foreground service, persistent
 * helper, permission changes or vehicle writes. Runs only after an explicit
 * Flutter start AND with an active event listener.
 *
 * All lifecycle/sink access happens on Android main thread. ADB commands run
 * serialized in the existing background worker, and generation checks prevent
 * any late I/O response from reactivating a stopped or backgrounded UI.
 */
internal class NativeReadOnlyStream(
    private val adb: SafeAdbTransport,
    private val worker: ExecutorService,
    private val main: Handler,
    private val onAcceptedSample: (Map<String, Any?>) -> Unit
) {
    companion object { const val SAMPLE_INTERVAL_MS = 5_000L }

    private val policy = CandidateStreamPolicy()
    private var sink: EventChannel.EventSink? = null
    private var generation = 0L
    private var running = false
    private var pending: Runnable? = null
    private var current = status("idle", "explicit_start_required")

    private fun status(state: String, reason: String) = mapOf<String, Any?>(
        "streamState" to state,
        "reason" to reason,
        "firmwareVerified" to false,
        "vehicleWriteAuthorized" to false,
        "automaticEventEligible" to false
    )

    fun listen(newSink: EventChannel.EventSink) {
        if (sink != null) stop("listener_replaced")
        sink = newSink
        publish(current)
    }

    fun cancel() {
        stop("listener_detached")
        sink = null
    }

    fun currentStatus(): Map<String, Any?> = current

    fun start(): Map<String, Any?> {
        if (running) return status("initializing", "already_started")
        if (sink == null) return mapOf(
            "ok" to false, "reason" to "no_active_stream_listener"
        )
        if (adb.transportState != "connected") return mapOf(
            "ok" to false, "reason" to "adb_not_connected"
        )

        val token = ++generation
        running = true
        policy.reset()
        publish(status("initializing", "reflecting_firmware_fids"))
        worker.execute {
            val prepared = try {
                adb.prepareNativeCandidateStream()
            } catch (_: Exception) {
                mapOf("ok" to false, "reason" to "catalog_setup_failed")
            }
            main.post {
                if (token != generation || !running) return@post
                if (prepared["ok"] != true) {
                    endWithError(prepared["reason"] as? String ?: "catalog_setup_failed")
                } else {
                    sample(token)
                }
            }
        }
        return mapOf(
            "ok" to true, "streamState" to "initializing",
            "firmwareVerified" to false, "vehicleWriteAuthorized" to false
        )
    }

    private fun sample(token: Long) {
        if (token != generation || !running || sink == null) return
        worker.execute {
            val raw = try { adb.nativeCandidateTick() }
                catch (_: Exception) { mapOf("ok" to false, "reason" to "read_failed") }
            main.post {
                if (token != generation || !running || sink == null) return@post
                val verdict = policy.inspect(raw, System.currentTimeMillis())
                publish(verdict)
                if (verdict["streamState"] == "candidate") {
                    onAcceptedSample(verdict)
                }
                if (verdict["streamState"] == "stopped") {
                    running = false
                    adb.stopNativeCandidateStream()
                } else {
                    val next = Runnable { sample(token) }
                    pending = next
                    main.postDelayed(next, SAMPLE_INTERVAL_MS)
                }
            }
        }
    }

    private fun endWithError(reason: String) {
        ++generation
        running = false
        pending?.let(main::removeCallbacks)
        pending = null
        adb.stopNativeCandidateStream()
        publish(status("stopped", reason))
    }

    /** Always called on main thread; safe from lifecycle callbacks. */
    fun stop(reason: String = "operator_stopped"): Map<String, Any?> {
        ++generation
        running = false
        pending?.let(main::removeCallbacks)
        pending = null
        adb.stopNativeCandidateStream()
        policy.reset()
        publish(status("stopped", reason))
        return mapOf(
            "ok" to true, "streamState" to "stopped",
            "reason" to reason,
            "vehicleWriteAuthorized" to false
        )
    }

    private fun publish(event: Map<String, Any?>) {
        current = event
        try { sink?.success(event) }
        catch (_: RuntimeException) { /* A detached view must not keep the stream alive. */ }
    }
}
