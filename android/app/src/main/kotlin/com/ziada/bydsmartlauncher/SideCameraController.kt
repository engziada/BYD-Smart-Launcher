package com.ziada.bydsmartlauncher

import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Preview exists only inside the focused launcher Activity. It never covers another app. */
internal class SideCameraController(
    private val activity: Activity,
    private val adb: SafeAdbTransport,
    private val io: ExecutorService,
    private val main: Handler,
) {
    private val prefs = activity.getSharedPreferences("side_camera", Context.MODE_PRIVATE)
    private val cameraWorker = Executors.newSingleThreadExecutor()
    private var focused = false
    private var demo = false
    private var polling = false
    private var generation = 0L
    private var readingAt = 0L
    private var retryAfter = 0L
    private var preview: Preview? = null
    private var state = "idle"
    private val timer = Runnable { tick() }

    private val enabled get() = prefs.getBoolean("enabled", false)
    private val active get() = focused && enabled && !demo && adb.betaProfileMatched &&
        activity.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun now() = SystemClock.elapsedRealtime()

    fun status(): Map<String, Any?> = mapOf(
        "enabled" to enabled, "supportedFirmware" to adb.betaProfileMatched,
        "state" to state,
    )

    fun setEnabled(value: Boolean): Map<String, Any?> {
        prefs.edit().putBoolean("enabled", value).apply()
        restart()
        return status()
    }

    fun setFocused(value: Boolean) {
        if (focused == value) return
        focused = value
        restart()
    }

    fun setDemo(value: Boolean) {
        demo = value
        restart()
    }

    private fun restart() {
        generation++
        readingAt = 0L
        main.removeCallbacks(timer)
        dismiss()
        state = if (active) "waiting_for_signal" else "idle"
        if (active) main.post(timer)
    }

    private fun tick() {
        if (!active) return
        val elapsed = now()
        val shown = preview
        if (shown != null) {
            if (elapsed - readingAt > 750L) {
                dismiss()
            } else if (elapsed - shown.lastFrameAt > 2_500L) {
                state = "no_camera_frames"
                retryAfter = elapsed + 5_000L
                dismiss()
            }
        }
        if (!polling) {
            polling = true
            val token = generation
            io.execute {
                val readStart = now()
                val values = runCatching { adb.sampleBetaSignals(BetaSignal.camera) }.getOrDefault(emptyMap())
                val readEnd = now()
                main.post {
                    polling = false
                    if (token != generation || !active) return@post
                    val requested = if (readEnd - readStart <= 500L && now() - readEnd <= 250L) {
                        sidePreview(values[BetaSignal.GEAR], values[BetaSignal.SPEED], values[BetaSignal.TURN])
                    } else null
                    if (requested == null) {
                        dismiss()
                        state = if (values.isEmpty()) "waiting_for_vehicle_data" else "waiting_for_signal"
                    } else {
                        readingAt = readEnd
                        if (preview?.side != requested) {
                            dismiss()
                            if (now() >= retryAfter) show(requested)
                        }
                    }
                }
            }
        }
        main.postDelayed(timer, 180L)
    }

    private fun show(side: SidePreview) {
        val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val item = Preview(side)
        preview = item
        state = "opening"
        val width = (host.width * 0.30f).toInt()
        if (width <= 0) {
            preview = null
            return
        }
        val position = if (side == SidePreview.LEFT) Gravity.START else Gravity.END
        val bounds = FrameLayout.LayoutParams(width, width * 9 / 16, Gravity.CENTER_VERTICAL or position)
        val margin = (16 * activity.resources.displayMetrics.density).toInt()
        bounds.setMargins(margin, 0, margin, 0)
        host.addView(item.view, bounds)
    }

    private fun dismiss() {
        val item = preview ?: return
        preview = null
        item.active = false
        item.view.alpha = 0f
        (item.view.parent as? FrameLayout)?.removeView(item.view)
        item.dispose()
    }

    fun close() {
        focused = false
        restart()
        cameraWorker.shutdown()
    }

    private inner class Preview(val side: SidePreview) : TextureView.SurfaceTextureListener {
        val view = TextureView(activity).apply {
            alpha = 0.01f
            isClickable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            surfaceTextureListener = this@Preview
        }
        @Volatile var active = true
        var lastFrameAt = now()
        private var camera: AvmSideCamera? = null
        private var surface: Surface? = null
        private var ownedTexture: SurfaceTexture? = null

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            ownedTexture = texture
            cameraWorker.execute {
                if (!active) return@execute
                val output = Surface(texture).also { surface = it }
                camera = AvmSideCamera.open(output, side.index)
                if (camera == null) main.post {
                    if (preview === this) {
                        state = "camera_unavailable"
                        retryAfter = now() + 5_000L
                        dismiss()
                    }
                }
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            if (preview !== this || !active) return
            lastFrameAt = now()
            view.alpha = 1f
            state = "showing_${side.name.lowercase()}"
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            dispose()
            return false
        }

        fun dispose() {
            active = false
            val texture = ownedTexture ?: return
            ownedTexture = null
            cameraWorker.execute {
                camera?.close()
                surface?.release()
                texture.release()
            }
        }
    }
}

/** Adapter to the vehicle's AVM class, with no cluster, global overlay or camera mode changes. */
private class AvmSideCamera(private val handle: Any, private val surface: Surface, private val index: Int) {
    private val type = handle.javaClass

    fun close() {
        runCatching { type.getMethod("rmPreviewSurface", Surface::class.java, Integer.TYPE).invoke(handle, surface, index) }
        runCatching { type.getMethod("stopPreview").invoke(handle) }
        runCatching { type.getMethod("close").invoke(handle) }
    }

    companion object {
        private const val INFO = "android.hardware.BmmCameraInfo"
        private const val CAMERA = "android.hardware.AVMCamera"
        private val loader by lazy {
            val parent = AvmSideCamera::class.java.classLoader
            if (runCatching { Class.forName(INFO, false, parent) }.isSuccess) parent
            else File("/system/framework/bmmcamera.jar").takeIf(File::isFile)?.let {
                PathClassLoader(it.path, parent)
            }
        }

        fun open(surface: Surface, index: Int): AvmSideCamera? {
            var opened: AvmSideCamera? = null
            return runCatching {
                val vendorLoader = loader ?: return null
                val info = Class.forName(INFO, true, vendorLoader)
                val find = info.getMethod("getCameraId", String::class.java)
                val id = sequenceOf("pano_h", "pano_l", "apa", "byd_apa").mapNotNull { tag ->
                    runCatching { (find.invoke(null, tag) as Number).toInt() }.getOrNull()
                }.firstOrNull { it >= 0 } ?: return null
                val type = Class.forName(CAMERA, true, vendorLoader)
                val device = type.getMethod("open", Integer.TYPE).invoke(null, id) ?: return null
                opened = AvmSideCamera(device, surface, index)
                val callbackType = Class.forName("$CAMERA\$IEventCallback", false, vendorLoader)
                val callback = Proxy.newProxyInstance(vendorLoader, arrayOf(callbackType)) { proxy, method, arguments ->
                    when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === arguments?.firstOrNull()
                        "toString" -> "BYDSidePreviewCallback"
                        else -> null
                    }
                }
                type.getMethod("setEventCallback", callbackType).invoke(device, callback)
                require(accepted(type.getMethod("addPreviewSurface", Surface::class.java, Integer.TYPE).invoke(device, surface, index)))
                require(accepted(type.getMethod("setPreviewSurface", Surface::class.java, Integer.TYPE).invoke(device, surface, index)))
                require(accepted(type.getMethod("startPreview").invoke(device)))
                opened
            }.getOrElse {
                opened?.close()
                null
            }
        }

        private fun accepted(result: Any?): Boolean = result != false && (result !is Number || result.toInt() >= 0)
    }
}
