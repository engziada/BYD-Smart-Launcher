package com.ziada.bydsmartlauncher.core

/** Safety policy only: does not interface with cameras or car hardware. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }
enum class Indicator { OFF, LEFT, RIGHT, HAZARD, UNKNOWN }
enum class CameraDecision { HIDE, SHOW_LEFT, SHOW_RIGHT }

data class CameraContext(
    val enabled: Boolean,
    val speedKmh: Double?,
    val gear: Gear,
    val indicator: Indicator,
    val factoryCameraShowing: Boolean,
    val thresholdKmh: Double = 20.0
)

object CameraPolicy {
    fun decide(state: CameraContext): CameraDecision {
        if (!state.enabled || state.factoryCameraShowing) return CameraDecision.HIDE
        if (state.gear != Gear.DRIVE && state.gear != Gear.NEUTRAL) return CameraDecision.HIDE
        val speed = state.speedKmh
        if (speed == null || !speed.isFinite() || speed < state.thresholdKmh) return CameraDecision.HIDE
        return when (state.indicator) {
            Indicator.LEFT -> CameraDecision.SHOW_LEFT
            Indicator.RIGHT -> CameraDecision.SHOW_RIGHT
            else -> CameraDecision.HIDE
        }
    }
}

enum class ControlOutcome { DENIED_UNVERIFIED, DENIED_MOVING, DENIED_UNSUPPORTED, READY_FOR_CONFIRMATION }
data class ControlContext(
    val capabilitiesVerified: Boolean,
    val parkedConfirmed: Boolean,
    val allowlisted: Boolean
)

object ControlPolicy {
    fun evaluate(c: ControlContext): ControlOutcome = when {
        !c.capabilitiesVerified -> ControlOutcome.DENIED_UNVERIFIED
        !c.parkedConfirmed -> ControlOutcome.DENIED_MOVING
        !c.allowlisted -> ControlOutcome.DENIED_UNSUPPORTED
        else -> ControlOutcome.READY_FOR_CONFIRMATION
    }
}
