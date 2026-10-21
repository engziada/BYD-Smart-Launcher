package com.ziada.bydsmartlauncher

import java.lang.reflect.Modifier

/**
 * One-shot, READ-ONLY firmware reflection entry point. Invoked under the existing
 * operator-authorized loopback ADB shell via app_process. This is NOT a persistent
 * privileged helper: no Binder service registration, subscriptions, writes, files,
 * changes to Android settings, or vehicle commands.
 *
 * Only a small allowlist of symbolic names is printed. The caller rejects partial
 * or malformed output and never promotes candidate reads to verified telemetry.
 */
object FidReflectionProbe {
    private const val BEGIN = "BYD_FID_PROBE_BEGIN_V1"
    private const val END = "BYD_FID_PROBE_END_V1"

    @JvmStatic
    fun main(args: Array<String>) {
        println(BEGIN)
        try {
            val constants = Class.forName("android.hardware.bydauto.BYDAutoConstants")
            printInt(constants, "BYDAutoConstants", "BYDAUTO_DEVICE_STATISTIC")
            printInt(constants, "BYDAutoConstants", "BYDAUTO_DEVICE_LIGHT")
            printInt(constants, "BYDAutoConstants", "BYDAUTO_DEVICE_CHARGING")
            printInt(constants, "BYDAutoConstants", "BYDAUTO_DEVICE_SPEED")
            printInt(constants, "BYDAutoConstants", "BYDAUTO_DEVICE_GEARBOX")

            val features = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds")
            val byName = features.declaredClasses.associateBy { it.simpleName }
            val statistic = byName["Statistic"]
            if (statistic != null) {
                for (name in listOf(
                    "STATISTIC_ELEC_PERCENTAGE",
                    "STATISTIC_TOTAL_MILEAGE",
                    "STATISTIC_TOTAL_ELEC_CONSUMPTION"
                )) printInt(statistic, "Statistic", name)
            }
            val charging = byName["Charging"]
            if (charging != null) {
                for (name in listOf(
                    "CHARGING_GUN_CONNECT_STATE",
                    "CHARGING_BATTERRY_DEVICE_STATE",
                    "CHARGING_TYPE"
                )) printInt(charging, "Charging", name)
            }
            val speed = byName["Speed"]
            if (speed != null) printInt(speed, "Speed", "SPEED_AUTO_SPEED")
            val gearbox = byName["Gearbox"]
            if (gearbox != null) printInt(gearbox, "Gearbox", "GEARBOX_AUTO_MODE_TYPE")
            val light = byName["Light"]
            if (light != null) printInt(light, "Light", "LIGHT_TURN_SIGNAL_LIGHT")
        } catch (_: Throwable) {
            // Deliberately no exception messages or stacktraces on the ADB channel.
            println("BYD_FID_PROBE_UNAVAILABLE")
        } finally {
            println(END)
            System.out.flush()
        }
    }

    private fun printInt(owner: Class<*>, label: String, name: String) {
        try {
            val field = owner.getDeclaredField(name)
            if (Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
                field.isAccessible = true
                println("$label.$name=${field.getInt(null)}")
            }
        } catch (_: Throwable) {
            // Missing symbols are expected on unsupported firmware. Never guess FIDs.
        }
    }
}
