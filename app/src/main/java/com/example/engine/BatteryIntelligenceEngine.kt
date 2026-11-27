package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.example.data.local.BatteryHistoryEntity
import com.example.data.local.RiderIntelligenceRepository
import com.example.domain.model.BatteryIntelligenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryIntelligenceEngine(
    private val context: Context,
    private val repository: RiderIntelligenceRepository,
    private val scope: CoroutineScope
) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _state = MutableStateFlow(BatteryIntelligenceState())
    val state: StateFlow<BatteryIntelligenceState> = _state.asStateFlow()

    private var monitorJob: Job? = null
    private var lastPersistedMs: Long = 0L
    private var lastPersistedPct: Int = -1
    private val recentSamples = ArrayDeque<Pair<Long, Int>>(40) // (timestampMs, pct)

    fun startMonitoring() {
        refreshSnapshot(persistHistory = false, activeSessionId = null)
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(15_000L)
                refreshSnapshot(persistHistory = false, activeSessionId = null)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun refreshSnapshot(
        persistHistory: Boolean = false,
        activeSessionId: Long? = null
    ): BatteryIntelligenceState {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPct = if (rawLevel >= 0 && scale > 0) {
            (rawLevel * 100) / scale
        } else {
            runCatching { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
                .getOrDefault(-1)
                .let { if (it in 0..100) it else -1 }
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Pad"
            else -> if (isCharging) "Charging" else "Discharging (On Battery)"
        }

        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = if (rawTemp != Int.MIN_VALUE && rawTemp > 0) rawTemp / 10f else null

        val rawVoltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltageMv = if (rawVoltage > 0) rawVoltage else null

        val rawCurrent = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(Int.MIN_VALUE)
        val currentSupported = rawCurrent != Int.MIN_VALUE && rawCurrent != 0
        val currentMicroAmps = if (currentSupported) rawCurrent else null

        val isPowerSave = runCatching { powerManager.isPowerSaveMode }.getOrDefault(false)
        val isIgnoringOpt = runCatching {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

        val now = System.currentTimeMillis()
        if (levelPct in 0..100) {
            if (isCharging) {
                recentSamples.clear()
            } else {
                val last = recentSamples.lastOrNull()
                if (last == null || now - last.first >= 30_000L || last.second != levelPct) {
                    if (recentSamples.size >= 40) recentSamples.removeFirst()
                    recentSamples.addLast(now to levelPct)
                }
            }
        }

        val (drainRatePerHour, estimatedMinutes, estimationLabel) = estimateDrainAndRuntime(
            levelPct = levelPct,
            isCharging = isCharging,
            samples = recentSamples.toList()
        )

        val lowBatteryAlert = when {
            isCharging -> null
            levelPct in 0..10 -> "Critical battery ($levelPct%). Connect power bank immediately."
            levelPct in 11..20 -> "Low battery ($levelPct%). Rider GPS and LTE radio may be throttled soon."
            else -> null
        }

        val thermalAlert = when {
            tempC != null && tempC >= 45.0f ->
                "Critical battery temperature (${String.format("%.1f", tempC)}°C). Remove from heat/sunlight."
            tempC != null && tempC >= 41.0f ->
                "Warm battery (${String.format("%.1f", tempC)}°C). Fast charging + GPS in sunlight increases heat."
            else -> null
        }

        val next = BatteryIntelligenceState(
            levelPercent = levelPct,
            temperatureC = tempC,
            isCharging = isCharging,
            chargingSource = chargingSource,
            voltageMv = voltageMv,
            currentMicroAmps = currentMicroAmps,
            currentSupported = currentSupported,
            isPowerSaveMode = isPowerSave,
            isBatteryOptimizationIgnored = isIgnoringOpt,
            observedDrainPerHourPct = drainRatePerHour,
            estimatedRemainingMinutes = estimatedMinutes,
            estimationLabel = estimationLabel,
            lowBatteryAlert = lowBatteryAlert,
            thermalAlert = thermalAlert,
            lastUpdatedMs = now
        )
        _state.value = next

        if (persistHistory && levelPct in 0..100) {
            val shouldSave = (now - lastPersistedMs >= 60_000L) || abs(levelPct - lastPersistedPct) >= 1
            if (shouldSave) {
                lastPersistedMs = now
                lastPersistedPct = levelPct
                scope.launch {
                    repository.insertBatterySnapshot(
                        BatteryHistoryEntity(
                            sessionId = activeSessionId,
                            timestampMs = now,
                            levelPercent = levelPct,
                            temperatureC = tempC,
                            isCharging = isCharging,
                            chargingSource = chargingSource,
                            voltageMv = voltageMv,
                            currentMicroAmps = currentMicroAmps,
                            isPowerSave = isPowerSave
                        )
                    )
                }
            }
        }

        return next
    }

    companion object {
        fun estimateDrainAndRuntime(
            levelPct: Int,
            isCharging: Boolean,
            samples: List<Pair<Long, Int>>
        ): Triple<Float?, Int?, String> {
            if (isCharging) {
                return Triple(null, null, "Charging — runtime estimate paused")
            }
            if (levelPct < 0) {
                return Triple(null, null, "Battery level unavailable")
            }
            if (samples.size < 2) {
                return Triple(null, null, "Collecting drain samples (~5 min needed)")
            }
            val first = samples.first()
            val last = samples.last()
            val elapsedMs = last.first - first.first
            val pctDrop = first.second - last.second
            if (elapsedMs < 180_000L || pctDrop <= 0) {
                return Triple(null, null, "Calibrating observed drain rate (${elapsedMs / 60_000L}m observed)")
            }
            val hours = elapsedMs / 3_600_000f
            val drainPerHour = (pctDrop / hours).coerceIn(0.5f, 80f)
            val estHoursRemaining = levelPct / drainPerHour
            val estMinutes = (estHoursRemaining * 60f).roundToInt().coerceAtLeast(1)
            val h = estMinutes / 60
            val m = estMinutes % 60
            val formatted = if (h > 0) "~${h}h ${m}m est." else "~${m}m est."
            return Triple(drainPerHour, estMinutes, "$formatted (${String.format("%.1f", drainPerHour)}%/hr observed)")
        }
    }
}
