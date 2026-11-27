package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BatteryHistoryEntity
import com.example.data.local.RiderIntelligenceRepository
import com.example.data.local.SessionTimelineEventEntity
import com.example.data.local.SignalHandoverEntity
import com.example.data.local.WorkSessionEntity
import com.example.data.local.ZoneObservationEntity
import com.example.domain.model.BatteryIntelligenceState
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.DualSimState
import com.example.domain.model.PerformancePeriodSummary
import com.example.domain.model.RiderWorkspaceSummary
import com.example.domain.model.RiderZonesState
import com.example.domain.model.TroubleshootingItem
import com.example.engine.BatteryIntelligenceEngine
import com.example.engine.DeviceIntelligenceEngine
import com.example.engine.DualSimAnalyzerEngine
import com.example.engine.GpsMonitorEngine
import com.example.engine.NetworkDiagnosticsEngine
import com.example.engine.PerformanceReportEngine
import com.example.engine.RiderWorkspaceEngine
import com.example.engine.RiderZoneEngine
import com.example.engine.SystemOptimizerEngine
import com.example.engine.TroubleshootingEngine
import com.example.util.ReportExportHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RiderIntelligenceViewModel(
    private val deviceEngine: DeviceIntelligenceEngine,
    private val dualSimEngine: DualSimAnalyzerEngine,
    private val batteryEngine: BatteryIntelligenceEngine,
    private val zoneEngine: RiderZoneEngine,
    private val workspaceEngine: RiderWorkspaceEngine,
    private val troubleshootingEngine: TroubleshootingEngine,
    private val reportEngine: PerformanceReportEngine,
    private val repository: RiderIntelligenceRepository,
    gpsEngine: GpsMonitorEngine,
    networkEngine: NetworkDiagnosticsEngine,
    systemEngine: SystemOptimizerEngine
) : ViewModel() {

    val deviceState: StateFlow<DeviceIntelligenceState> = deviceEngine.state
    val dualSimState: StateFlow<DualSimState> = dualSimEngine.state
    val batteryState: StateFlow<BatteryIntelligenceState> = batteryEngine.state
    val zonesState: StateFlow<RiderZonesState> = zoneEngine.state
    val activeSession: StateFlow<WorkSessionEntity?> = workspaceEngine.activeSession
    val allSessions: StateFlow<List<WorkSessionEntity>> = workspaceEngine.allSessions
    val liveSessionDurationMs: StateFlow<Long> = workspaceEngine.liveSessionDurationMs
    val workspaceSummary: StateFlow<RiderWorkspaceSummary> = workspaceEngine.workspaceSummary
    val timelineEvents: StateFlow<List<SessionTimelineEventEntity>> = workspaceEngine.recentTimelineEvents

    val batteryHistory: StateFlow<List<BatteryHistoryEntity>> = repository.batteryHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val signalHandovers: StateFlow<List<SignalHandoverEntity>> = repository.signalHandoversFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val zoneObservations: StateFlow<List<ZoneObservationEntity>> = repository.zoneObservationsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val troubleshootingItems: StateFlow<List<TroubleshootingItem>> = combine(
        gpsEngine.state,
        networkEngine.state,
        systemEngine.state,
        deviceEngine.state,
        batteryEngine.state,
        dualSimEngine.state,
        zoneEngine.state,
        workspaceEngine.allSessions
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        troubleshootingEngine.analyze(
            gps = arr[0] as com.example.domain.model.GpsTelemetryState,
            network = arr[1] as com.example.domain.model.NetworkDiagnosticsState,
            system = arr[2] as com.example.domain.model.SystemCheckState,
            device = arr[3] as DeviceIntelligenceState,
            battery = arr[4] as BatteryIntelligenceState,
            dualSim = arr[5] as DualSimState,
            zones = arr[6] as RiderZonesState,
            recentSessions = arr[7] as List<WorkSessionEntity>
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dailyReport: StateFlow<PerformancePeriodSummary> = combine(
        workspaceEngine.allSessions,
        zoneObservations,
        batteryHistory,
        signalHandovers
    ) { sessions, zones, bat, handovers ->
        reportEngine.buildSummary(
            periodLabel = "Today",
            sinceMs = RiderWorkspaceEngine.startOfTodayMs(),
            sessions = sessions,
            zoneObservations = zones,
            batteryHistory = bat,
            handovers = handovers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PerformancePeriodSummary(
            periodLabel = "Today",
            sessionsCount = 0,
            totalRiderMinutes = 0L,
            avgGpsAccuracyMeters = null,
            gnssFixAvailabilityPercent = null,
            networkAvailabilityPercent = null,
            networkInterruptionCount = 0,
            batteryConsumedPercent = 0,
            overheatEventsCount = 0,
            readinessScore = null,
            detectedProblems = emptyList(),
            suggestedImprovements = emptyList()
        )
    )

    val weeklyReport: StateFlow<PerformancePeriodSummary> = combine(
        workspaceEngine.allSessions,
        zoneObservations,
        batteryHistory,
        signalHandovers
    ) { sessions, zones, bat, handovers ->
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L)
        reportEngine.buildSummary(
            periodLabel = "Last 7 Days",
            sinceMs = sevenDaysAgo,
            sessions = sessions,
            zoneObservations = zones,
            batteryHistory = bat,
            handovers = handovers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PerformancePeriodSummary(
            periodLabel = "Last 7 Days",
            sessionsCount = 0,
            totalRiderMinutes = 0L,
            avgGpsAccuracyMeters = null,
            gnssFixAvailabilityPercent = null,
            networkAvailabilityPercent = null,
            networkInterruptionCount = 0,
            batteryConsumedPercent = 0,
            overheatEventsCount = 0,
            readinessScore = null,
            detectedProblems = emptyList(),
            suggestedImprovements = emptyList()
        )
    )

    fun onForegroundResume() {
        deviceEngine.startMonitoring()
        dualSimEngine.startMonitoring()
        batteryEngine.startMonitoring()
    }

    fun onBackgroundPause() {
        deviceEngine.stopMonitoring()
        dualSimEngine.stopMonitoring()
        batteryEngine.stopMonitoring()
    }

    fun refreshAll() {
        deviceEngine.refreshSnapshot()
        dualSimEngine.refreshSnapshot()
        batteryEngine.refreshSnapshot(persistHistory = false)
    }

    fun onPhoneStatePermissionUpdated() {
        dualSimEngine.startMonitoring()
    }

    fun openSimSettings(): Boolean = dualSimEngine.openSimSettings()
    fun openInternetPanel(): Boolean = dualSimEngine.openInternetPanel()

    fun setZoneRecordingPaused(paused: Boolean) = zoneEngine.setRecordingPaused(paused)
    fun deleteAllZoneHistory() = zoneEngine.deleteAllLocationHistory()

    fun startWorkSession(notes: String = "") = workspaceEngine.startWorkSession(notes)
    fun pauseWorkSession() = workspaceEngine.pauseWorkSession()
    fun resumeWorkSession() = workspaceEngine.resumeWorkSession()
    fun endWorkSession() = workspaceEngine.endWorkSession()

    fun exportCsv(context: Context): Boolean =
        ReportExportHelper.exportAndShareCsv(context, allSessions.value, timelineEvents.value)

    fun exportPdf(context: Context): Boolean =
        ReportExportHelper.exportAndSharePdf(context, allSessions.value, timelineEvents.value)

    companion object {
        fun factory(
            deviceEngine: DeviceIntelligenceEngine,
            dualSimEngine: DualSimAnalyzerEngine,
            batteryEngine: BatteryIntelligenceEngine,
            zoneEngine: RiderZoneEngine,
            workspaceEngine: RiderWorkspaceEngine,
            troubleshootingEngine: TroubleshootingEngine,
            reportEngine: PerformanceReportEngine,
            repository: RiderIntelligenceRepository,
            gpsEngine: GpsMonitorEngine,
            networkEngine: NetworkDiagnosticsEngine,
            systemEngine: SystemOptimizerEngine
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RiderIntelligenceViewModel(
                    deviceEngine = deviceEngine,
                    dualSimEngine = dualSimEngine,
                    batteryEngine = batteryEngine,
                    zoneEngine = zoneEngine,
                    workspaceEngine = workspaceEngine,
                    troubleshootingEngine = troubleshootingEngine,
                    reportEngine = reportEngine,
                    repository = repository,
                    gpsEngine = gpsEngine,
                    networkEngine = networkEngine,
                    systemEngine = systemEngine
                ) as T
            }
        }
    }
}
