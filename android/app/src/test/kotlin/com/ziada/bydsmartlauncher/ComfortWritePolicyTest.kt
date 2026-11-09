package com.ziada.bydsmartlauncher

import org.junit.Assert.*
import org.junit.Test

class ComfortWritePolicyTest {
    private val gear = VehicleAddress(1011, 1)
    private val speed = VehicleAddress(1013, 2)
    private val celsius = VehicleAddress(1000, 3)
    private val controls = ComfortMapping.entries.associateWith { mapping ->
        VerifiedComfortAddress(
            VehicleAddress(mapping.deviceType, mapping.ordinal * 2 + 4),
            VehicleAddress(mapping.deviceType, mapping.ordinal * 2 + 5),
            invertedWindowPercentage = mapping.isWindow,
        )
    }
    private val profile = VerifiedComfortProfile(gear, 1, speed, celsius, controls)

    private inner class FakeIo(private val mapping: ComfortMapping) : ComfortVehicleIo {
        val address = controls.getValue(mapping)
        var state = address.convert(mapping.range.first)
        var parked = 1
        var nextGear = 1
        var readGear = 0
        var speedValue: Float? = 0f
        var unit = 1
        var status: Int? = 1
        var updateState = true
        var now = 100L
        val writes = mutableListOf<Pair<VehicleAddress, Int>>()
        override fun readInt(address: VehicleAddress): Int? = when (address) {
            gear -> if (readGear++ == 0) parked else nextGear
            celsius -> unit
            this.address.readback -> state
            else -> null
        }
        override fun readSpeed(address: VehicleAddress): Float? = speedValue
        override fun writeInt(address: VehicleAddress, value: Int): Int? {
            writes += address to value
            if (updateState) state = value
            return status
        }
        fun execute(value: Int = mapping.range.last, confirmedAt: Long = 100L, settings: VerifiedComfortProfile = profile) =
            ComfortWritePolicy.execute(settings, mapping, value, confirmedAt, { now }, { now += 200 }, this)
    }

    @Test fun allSevenControlsWriteOnceAndConfirmFromIndependentReadback() {
        for (mapping in ComfortMapping.entries) {
            val io = FakeIo(mapping)
            assertEquals(ComfortWriteResult("confirmed", mapping.range.last), io.execute())
            assertEquals(listOf(io.address.write to io.address.convert(mapping.range.last)), io.writes)
        }
    }

    @Test fun blocksUnapprovedRangesFirmwareAndChangedMotionBeforeAnyWrite() {
        val mapping = ComfortMapping.AC_FAN
        assertNull(VerifiedComfortProfiles.forFingerprint("unreviewed/fingerprint"))
        val scenarios = listOf(
            FakeIo(mapping).apply { speedValue = 0.1f } to "park_not_verified",
            FakeIo(mapping).apply { speedValue = null } to "park_not_verified",
            FakeIo(mapping).apply { nextGear = 2 } to "park_not_verified",
            FakeIo(mapping).apply { now = 5000 } to "confirmation_expired",
        )
        for ((io, expected) in scenarios) {
            assertEquals(expected, io.execute().status)
            assertTrue(io.writes.isEmpty())
        }
        val io = FakeIo(mapping)
        assertEquals("invalid_value", io.execute(value = -1).status)
        assertEquals("mapping_not_verified", io.execute(settings = profile.copy(controls = emptyMap())).status)
        assertTrue(io.writes.isEmpty())
    }

    @Test fun neverRepeatsAnUnconfirmedWriteAndRequiresCelsius() {
        val fan = FakeIo(ComfortMapping.AC_FAN).apply { updateState = false }
        assertEquals("sent_unconfirmed", fan.execute().status)
        assertEquals(1, fan.writes.size)
        val ac = FakeIo(ComfortMapping.AC_TEMPERATURE).apply { unit = 2 }
        assertEquals("celsius_required", ac.execute().status)
        assertTrue(ac.writes.isEmpty())
    }

    @Test fun fanPilotRequiresBothStartingAndTargetLevelsInRange() {
        val fan = ComfortMapping.AC_FAN
        val pilot = profile.copy(controls = mapOf(fan to controls.getValue(fan).copy(pilotLevels = 1..2)))
        val io = FakeIo(fan)
        assertEquals("outside_pilot_range", io.execute(value = 2, settings = pilot).status)
        io.state = 1
        assertEquals("outside_pilot_range", io.execute(value = 3, settings = pilot).status)
        assertTrue(io.writes.isEmpty())
        assertEquals(ComfortWriteResult("confirmed", 2), io.execute(value = 2, settings = pilot))
        assertEquals(ComfortWriteResult("confirmed", 1), io.execute(value = 1, settings = pilot))
        assertEquals(listOf(2, 1), io.writes.map { it.second })
    }

    @Test fun acPowerTurnsOffAndRestoresOnThroughVoiceTargets() {
        val mapping = ComfortMapping.AC_POWER
        val io = FakeIo(mapping).apply { state = 1 }
        val off = ComfortVoiceRequest.parse("Turn off the A/C")
        val on = ComfortVoiceRequest.parse("شغل التكييف")
        assertEquals(ComfortVoiceRequest(mapping, 0), off)
        assertEquals(ComfortVoiceRequest(mapping, 1), on)
        assertEquals(ComfortWriteResult("confirmed", 0), io.execute(value = off!!.target!!))
        assertEquals(ComfortWriteResult("confirmed", 1), io.execute(value = on!!.target!!))
        assertEquals(listOf(io.address.write to 0, io.address.write to 1), io.writes)
    }

    @Test fun windowsUsePercentOpenAndWaitForExactEndStops() {
        for (mapping in ComfortMapping.entries.filter { it.isWindow }) {
            val address = controls.getValue(mapping).copy(invertedWindowPercentage = false)
            val percentOpen = profile.copy(controls = mapOf(mapping to address))
            val io = FakeIo(mapping).apply {
                state = 0
                updateState = false
            }
            var polls = 0
            val outcome = ComfortWritePolicy.execute(percentOpen, mapping, 100, 100L, { io.now }, {
                io.now += 200
                io.state = if (++polls == 10) 100 else 99
            }, io)
            assertEquals(ComfortWriteResult("confirmed", 100), outcome)
            assertEquals(listOf(address.write to 100), io.writes)
            assertEquals(10, polls)
        }
    }

    @Test fun validatesHelperFramesAndBilingualCommands() {
        val fan = ComfortMapping.AC_FAN
        assertEquals(ComfortWriteResult("confirmed", 4), ComfortWriteProtocol.parse("BYD_COMFORT_V1 confirmed 4", fan))
        assertEquals("write_outcome_unknown", ComfortWriteProtocol.parse("BYD_COMFORT_V1 confirmed 88", fan).status)
        assertEquals(ComfortVoiceRequest(fan, 3), ComfortVoiceRequest.parse("ظبط سرعة المروحة على ٣"))
        assertEquals(ComfortVoiceRequest(ComfortMapping.FRONT_RIGHT, 100), ComfortVoiceRequest.parse("افتح الشباك اليمين"))
        assertEquals(ComfortVoiceRequest(ComfortMapping.REAR_LEFT), ComfortVoiceRequest.parse("Read rear left window"))
        assertNull(ComfortVoiceRequest.parse("Set fan to -1"))
        assertNull(ComfortVoiceRequest.parse("Set fan to 99"))
        assertNull(ComfortVoiceRequest.parse("Don't close the front right window"))
    }
}
