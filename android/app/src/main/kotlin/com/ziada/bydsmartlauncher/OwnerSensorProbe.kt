package com.ziada.bydsmartlauncher

import android.content.Context
import android.os.Looper
import android.view.Surface
import dalvik.system.PathClassLoader
import java.io.File

/** Fixed, read-only entry point inside the launcher APK, executed by the local ADB shell. */
object OwnerSensorProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        println(OwnerSensorReport.BEGIN)
        val cameraMethods = runCatching {
            val name = "android.hardware.AVMCamera"
            val type = runCatching { Class.forName(name, false, ClassLoader.getSystemClassLoader()) }.getOrElse {
                val jar = "/system/framework/bmmcamera.jar"
                if (!File(jar).isFile) return@getOrElse null
                Class.forName(name, false, PathClassLoader(jar, ClassLoader.getSystemClassLoader()))
            }
            type?.getMethod("setPreviewSurface", Surface::class.java, Int::class.javaPrimitiveType) != null
        }.getOrDefault(false)
        println("avmSurfaceApi=${if (cameraMethods) 1 else 0}")
        runCatching {
            if (Looper.myLooper() == null) Looper.prepareMainLooper()
            val threadType = Class.forName("android.app.ActivityThread")
            val thread = threadType.getMethod("systemMain").invoke(null)
            val context = threadType.getMethod("getSystemContext").invoke(thread) as Context
            readTyres(context)
        }
        println(OwnerSensorReport.END)
        System.out.flush()
        // ActivityThread creates threads in the shell JVM. End this one-shot process.
        kotlin.system.exitProcess(0)
    }

    private fun readTyres(context: Context) {
        // BYDAutoTyreDevice's typed getters and area order were identified in Assistant NG.
        val type = Class.forName("android.hardware.bydauto.tyre.BYDAutoTyreDevice")
        val tyre = type.getMethod("getInstance", Context::class.java).invoke(null, context) ?: return
        if (type.getMethod("hasFeature", String::class.java).invoke(tyre, "TyrePressureMonitor") != 1) return
        val pressure = type.getMethod("getTyrePressureValue", Int::class.javaPrimitiveType)
        val signal = type.getMethod("getTyreSignalState", Int::class.javaPrimitiveType)
        for ((index, name) in OwnerSensorReport.wheels.withIndex()) {
            runCatching {
                val area = index + 1
                val value = pressure.invoke(tyre, area) as? Int
                if (signal.invoke(tyre, area) == 0 && value != null && value in 1..4094) {
                    println("$name=$value")
                }
            }
        }
    }
}

internal object OwnerSensorReport {
    const val BEGIN = "BYD_OWNER_SENSOR_BEGIN_V1"
    const val END = "BYD_OWNER_SENSOR_END_V1"
    val wheels = listOf("frontLeftKpa", "frontRightKpa", "rearLeftKpa", "rearRightKpa")

    fun parse(raw: String?): Map<String, Int>? {
        if (raw == null || raw.length > 4096) return null
        val lines = raw.lineSequence().map(String::trim).toList()
        val start = lines.indexOf(BEGIN)
        val end = lines.indexOf(END)
        if (start < 0 || end <= start || start != lines.lastIndexOf(BEGIN) ||
            end != lines.lastIndexOf(END)) return null
        val readings = mutableMapOf<String, Int>()
        for (line in lines.subList(start + 1, end)) {
            val key = line.substringBefore('=')
            val value = line.substringAfter('=', "").toIntOrNull() ?: return null
            val valid = when (key) {
                "avmSurfaceApi" -> value in 0..1
                in wheels -> value in 1..4094
                else -> false
            }
            if (!valid || readings.put(key, value) != null) return null
        }
        return readings.takeIf { "avmSurfaceApi" in it }
    }
}
