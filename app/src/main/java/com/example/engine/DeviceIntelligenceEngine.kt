package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.ThermalSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class DeviceIntelligenceEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _state = MutableStateFlow(DeviceIntelligenceState())
    val state: StateFlow<DeviceIntelligenceState> = _state.asStateFlow()

    private val ramHistoryBuffer = ArrayDeque<Int>(20)
    private var monitorJob: Job? = null
    private var thermalListenerRegistered = false
    private var thermalListenerObj: Any? = null

    private var prevProcCpuTimeMs: Long? = null
    private var prevWallClockMs: Long? = null
    private var prevSysTotalTicks: Long? = null
    private var prevSysIdleTicks: Long? = null

    fun startMonitoring() {
        registerThermalListenerIfSupported()
        refreshSnapshot()
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3000L)
                refreshSnapshot()
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        unregisterThermalListenerIfSupported()
    }

    fun refreshSnapshot(): DeviceIntelligenceState {
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { activityManager.getMemoryInfo(memInfo) }
        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(0L)
        val availRamMb = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(0L)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)
        val ramUsagePct = if (totalRamMb > 0L) {
            ((usedRamMb.toDouble() / totalRamMb.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        val isLowMemory = memInfo.lowMemory

        if (totalRamMb > 0L) {
            if (ramHistoryBuffer.size >= 20) ramHistoryBuffer.removeFirst()
            ramHistoryBuffer.addLast(ramUsagePct)
        }

        val (storageTotalGb, storageAvailGb, storageUsedPct) = readStorageMetrics()
        val (cpuPct, cpuAvail, cpuLabel) = readCpuEstimation()
        val (thermalCode, thermalSeverity, thermalLabel) = readThermalStatus()
        val (cpuTempC, tempSourceLabel) = readDeviceTemperatureCelsius()

        val score = computeHealthScore(
            ramUsagePct = ramUsagePct,
            isLowMemory = isLowMemory,
            storageUsedPct = storageUsedPct,
            thermalSeverity = thermalSeverity,
            cpuTempC = cpuTempC
        )

        val healthLabel = when {
            score >= 85 -> "Optimal"
            score >= 65 -> "Good"
            score >= 45 -> "Moderate Load"
            else -> "Degraded"
        }

        val overheatWarning = when {
            thermalSeverity == ThermalSeverity.CRITICAL ->
                "Critical thermal throttling detected ($thermalLabel). Cool device immediately."
            thermalSeverity == ThermalSeverity.SEVERE ->
                "Severe device heat ($thermalLabel). GNSS and CPU performance may throttle."
            thermalSeverity == ThermalSeverity.MODERATE ->
                "Moderate thermal elevation ($thermalLabel). Avoid direct sun on dashboard mount."
            cpuTempC != null && cpuTempC >= 44.0f ->
                "Device temperature elevated (${String.format("%.1f", cpuTempC)}°C via $tempSourceLabel)."
            else -> null
        }

        val degradationAlert = buildList {
            if (isLowMemory || ramUsagePct >= 90) {
                add("Low RAM available (${availRamMb} MB free) — background apps may be killed.")
            } else if (ramUsagePct >= 82) {
                add("High memory pressure ($ramUsagePct% RAM in use).")
            }
            if (storageUsedPct >= 93) {
                add("Critical internal storage full ($storageUsedPct% used) — less than ${String.format("%.1f", storageAvailGb)} GB free.")
            }
            if (thermalSeverity == ThermalSeverity.SEVERE || thermalSeverity == ThermalSeverity.CRITICAL) {
                add("Thermal throttling active — background fix intervals may slow down.")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(" ")

        val updated = DeviceIntelligenceState(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            usedRamMb = usedRamMb,
            ramUsagePercent = ramUsagePct,
            isLowMemory = isLowMemory,
            storageTotalGb = storageTotalGb,
            storageAvailableGb = storageAvailGb,
            storageUsedPercent = storageUsedPct,
            cpuUsagePercent = cpuPct,
            cpuUsageAvailable = cpuAvail,
            cpuSourceLabel = cpuLabel,
            cpuTempCelsius = cpuTempC,
            cpuTempSourceLabel = tempSourceLabel,
            thermalStatusCode = thermalCode,
            thermalSeverity = thermalSeverity,
            thermalStatusLabel = thermalLabel,
            performanceHealthScore = score,
            performanceHealthLabel = healthLabel,
            overheatingWarning = overheatWarning,
            degradationAlert = degradationAlert,
            ramHistory = ramHistoryBuffer.toList(),
            lastUpdatedMs = System.currentTimeMillis()
        )
        _state.value = updated
        return updated
    }

    private fun readStorageMetrics(): Triple<Float, Float, Int> {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availBytes = stat.availableBytes
            val totalGb = totalBytes / (1024f * 1024f * 1024f)
            val availGb = availBytes / (1024f * 1024f * 1024f)
            val usedPct = if (totalBytes > 0L) {
                (((totalBytes - availBytes).toDouble() / totalBytes.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
            } else 0
            Triple(totalGb, availGb, usedPct)
        }.getOrDefault(Triple(0f, 0f, 0))
    }

    private fun readCpuEstimation(): Triple<Float?, Boolean, String> {
        // 1. Try system-wide /proc/stat when permitted (often restricted on Android 8+ by SELinux)
        val sysCpu = readSystemProcStat()
        if (sysCpu != null) {
            return Triple(sysCpu, true, "System /proc/stat")
        }

        // 2. Fallback to Android-supported Process.getElapsedCpuTime() for this app process
        val nowWall = SystemClock.elapsedRealtime()
        val nowCpu = Process.getElapsedCpuTime()
        val prevW = prevWallClockMs
        val prevC = prevProcCpuTimeMs
        prevWallClockMs = nowWall
        prevProcCpuTimeMs = nowCpu

        if (prevW != null && prevC != null && nowWall > prevW) {
            val wallDelta = (nowWall - prevW).toFloat()
            val cpuDelta = (nowCpu - prevC).coerceAtLeast(0L).toFloat()
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val pct = ((cpuDelta / (wallDelta * cores)) * 100f).coerceIn(0f, 100f)
            return Triple(pct, true, "App Process CPU (${cores} cores) — system /proc/stat restricted")
        }
        return Triple(null, false, "System CPU % restricted by Android OS; measuring app process...")
    }

    private fun readSystemProcStat(): Float? {
        return runCatching {
            val file = File("/proc/stat")
            if (!file.canRead()) return null
            val firstLine = file.bufferedReader().use { it.readLine() } ?: return null
            if (!firstLine.startsWith("cpu ")) return null
            val tokens = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (tokens.size < 4) return null
            val idle = tokens[3] + (tokens.getOrNull(4) ?: 0L)
            val total = tokens.sum()
            val prevTotal = prevSysTotalTicks
            val prevIdle = prevSysIdleTicks
            prevSysTotalTicks = total
            prevSysIdleTicks = idle
            if (prevTotal == null || prevIdle == null || total <= prevTotal) return null
            val dTotal = (total - prevTotal).toFloat()
            val dIdle = (idle - prevIdle).coerceAtLeast(0L).toFloat()
            (((dTotal - dIdle) / dTotal) * 100f).coerceIn(0f, 100f)
        }.getOrNull()
    }

    private fun readThermalStatus(): Triple<Int, ThermalSeverity, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Triple(-1, ThermalSeverity.UNAVAILABLE, "Requires Android 10+ (Thermal API)")
        }
        return runCatching {
            val status = powerManager.currentThermalStatus
            mapThermalStatus(status)
        }.getOrDefault(Triple(-1, ThermalSeverity.UNAVAILABLE, "Thermal API unavailable"))
    }

    private fun mapThermalStatus(status: Int): Triple<Int, ThermalSeverity, String> = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> Triple(status, ThermalSeverity.NOMINAL, "Nominal (Cool)")
        PowerManager.THERMAL_STATUS_LIGHT -> Triple(status, ThermalSeverity.NOMINAL, "Light Warmth")
        PowerManager.THERMAL_STATUS_MODERATE -> Triple(status, ThermalSeverity.MODERATE, "Moderate Heat")
        PowerManager.THERMAL_STATUS_SEVERE -> Triple(status, ThermalSeverity.SEVERE, "Severe Throttling")
        PowerManager.THERMAL_STATUS_CRITICAL -> Triple(status, ThermalSeverity.CRITICAL, "Critical Heat")
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> Triple(status, ThermalSeverity.CRITICAL, "Emergency Thermal")
        else -> Triple(status, ThermalSeverity.UNAVAILABLE, "Unknown ($status)")
    }

    private fun readDeviceTemperatureCelsius(): Pair<Float?, String> {
        // Attempt standard readable thermal sysfs only if kernel permits read-only access
        val sysfsTemp = runCatching {
            val dir = File("/sys/class/thermal")
            if (!dir.exists() || !dir.canRead()) return@runCatching null
            val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return@runCatching null
            for (zone in zones) {
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")
                if (!typeFile.canRead() || !tempFile.canRead()) continue
                val typeName = typeFile.readText().trim().lowercase()
                if (typeName.contains("cpu") || typeName.contains("soc") || typeName.contains("tsens") || typeName.contains("ap")) {
                    val raw = tempFile.readText().trim().toFloatOrNull() ?: continue
                    val celsius = if (raw > 1000f) raw / 1000f else if (raw > 120f) raw / 10f else raw
                    if (celsius in 10f..110f) {
                        return@runCatching celsius to "Kernel Thermal ($typeName)"
                    }
                }
            }
            null
        }.getOrNull()
        if (sysfsTemp != null) return sysfsTemp

        // Fallback to BatteryManager temperature sensor
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val tenthsC = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        if (tenthsC != Int.MIN_VALUE && tenthsC > 0) {
            return (tenthsC / 10f) to "Battery Thermal Sensor (CPU temp restricted)"
        }
        return null to "Temperature sensor unavailable"
    }

    private fun registerThermalListenerIfSupported() {
        if (thermalListenerRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        registerThermalListenerApi29()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun registerThermalListenerApi29() {
        val listener = PowerManager.OnThermalStatusChangedListener {
            refreshSnapshot()
        }
        runCatching {
            powerManager.addThermalStatusListener(listener)
            thermalListenerObj = listener
            thermalListenerRegistered = true
        }
    }

    private fun unregisterThermalListenerIfSupported() {
        if (!thermalListenerRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val listener = thermalListenerObj as? PowerManager.OnThermalStatusChangedListener ?: return
        runCatching { powerManager.removeThermalStatusListener(listener) }
        thermalListenerRegistered = false
        thermalListenerObj = null
    }

    companion object {
        fun computeHealthScore(
            ramUsagePct: Int,
            isLowMemory: Boolean,
            storageUsedPct: Int,
            thermalSeverity: ThermalSeverity,
            cpuTempC: Float?
        ): Int {
            var score = 100
            if (isLowMemory) score -= 28
            else if (ramUsagePct >= 90) score -= 22
            else if (ramUsagePct >= 80) score -= 12
            else if (ramUsagePct >= 70) score -= 5

            if (storageUsedPct >= 95) score -= 18
            else if (storageUsedPct >= 88) score -= 10
            else if (storageUsedPct >= 80) score -= 4

            when (thermalSeverity) {
                ThermalSeverity.CRITICAL -> score -= 40
                ThermalSeverity.SEVERE -> score -= 28
                ThermalSeverity.MODERATE -> score -= 14
                ThermalSeverity.NOMINAL, ThermalSeverity.UNAVAILABLE -> Unit
            }

            if (cpuTempC != null) {
                if (cpuTempC >= 46f) score -= 15
                else if (cpuTempC >= 42f) score -= 8
            }
            return score.coerceIn(0, 100)
        }
    }
}
