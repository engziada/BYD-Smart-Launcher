package com.ziada.bydsmartlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.ziada.bydsmartlauncher.automation.LocalAutomationRepository
import com.ziada.bydsmartlauncher.automation.VehicleAlertRouter
import com.ziada.bydsmartlauncher.automation.VerifiedChargeState
import com.ziada.bydsmartlauncher.automation.VerifiedSignal
import java.security.MessageDigest
import java.util.UUID
import com.ziada.bydsmartlauncher.automation.AutomationRule
import com.ziada.bydsmartlauncher.automation.TriggerKind
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Read-only monitoring after the operator connects. Credentials stay in the existing outbox. */
class VehicleBetaService : Service() {
    private val adb by lazy { SafeAdbTransport.shared(this) }
    private val telegram by lazy { TelegramBotService.shared(this) }
    private val rules by lazy { LocalAutomationRepository(this) }
    private val prefs by lazy { getSharedPreferences("beta_range", MODE_PRIVATE) }
    private val eventPrefs by lazy { getSharedPreferences(EVENT_PREFS, Context.MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val power = RepeatedSignal<Boolean>()
    private val charge = RepeatedSignal<VerifiedChargeState>()
    private val range = ConsumptionWindow()
    private val router = VehicleAlertRouter()
    private var powerSession: String? = null
    private var lastPower: Boolean? = null
    private var lastGoodRead = 0L
    private var lastRateSavedAt = 0L

    @Volatile private var stopped = false
    @Volatile private var reconnectAuthorized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = requireNotNull(getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Vehicle monitoring", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("BYD Smart Launcher")
            .setContentText("Reading vehicle data for range and enabled alerts")
            .setOngoing(true)
            .build())
        worker.scheduleWithFixedDelay({
            runCatching(::tick).onFailure { clearInputs() }
        }, 0L, 750L, TimeUnit.MILLISECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("restore_authorized_connection", false) == true) reconnectAuthorized = true
        return START_NOT_STICKY
    }

    private fun clearInputs() {
        latest = emptyMap()
        power.reset()
        charge.reset()
        range.reset()
        router.reset()
        lastPower = null
        powerSession = null
    }

    private fun tick() {
        if (stopped) return
        if (reconnectAuthorized) {
            reconnectAuthorized = false
            if (adb.betaProfileMatched && !adb.hasConnectedSession()) adb.connect()
        }
        val startedAt = SystemClock.elapsedRealtime()
        val sample = adb.sampleBetaSignals(BetaSignal.monitor)
        if (stopped) return
        val elapsed = SystemClock.elapsedRealtime()
        if (sample.values.none { it != null } || elapsed - startedAt > 3_000L) {
            clearInputs()
            return
        }
        if (elapsed - lastGoodRead > 8_000L) clearInputs()
        lastGoodRead = elapsed
        val now = System.currentTimeMillis()
        val soc = sample[BetaSignal.SOC]?.takeIf { it in 0.0..100.0 }
        val powerOn = power.observe(when (sample[BetaSignal.POWER]) {
            0.0 -> false
            2.0 -> true
            else -> null
        })
        val chargeState = charge.observe(chargeState(sample[BetaSignal.GUN], sample[BetaSignal.BMS]))
        if (powerOn == true && lastPower != true) powerSession = UUID.randomUUID().toString()
        lastPower = powerOn
        val enabledRules = rules.enabledRules()
        if (powerOn != null && rules.masterEnabled()) recordPower(powerOn, now, enabledRules)
        router.observe(VerifiedSignal(now, true, soc, tripId = powerSession, chargeState = chargeState),
            now, enabledRules, telegram::queueVerifiedVehicleEvent)

        val odometer = sample[BetaSignal.ODOMETER]?.takeIf { it in 0.0..10_000_000.0 }?.div(10.0)
        val consumed = sample[BetaSignal.ENERGY]?.takeIf { it in 0.0..10_000_000.0 }
        val drivingElectric = sample[BetaSignal.GEAR] == 4.0 && sample[BetaSignal.GUN] == 1.0 &&
            sample[BetaSignal.ENGINE_RPM] == 0.0 && sample[BetaSignal.BMS] != 1.0
        if (drivingElectric && odometer != null && consumed != null && soc != null) {
            val rate = range.add(CandidateSample(soc, consumed, odometer, elapsed))
            if (rate != null && elapsed - lastRateSavedAt >= 15_000L) {
                prefs.edit().putFloat("rate", rate.toFloat()).putLong("rate_at", now).apply()
                lastRateSavedAt = elapsed
            }
        } else {
            range.reset()
        }
        val rate = range.consumption() ?: prefs.getFloat("rate", Float.NaN).toDouble().takeIf {
            it in 4.0..65.0 && now - prefs.getLong("rate_at", 0L) in 0L..604_800_000L
        }
        val capacity = adb.capacityStatus()["usableCapacityKwh"] as? Double
        val remaining = sample[BetaSignal.REMAINING]?.takeIf { energy ->
            energy in 0.0..200.0 && soc != null &&
                if (soc == 0.0) energy == 0.0 else energy / (soc / 100.0) in 5.0..200.0
        }
        val availableEnergy = remaining ?: if (capacity != null && soc != null) capacity * soc / 100.0 else null
        if (stopped) return
        latest = mapOf(
            "active" to true, "capturedAt" to elapsed, "powerOn" to powerOn,
            "chargeState" to chargeState?.name, "consumption" to rate,
            "remainingKwh" to availableEnergy, "bmsEnergy" to (remaining != null),
            "rangeKm" to if (rate != null && availableEnergy != null) {
                (100.0 * availableEnergy / rate).takeIf { it in 0.0..2000.0 }
            } else null,
        )
    }

    private fun recordPower(on: Boolean, now: Long, enabledRules: List<AutomationRule>) {
        val previousAt = eventPrefs.getLong("power_at", 0L)
        val previous = eventPrefs.getBoolean("power_on", on)
        val age = now - previousAt
        if (previous == on && age in 0L..60_000L) return
        val kind = if (on) TriggerKind.POWER_ON else TriggerKind.POWER_OFF
        if (previous != on && age in 1L..604_800_000L && enabledRules.any { it.kind == kind }) {
            val event = "power:$previousAt:${kind.name}"
            val id = MessageDigest.getInstance("SHA-256").digest(event.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            telegram.queueVerifiedVehicleEvent(id, kind.name, now)
        }
        eventPrefs.edit().putLong("power_at", now).putBoolean("power_on", on).commit()
    }

    override fun onDestroy() {
        stopped = true
        latest = emptyMap()
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val EVENT_PREFS = "beta_vehicle_events"
        private const val CHANNEL = "byd_vehicle_beta"
        private const val NOTIFICATION = 4150
        @Volatile private var latest: Map<String, Any?> = emptyMap()

        internal fun status(): Map<String, Any?> {
            val data = latest
            val time = data["capturedAt"] as? Long ?: return emptyMap()
            return if (SystemClock.elapsedRealtime() - time in 0L..8_000L) data - "capturedAt" else emptyMap()
        }

        internal fun start(context: Context, afterBoot: Boolean = false) {
            if (!afterBoot) context.getSharedPreferences(EVENT_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("authorized_connection", true).apply()
            runCatching {
                context.startForegroundService(Intent(context, VehicleBetaService::class.java)
                    .putExtra("restore_authorized_connection", afterBoot))
            }
        }

        internal fun restoreAfterBoot(context: Context) {
            val authorized = context.getSharedPreferences(EVENT_PREFS, Context.MODE_PRIVATE)
                .getBoolean("authorized_connection", false)
            if (authorized && LocalAutomationRepository(context).masterEnabled() &&
                SafeAdbTransport.shared(context).betaProfileMatched) start(context, afterBoot = true)
        }

        internal fun clearPowerBaseline(context: Context) {
            context.getSharedPreferences(EVENT_PREFS, Context.MODE_PRIVATE).edit()
                .remove("power_at").remove("power_on").commit()
        }

        internal fun stop(context: Context) {
            context.getSharedPreferences(EVENT_PREFS, Context.MODE_PRIVATE)
                .edit().remove("authorized_connection").apply()
            clearPowerBaseline(context)
            context.stopService(Intent(context, VehicleBetaService::class.java))
        }
    }
}


class VehicleBetaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) VehicleBetaService.restoreAfterBoot(context)
    }
}
