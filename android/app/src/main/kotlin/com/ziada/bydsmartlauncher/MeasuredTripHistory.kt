package com.ziada.bydsmartlauncher

import android.content.Context
import org.json.JSONArray

internal data class MeasuredTrip(val km: Double, val drivingKwh: Double) {
    val valid: Boolean
        get() = km in 1.0..2_000.0 && drivingKwh in 0.01..400.0 &&
            (100.0 * drivingKwh / km) in 1.0..150.0
}

/** User-entered, EV-only trip energy from an independent meter or the OEM trip display. */
internal class MeasuredTripHistory(context: Context) {
    private val prefs = context.getSharedPreferences("measured_trips", Context.MODE_PRIVATE)

    fun status(): Map<String, Any?> = summarize(load())

    fun record(distanceKm: Double?, drivingKwh: Double?, eligible: Boolean): Map<String, Any?> {
        if (!eligible) return mapOf("ok" to false, "error" to "ev_driving_without_charging_required")
        val trip = MeasuredTrip(distanceKm ?: Double.NaN, drivingKwh ?: Double.NaN)
        if (!trip.valid) return mapOf("ok" to false, "error" to "invalid_trip_measurements")
        val trips = (load() + trip).takeLast(LIMIT)
        val json = JSONArray()
        trips.forEach { json.put(JSONArray().put(it.km).put(it.drivingKwh)) }
        if (!prefs.edit().putString("trips", json.toString()).commit()) {
            return mapOf("ok" to false, "error" to "storage_error")
        }
        return summarize(trips)
    }

    fun clear(): Map<String, Any?> = if (prefs.edit().remove("trips").commit()) status()
        else mapOf("ok" to false, "error" to "storage_error")

    private fun load(): List<MeasuredTrip> = runCatching {
        val raw = JSONArray(prefs.getString("trips", "[]"))
        (0 until raw.length().coerceAtMost(LIMIT)).mapNotNull { i ->
            val row = raw.optJSONArray(i) ?: return@mapNotNull null
            MeasuredTrip(row.optDouble(0), row.optDouble(1)).takeIf { it.valid }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val LIMIT = 30

        fun summarize(trips: List<MeasuredTrip>): Map<String, Any?> {
            val distance = trips.sumOf { it.km }
            return mapOf(
                "ok" to true,
                "tripCount" to trips.size,
                "distanceKm" to distance,
                "consumption" to if (distance > 0) 100.0 * trips.sumOf { it.drivingKwh } / distance else null,
            )
        }
    }
}
