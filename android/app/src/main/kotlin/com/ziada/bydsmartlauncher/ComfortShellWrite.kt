package com.ziada.bydsmartlauncher

import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock

/** One bounded request from the launcher's approved local ADB shell. */
object ComfortShellWrite {
    @JvmStatic
    fun main(args: Array<String>) {
        val mapping = ComfortMapping.entries.firstOrNull { it.id == args.getOrNull(0) }
        val value = args.getOrNull(1)?.toIntOrNull()
        val confirmedAt = args.getOrNull(2)?.toLongOrNull()
        val profile = VerifiedComfortProfiles.forFingerprint(Build.FINGERPRINT)
        val result = when {
            args.size != 3 || mapping == null || value == null || confirmedAt == null || confirmedAt < 0 ->
                ComfortWriteResult("invalid_value")
            profile == null -> ComfortWriteResult("firmware_not_verified")
            else -> {
                val service = ComfortBinder.connect()
                if (service == null) ComfortWriteResult("autoservice_unavailable")
                else ComfortWritePolicy.execute(
                    profile, mapping, value, confirmedAt, SystemClock::elapsedRealtime,
                    { SystemClock.sleep(200) }, service,
                )
            }
        }
        println("BYD_COMFORT_V1 ${result.status} ${result.readback ?: "-"}")
    }
}

/** Restricted autoservice integer/float GETs and integer SET used by the one-shot helper. */
private class ComfortBinder(private val binder: IBinder, private val descriptor: String) : ComfortVehicleIo {
    override fun readInt(address: VehicleAddress): Int? = call(5, address)?.let(::decodeRead)

    override fun readSpeed(address: VehicleAddress): Float? =
        call(7, address)?.let(::decodeRead)?.let(Float::fromBits)

    override fun writeInt(address: VehicleAddress, value: Int): Int? = call(6, address, value)?.firstOrNull()

    private fun decodeRead(words: IntArray): Int? =
        if (words.size == 2 && words[0] == 0) words[1] else null

    private fun call(code: Int, address: VehicleAddress, value: Int? = null): IntArray? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.writeInt(address.device)
            data.writeInt(address.fid)
            value?.let(data::writeInt)
            if (!binder.transact(code, data, reply, 0) || reply.dataAvail() !in setOf(4, 8)) null
            else IntArray(reply.dataAvail() / Int.SIZE_BYTES) { reply.readInt() }
        } catch (_: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        fun connect(): ComfortBinder? = runCatching {
            val manager = Class.forName("android.os.ServiceManager")
            val binder = manager.getMethod("getService", String::class.java).invoke(null, "autoservice") as? IBinder
                ?: return null
            val descriptor = binder.interfaceDescriptor?.takeIf { it.isNotBlank() } ?: return null
            ComfortBinder(binder, descriptor)
        }.getOrNull()
    }
}

internal object ComfortWriteProtocol {
    private val response = Regex(
        """BYD_COMFORT_V1 (confirmed|already_at_target|invalid_value|mapping_not_verified|confirmation_expired|celsius_required|readback_unavailable|park_not_verified|write_rejected|write_outcome_unknown|sent_unconfirmed|firmware_not_verified|autoservice_unavailable) (-|[0-9]{1,3})"""
    )

    fun parse(raw: String?, mapping: ComfortMapping): ComfortWriteResult {
        if (raw == null || raw.length > 2048) return ComfortWriteResult("write_outcome_unknown")
        val lines = raw.lineSequence().map(String::trim).filter { it.startsWith("BYD_COMFORT_V1") }.toList()
        val match = lines.singleOrNull()?.let(response::matchEntire) ?: return ComfortWriteResult("write_outcome_unknown")
        val status = match.groupValues[1]
        val readback = match.groupValues[2].toIntOrNull()?.takeIf { it in mapping.range }
        if (readback == null && status in setOf("confirmed", "already_at_target")) {
            return ComfortWriteResult("write_outcome_unknown")
        }
        return ComfortWriteResult(status, readback)
    }
}
