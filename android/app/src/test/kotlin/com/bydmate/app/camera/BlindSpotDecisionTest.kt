package com.bydmate.app.camera

import org.junit.Assert.*
import org.junit.Test

class BlindSpotDecisionTest {
    private fun input(
        blink: Int? = 2,
        speed: Float? = 42f,
        reverse: Boolean = false,
        age: Long = 10,
        oemCamera: Boolean = false,
    ) = BlindSpotInput(
        blink = blink, speedKmh = speed, gearIsReverse = reverse,
        thresholdKmh = 20, telemetryAgeMs = age,
        nativeCameraForeground = oemCamera
    )

    @Test fun leftAndRightOnly() {
        assertEquals(BlindSpotSide.LEFT, decideBlindSpot(input(blink = 2)).show)
        assertEquals(BlindSpotSide.RIGHT, decideBlindSpot(input(blink = 4)).show)
        assertEquals(BlindSpotSide.NONE, decideBlindSpot(input(blink = 6)).show)
        assertEquals(BlindSpotSide.NONE, decideBlindSpot(input(blink = 1)).show)
        assertEquals(BlindSpotSide.NONE, decideBlindSpot(input(blink = null)).show)
    }

    @Test fun stockCameraOverridesOurOverlay() {
        val reverse = decideBlindSpot(input(reverse = true))
        val native = decideBlindSpot(input(oemCamera = true))
        assertEquals(BlindSpotSide.NONE, reverse.show)
        assertFalse(reverse.cameraWarm)
        assertEquals(BlindSpotSide.NONE, native.show)
        assertFalse(native.cameraWarm)
    }

    @Test fun staleOrInvalidTelemetryHidesView() {
        assertEquals(BlindSpotSide.NONE, decideBlindSpot(input(age = 751)).show)
        assertEquals(BlindSpotSide.NONE, decideBlindSpot(input(speed = null)).show)
        assertEquals(BlindSpotSide.NONE, decideBlindSpot(input(speed = 15f)).show)
    }

    @Test fun freshSampleRecoversAfterLoss() {
        val gate = BlindSpotTelemetryGate()
        gate.reset(10_000L)
        assertFalse(gate.onSample(BlindSpotSample(2, 30f, 4), 10_010L).mustClose)
        assertFalse(gate.onSample(null, 10_160L).mustClose)
        assertTrue(gate.onSample(null, 10_310L).mustClose)
        val recovered = gate.onSample(BlindSpotSample(4, 33f, 4), 10_460L)
        assertFalse(recovered.mustClose)
        assertEquals(BlindSpotSide.RIGHT, decideBlindSpot(input(blink = recovered.lastValid?.blink)).show)
    }

    @Test fun clusterAndMainScreenFallback() {
        assertEquals(BlindSpotScreen.MAIN, blindSpotScreen(BlindSpotSide.LEFT, false, false, false))
        assertEquals(BlindSpotScreen.CLUSTER, blindSpotScreen(BlindSpotSide.LEFT, false, false, true))
        assertEquals(BlindSpotScreen.MAIN, blindSpotScreen(BlindSpotSide.RIGHT, false, false, true))
        assertEquals(BlindSpotScreen.MAIN, blindSpotScreen(BlindSpotSide.LEFT, true, true, true))
    }

    @Test fun fastLoopArmHonorsHysteresisAndReverse() {
        assertTrue(blindSpotArmed(true, 4, 15, 20))
        assertFalse(blindSpotArmed(true, 2, 40, 20))
        assertFalse(blindSpotArmed(false, 4, 50, 20))
    }

    @Test fun freezeWatchdogRequiresActualFrames() {
        assertFalse(blindSpotFrameStalled(true, false, 100, 2000))
        assertFalse(blindSpotFrameStalled(false, true, 100, 2000))
        assertTrue(blindSpotFrameStalled(true, true, 100, 2000))
    }
}

class BlindSpotBackoffTest {
    @Test fun heldSignalDoesNotResetBackoffButFreshEdgeDoes() {
        val b = BlindSpotBackoff()
        b.request(BlindSpotSide.LEFT)
        b.fail(1_000, 3_000)
        assertTrue(b.blocked(2_000))
        b.request(BlindSpotSide.LEFT)
        assertTrue(b.blocked(2_100))
        b.request(BlindSpotSide.RIGHT)
        assertFalse(b.blocked(2_100))
    }
}
