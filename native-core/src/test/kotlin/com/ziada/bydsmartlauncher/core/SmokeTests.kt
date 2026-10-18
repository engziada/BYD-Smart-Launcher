package com.ziada.bydsmartlauncher.core

import kotlin.math.abs

fun main() {
    fun verify(ok: Boolean, label: String) {
        check(ok) { label }
        println("PASS $label")
    }
    val estimate = RangeEstimator.estimate(RangeInput(53, 60.0, 15.0))
    verify(estimate != null && abs(estimate.rangeKm - 212.0) < 1e-8, "range 53% x 60kWh / 15")
    verify(RangeEstimator.estimate(RangeInput(null, 60.0, 15.0)) == null, "missing SOC unavailable")
    verify(RangeEstimator.estimate(RangeInput(53, 60.0, 0.0)) == null, "zero consumption unavailable")
    verify(RangeEstimator.estimate(RangeInput(101, 60.0, 15.0)) == null, "invalid SOC unavailable")
    verify(RangeEstimator.estimate(RangeInput(53, Double.NaN, 15.0)) == null, "NaN capacity unavailable")
    verify(RangeEstimator.estimate(RangeInput(53, 60.0, 15.0, 4.0)) == null, "carry outside tolerance unavailable")

    val cam = CameraContext(true, 35.0, Gear.DRIVE, Indicator.LEFT, false)
    verify(CameraPolicy.decide(cam) == CameraDecision.SHOW_LEFT, "indicator routes left")
    verify(CameraPolicy.decide(cam.copy(indicator = Indicator.RIGHT)) == CameraDecision.SHOW_RIGHT, "indicator routes right")
    verify(CameraPolicy.decide(cam.copy(gear = Gear.REVERSE)) == CameraDecision.HIDE, "factory reverse takes precedence")
    verify(CameraPolicy.decide(cam.copy(factoryCameraShowing = true)) == CameraDecision.HIDE, "OEM 360 takes precedence")
    verify(CameraPolicy.decide(cam.copy(speedKmh = null)) == CameraDecision.HIDE, "stale speed fails closed")
    verify(CameraPolicy.decide(cam.copy(enabled = false)) == CameraDecision.HIDE, "camera default disabled")

    verify(ControlPolicy.evaluate(ControlContext(false, true, true)) == ControlOutcome.DENIED_UNVERIFIED, "unverified controls denied")
    verify(ControlPolicy.evaluate(ControlContext(true, false, true)) == ControlOutcome.DENIED_MOVING, "moving controls denied")
    verify(ControlPolicy.evaluate(ControlContext(true, true, false)) == ControlOutcome.DENIED_UNSUPPORTED, "non-allowlisted controls denied")
    verify(ControlPolicy.evaluate(ControlContext(true, true, true)) == ControlOutcome.READY_FOR_CONFIRMATION, "verified still needs confirmation")
}
