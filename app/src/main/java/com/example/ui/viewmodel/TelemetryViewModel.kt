package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DiagnosticRecordEntity
import com.example.data.local.DiagnosticsHistoryRepository
import com.example.data.local.PreferencesRepository
import com.example.domain.model.AppSettings
import com.example.domain.model.AppUpdateUiState
import com.example.domain.model.CompassState
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.GitHubReleaseManifest
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderModeState
import com.example.domain.model.SmartDiagnosticState
import com.example.domain.model.SmartGpsRecoveryState
import com.example.domain.model.SystemCheckState
import com.example.domain.model.TalabatDetectionResult
import com.example.domain.model.TalabatLaunchOutcome
import com.example.engine.CompassSensorEngine
import com.example.engine.GpsMonitorEngine
import com.example.engine.NetworkDiagnosticsEngine
import com.example.engine.OneTapDiagnosticRunner
import com.example.engine.RiderModeController
import com.example.engine.SmartGpsRecoveryEngine
import com.example.engine.SystemOptimizerEngine
import com.example.engine.TalabatDetectorEngine
import com.example.update.GitHubUpdateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TelemetryViewModel(
    private val gpsEngine: GpsMonitorEngine,
    private val networkEngine: NetworkDiagnosticsEngine,
    private val systemEngine: SystemOptimizerEngine,
    private val compassEngine: CompassSensorEngine,
    private val riderModeController: RiderModeController,
    private val diagnosticRunner: OneTapDiagnosticRunner,
    private val smartGpsRecoveryEngine: SmartGpsRecoveryEngine,
    private val talabatDetectorEngine: TalabatDetectorEngine,
    private val gitHubUpdateManager: GitHubUpdateManager,
    private val historyRepository: DiagnosticsHistoryRepository,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    val gpsState: StateFlow<GpsTelemetryState> = gpsEngine.state
    val networkState: StateFlow<NetworkDiagnosticsState> = networkEngine.state
    val systemState: StateFlow<SystemCheckState> = systemEngine.state
    val compassState: StateFlow<CompassState> = compassEngine.state
    val riderModeState: StateFlow<RiderModeState> = riderModeController.state
    val diagnosticState: StateFlow<SmartDiagnosticState> = diagnosticRunner.state
    val smartRecoveryState: StateFlow<SmartGpsRecoveryState> = smartGpsRecoveryEngine.state
    val talabatDetectionState: StateFlow<TalabatDetectionResult> = talabatDetectorEngine.state
    val updateState: StateFlow<AppUpdateUiState> = gitHubUpdateManager.state

    val settings: StateFlow<AppSettings> = preferencesRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val recentHistory: StateFlow<List<DiagnosticRecordEntity>> = historyRepository.recentRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            preferencesRepository.settingsFlow.collect { appSettings ->
                riderModeController.updateForegroundInterval(appSettings.gpsMonitoringIntervalMs)
            }
        }
        viewModelScope.launch {
            combine(gpsEngine.state, systemEngine.state) { _, _ -> Unit }.collect {
                smartGpsRecoveryEngine.updateSnapshotFromLiveEngines()
            }
        }
        gitHubUpdateManager.autoCheckUpdatesIfNeeded()
    }

    fun onForegroundResume() {
        riderModeController.onUiResumed()
        talabatDetectorEngine.refreshDetection()
        smartGpsRecoveryEngine.updateSnapshotFromLiveEngines()
    }

    fun onBackgroundPause() {
        compassEngine.stop()
        riderModeController.onUiPaused()
    }

    fun onPermissionResultUpdated() {
        systemEngine.refreshSystemChecks()
        riderModeController.refreshPermissionSnapshot()
        gpsEngine.startMonitoring(settings.value.gpsMonitoringIntervalMs)
        smartGpsRecoveryEngine.updateSnapshotFromLiveEngines()
    }

    fun startRiderMode(): Boolean = riderModeController.startRiderMode()
    fun stopRiderMode() = riderModeController.stopRiderMode()

    fun startCompass() = compassEngine.start()
    fun stopCompass() = compassEngine.stop()

    fun toggleGpsMonitoring() {
        if (gpsState.value.isMonitoringActive) {
            gpsEngine.stopMonitoring()
        } else {
            gpsEngine.startMonitoring(settings.value.gpsMonitoringIntervalMs)
        }
    }

    fun startGpsMonitoring() {
        gpsEngine.startMonitoring(settings.value.gpsMonitoringIntervalMs)
    }

    fun stopGpsMonitoring() {
        gpsEngine.stopMonitoring()
    }

    fun runSmartGpsRecovery() {
        viewModelScope.launch {
            smartGpsRecoveryEngine.runRecoveryWorkflow()
        }
    }

    fun refreshLocationRequest() {
        viewModelScope.launch {
            smartGpsRecoveryEngine.refreshSingleLocationRequest()
        }
    }

    fun resetSpeedStats() = gpsEngine.resetSpeedStats()

    fun refreshNetwork() = networkEngine.refreshNetworkSnapshot("Manual refresh")
    fun runLatencyTest() = networkEngine.runLatencyTest()
    fun runSpeedTest() = networkEngine.runSpeedTest()

    fun refreshSystemChecks() {
        systemEngine.refreshSystemChecks()
        riderModeController.refreshPermissionSnapshot()
        talabatDetectorEngine.refreshDetection()
        smartGpsRecoveryEngine.updateSnapshotFromLiveEngines()
    }

    fun runBoostDiagnostics() {
        viewModelScope.launch {
            gpsEngine.startMonitoring(settings.value.gpsMonitoringIntervalMs)
            diagnosticRunner.runFullDiagnostics()
        }
    }

    fun dismissDiagnosticReport() = diagnosticRunner.dismissReport()

    fun executeRecommendedAction(
        actionType: DiagnosticActionType,
        onRequestLocationPermission: () -> Unit
    ) {
        when (actionType) {
            DiagnosticActionType.REQUEST_LOCATION_PERMISSION -> onRequestLocationPermission()
            DiagnosticActionType.OPEN_LOCATION_SETTINGS -> systemEngine.openLocationSettings()
            DiagnosticActionType.OPEN_BATTERY_SETTINGS -> systemEngine.openBatteryOptimizationSettings()
            DiagnosticActionType.OPEN_APP_PERMISSIONS -> systemEngine.openAppPermissions()
            DiagnosticActionType.OPEN_NETWORK_SETTINGS -> systemEngine.openNetworkSettings()
            DiagnosticActionType.MOVE_TO_OPEN_SKY -> Unit
        }
    }

    fun openLocationSettings() = systemEngine.openLocationSettings()
    fun openBatterySettings() = systemEngine.openBatteryOptimizationSettings()
    fun openAppPermissions(pkg: String? = null) {
        if (pkg != null) systemEngine.openAppPermissions(pkg) else systemEngine.openAppPermissions()
    }
    fun openNetworkSettings() = systemEngine.openNetworkSettings()

    fun refreshTalabatDetection(): TalabatDetectionResult = talabatDetectorEngine.refreshDetection()
    fun selectManualRiderApp(packageName: String?) = talabatDetectorEngine.selectManualRiderApp(packageName)
    fun launchTalabatRider(): TalabatLaunchOutcome = talabatDetectorEngine.launchTalabatRider()

    fun openCoordinateInMaps(lat: Double, lon: Double): Boolean =
        systemEngine.openCoordinateInMaps(lat, lon)

    fun checkForUpdates() {
        viewModelScope.launch {
            gitHubUpdateManager.checkForUpdates(isManual = true)
        }
    }

    fun startUpdateDownload(manifest: GitHubReleaseManifest) =
        gitHubUpdateManager.startUpdateDownload(manifest)

    fun retryInstallVerifiedApk(): Boolean = gitHubUpdateManager.retryInstallVerifiedApk()
    fun openInstallUnknownAppsSettings() = gitHubUpdateManager.openInstallUnknownAppsSettings()
    fun dismissUpdateDialog() = gitHubUpdateManager.dismissUpdateDialog()
    fun showUpdateDialogIfAvailable() = gitHubUpdateManager.showUpdateDialogIfAvailable()
    fun openGitHubReleasesFallback() = gitHubUpdateManager.openGitHubReleasesFallback()

    companion object {
        fun factory(
            gpsEngine: GpsMonitorEngine,
            networkEngine: NetworkDiagnosticsEngine,
            systemEngine: SystemOptimizerEngine,
            compassEngine: CompassSensorEngine,
            riderModeController: RiderModeController,
            diagnosticRunner: OneTapDiagnosticRunner,
            smartGpsRecoveryEngine: SmartGpsRecoveryEngine,
            talabatDetectorEngine: TalabatDetectorEngine,
            gitHubUpdateManager: GitHubUpdateManager,
            historyRepository: DiagnosticsHistoryRepository,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TelemetryViewModel(
                    gpsEngine = gpsEngine,
                    networkEngine = networkEngine,
                    systemEngine = systemEngine,
                    compassEngine = compassEngine,
                    riderModeController = riderModeController,
                    diagnosticRunner = diagnosticRunner,
                    smartGpsRecoveryEngine = smartGpsRecoveryEngine,
                    talabatDetectorEngine = talabatDetectorEngine,
                    gitHubUpdateManager = gitHubUpdateManager,
                    historyRepository = historyRepository,
                    preferencesRepository = preferencesRepository
                ) as T
            }
        }
    }
}
