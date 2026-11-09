package com.ziada.bydsmartlauncher

import com.ziada.bydsmartlauncher.automation.VerifiedChargeState

/** Fixed autoservice GETs for the Song Plus firmware profile. Never accepts shell input. */
internal enum class BetaSignal(val device: Int, val fid: Int, val floating: Boolean = false) {
    POWER(1001, 315621418),
    GUN(1009, 876609586),
    BMS(1009, 876609560),
    GEAR(1011, 555745336),
    SPEED(1013, -1807745016, true),
    TURN(1004, 950009900),
    SOC(1014, 1246777400, true),
    ODOMETER(1014, 1246765072),
    ENERGY(1014, 1032871984, true),
    REMAINING(1005, 882901008, true),
    ENGINE_RPM(1012, 339738642);

    private val read get() = "service call autoservice ${if (floating) 7 else 5} i32 $device i32 $fid"

    companion object {
        private const val DELIMITER = "BYD_BETA_READ_END"
        val monitor = entries.filterNot { it == SPEED || it == TURN }
        val camera = listOf(GEAR, SPEED, TURN)

        fun command(signals: List<BetaSignal>): String =
            signals.joinToString("; ") { "${it.read}; echo $DELIMITER" }

        fun parse(signals: List<BetaSignal>, output: String?): Map<BetaSignal, Double?> {
            if (output == null || output.length > 16_384) return emptyMap()
            val parts = output.split(DELIMITER)
            if (parts.size != signals.size + 1 || parts.last().isNotBlank()) return emptyMap()
            return signals.zip(parts).associate { (signal, part) ->
                signal to if (signal.floating) CandidateTelemetry.float(part) else CandidateTelemetry.int(part)?.toDouble()
            }
        }
    }
}

internal enum class SidePreview(val index: Int) { LEFT(2), RIGHT(3) }

internal fun sidePreview(gear: Double?, speed: Double?, turn: Double?): SidePreview? {
    // Use the exact DiLink gear and turn enums. Keep the factory low-speed
    // manoeuvring view in control, even before it takes launcher focus.
    if (gear != 4.0 || speed == null || speed !in 30.0..180.0) return null
    return when (turn) {
        2.0 -> SidePreview.LEFT
        4.0 -> SidePreview.RIGHT
        else -> null
    }
}

internal fun chargeState(gun: Double?, bms: Double?): VerifiedChargeState? = when {
    gun == 1.0 && bms != null && bms != 1.0 && bms in 0.0..31.0 -> VerifiedChargeState.IDLE
    gun in listOf(2.0, 3.0, 4.0) && bms == 1.0 -> VerifiedChargeState.CHARGING
    gun in listOf(2.0, 3.0, 4.0) && bms == 2.0 -> VerifiedChargeState.FINISHED
    else -> null
}

/** First and transient samples never promote a state change. */
internal class RepeatedSignal<T> {
    private var previous: T? = null
    private var stable: T? = null

    fun observe(value: T?): T? {
        if (value != null && value == previous) stable = value
        previous = value
        return stable
    }

    fun reset() {
        previous = null
        stable = null
    }
}
