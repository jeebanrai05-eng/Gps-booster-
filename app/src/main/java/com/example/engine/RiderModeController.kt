package com.example.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesRepository
import com.example.domain.model.RiderModeState
import com.example.service.RiderModeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RiderModeController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gpsEngine: GpsMonitorEngine,
    private val networkEngine: NetworkDiagnosticsEngine,
    private val systemEngine: SystemOptimizerEngine,
    private val preferencesRepository: PreferencesRepository
) {
    private val _state = MutableStateFlow(RiderModeState())
    val state: StateFlow<RiderModeState> = _state.asStateFlow()

    @Volatile
    var isUiInForeground: Boolean = false
        private set

    private var foregroundIntervalMs: Long = 2000L

    init {
        refreshPermissionSnapshot()
    }

    fun updateForegroundInterval(intervalMs: Long) {
        foregroundIntervalMs = intervalMs.coerceAtLeast(1000L)
        if (isUiInForeground) {
            gpsEngine.updateInterval(foregroundIntervalMs)
        }
    }

    fun backgroundMonitoringIntervalMs(): Long = maxOf(foregroundIntervalMs, 5000L)

    fun refreshPermissionSnapshot() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFine || hasCoarse
        }
        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _state.update {
            it.copy(
                hasForegroundLocationPermission = hasFine || hasCoarse,
                hasBackgroundLocationPermission = hasBg,
                hasNotificationPermission = hasNotif,
                monitoringIntervalMs = backgroundMonitoringIntervalMs()
            )
        }
    }

    fun onUiResumed() {
        isUiInForeground = true
        refreshPermissionSnapshot()
        systemEngine.refreshSystemChecks()
        gpsEngine.startMonitoring(foregroundIntervalMs)
        networkEngine.startMonitoring()
    }

    fun onUiPaused() {
        isUiInForeground = false
        if (_state.value.isActive) {
            // Switch to battery-efficient background interval while Rider Mode FGS owns monitoring
            gpsEngine.startMonitoring(backgroundMonitoringIntervalMs())
        } else {
            gpsEngine.stopMonitoring()
            networkEngine.stopMonitoring()
        }
    }

    fun startRiderMode(): Boolean {
        refreshPermissionSnapshot()
        val snap = _state.value
        if (!snap.hasForegroundLocationPermission) {
            _state.update {
                it.copy(
                    isActive = false,
                    isStarting = false,
                    statusLabel = "Location Permission Required",
                    restrictionNote = "Grant Precise Location so Android can run the Rider Mode location foreground service."
                )
            }
            return false
        }

        _state.update {
            it.copy(
                isStarting = true,
                restrictionNote = null,
                statusLabel = "Starting Rider Mode..."
            )
        }

        val intent = Intent(context, RiderModeService::class.java).apply {
            action = RiderModeService.ACTION_START_RIDER_MODE
        }
        return runCatching {
            ContextCompat.startForegroundService(context, intent)
            true
        }.getOrElse { err ->
            _state.update {
                it.copy(
                    isActive = false,
                    isStarting = false,
                    statusLabel = "Start Blocked by System",
                    restrictionNote = err.localizedMessage ?: "Android blocked starting the foreground service."
                )
            }
            false
        }
    }

    fun stopRiderMode() {
        val intent = Intent(context, RiderModeService::class.java).apply {
            action = RiderModeService.ACTION_STOP_RIDER_MODE
        }
        runCatching { context.startService(intent) }
        runCatching { context.stopService(Intent(context, RiderModeService::class.java)) }
        markServiceStopped(restrictionNote = null)
    }

    fun markServiceActive(startedAtMs: Long) {
        scope.launch {
            preferencesRepository.setRiderModeActive(true)
        }
        _state.update {
            it.copy(
                isActive = true,
                isStarting = false,
                startedAtMs = startedAtMs,
                statusLabel = "Rider Mode Active",
                restrictionNote = null,
                monitoringIntervalMs = backgroundMonitoringIntervalMs()
            )
        }
    }

    fun markServiceStopped(restrictionNote: String?) {
        scope.launch {
            preferencesRepository.setRiderModeActive(false)
        }
        _state.update {
            it.copy(
                isActive = false,
                isStarting = false,
                startedAtMs = null,
                activeAlertSummary = null,
                statusLabel = "Monitoring Stopped",
                restrictionNote = restrictionNote
            )
        }
        if (!isUiInForeground) {
            gpsEngine.stopMonitoring()
            networkEngine.stopMonitoring()
        } else {
            gpsEngine.updateInterval(foregroundIntervalMs)
        }
    }

    fun updateServiceTelemetrySummary(
        activeAlertSummary: String?,
        lastCheckMs: Long
    ) {
        _state.update {
            it.copy(
                activeAlertSummary = activeAlertSummary,
                lastMonitorCheckMs = lastCheckMs
            )
        }
    }

    suspend fun isRiderModePersistedActive(): Boolean {
        return runCatching { preferencesRepository.riderModeActiveFlow.first() }.getOrDefault(false)
    }
}
