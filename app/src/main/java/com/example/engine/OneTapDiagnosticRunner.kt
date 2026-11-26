package com.example.engine

import com.example.data.local.DiagnosticRecordEntity
import com.example.data.local.DiagnosticsHistoryRepository
import com.example.domain.model.CompletedDiagnosticReport
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.DiagnosticStepItem
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.RecommendedAction
import com.example.domain.model.SmartDiagnosticState
import com.example.domain.model.StepStatus
import com.example.domain.model.defaultDiagnosticSteps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

class OneTapDiagnosticRunner(
    private val gpsEngine: GpsMonitorEngine,
    private val networkEngine: NetworkDiagnosticsEngine,
    private val systemEngine: SystemOptimizerEngine,
    private val historyRepository: DiagnosticsHistoryRepository
) {
    private val _state = MutableStateFlow(SmartDiagnosticState())
    val state: StateFlow<SmartDiagnosticState> = _state.asStateFlow()

    suspend fun runFullDiagnostics() {
        if (_state.value.isRunning) return

        val steps = defaultDiagnosticSteps().toMutableList()
        _state.value = SmartDiagnosticState(
            isRunning = true,
            currentStepIndex = 0,
            progressFraction = 0.05f,
            steps = steps,
            report = null
        )

        val problems = mutableListOf<String>()
        val actions = linkedMapOf<DiagnosticActionType, RecommendedAction>()
        var score = 100

        suspend fun executeStep(index: Int, evaluator: suspend () -> Pair<StepStatus, String>) {
            steps[index] = steps[index].copy(status = StepStatus.RUNNING, detail = "Inspecting...")
            _state.update {
                it.copy(
                    currentStepIndex = index,
                    progressFraction = (index + 0.4f) / 10f,
                    steps = steps.toList()
                )
            }
            delay(220L)
            val (status, detail) = evaluator()
            steps[index] = steps[index].copy(status = status, detail = detail)
            _state.update {
                it.copy(
                    progressFraction = (index + 1f) / 10f,
                    steps = steps.toList()
                )
            }
        }

        // Refresh live snapshots
        gpsEngine.refreshStaticStatus()
        networkEngine.refreshNetworkSnapshot("Smart diagnostic check")
        systemEngine.refreshSystemChecks()

        val gps = gpsEngine.state.value
        val sys = systemEngine.state.value
        val net = networkEngine.state.value

        // STEP 1: GPS availability
        executeStep(0) {
            when {
                !sys.isLocationServicesEnabled -> {
                    score -= 25
                    problems += "Device Location Services are turned off."
                    actions[DiagnosticActionType.OPEN_LOCATION_SETTINGS] = RecommendedAction(
                        id = "loc_settings",
                        title = "Enable Device Location",
                        description = "Turn on Location Services & High Accuracy GPS in Android Settings.",
                        actionType = DiagnosticActionType.OPEN_LOCATION_SETTINGS
                    )
                    StepStatus.FAIL to "Location Services disabled"
                }
                !gps.hasGnssHardware -> {
                    score -= 20
                    problems += "No dedicated GNSS hardware detected on this device."
                    StepStatus.WARNING to "No GNSS hardware; network fallback only"
                }
                !sys.isGpsProviderEnabled -> {
                    score -= 20
                    problems += "GPS hardware provider is disabled."
                    actions[DiagnosticActionType.OPEN_LOCATION_SETTINGS] = RecommendedAction(
                        id = "loc_settings",
                        title = "Enable GPS Provider",
                        description = "Switch Android Location mode to include GPS satellites.",
                        actionType = DiagnosticActionType.OPEN_LOCATION_SETTINGS
                    )
                    StepStatus.FAIL to "GPS provider disabled"
                }
                else -> StepStatus.PASS to "GNSS hardware & GPS provider active"
            }
        }

        // STEP 2: Location permission
        executeStep(1) {
            when {
                sys.hasFineLocationPermission -> StepStatus.PASS to "Precise (Fine) location granted"
                sys.hasCoarseLocationPermission -> {
                    score -= 15
                    problems += "Only Coarse location is granted; riders need Precise GPS."
                    actions[DiagnosticActionType.REQUEST_LOCATION_PERMISSION] = RecommendedAction(
                        id = "fine_perm",
                        title = "Grant Precise Location",
                        description = "Allow Precise Location for meter-level delivery accuracy.",
                        actionType = DiagnosticActionType.REQUEST_LOCATION_PERMISSION
                    )
                    StepStatus.WARNING to "Approximate (Coarse) only"
                }
                else -> {
                    score -= 25
                    problems += "Location permission is denied."
                    actions[DiagnosticActionType.REQUEST_LOCATION_PERMISSION] = RecommendedAction(
                        id = "fine_perm",
                        title = "Grant Location Permission",
                        description = "Allow location access so diagnostics can inspect GPS fixes.",
                        actionType = DiagnosticActionType.REQUEST_LOCATION_PERMISSION
                    )
                    StepStatus.FAIL to "Location permission denied"
                }
            }
        }

        // STEP 3: GNSS satellite information
        val latestGps3 = gpsEngine.state.value
        executeStep(2) {
            when {
                !latestGps3.hasFineLocationPermission || !latestGps3.isGpsProviderEnabled ->
                    StepStatus.WARNING to "Unavailable without Fine GPS permission & active GPS"
                !latestGps3.gnssStatusSupported ->
                    StepStatus.WARNING to "GnssStatus callback unavailable on this device"
                latestGps3.satellitesUsedInFix >= 5 ->
                    StepStatus.PASS to "${latestGps3.satellitesUsedInFix} used in fix (${latestGps3.satellitesTotal} visible)"
                latestGps3.satellitesTotal > 0 -> {
                    score -= 10
                    problems += "Low satellites locked in fix (${latestGps3.satellitesUsedInFix}/${latestGps3.satellitesTotal} visible)."
                    actions[DiagnosticActionType.MOVE_TO_OPEN_SKY] = RecommendedAction(
                        id = "open_sky",
                        title = "Improve Sky View",
                        description = "Move away from heavy concrete roofs or tunnels for clearer GNSS reception.",
                        actionType = DiagnosticActionType.MOVE_TO_OPEN_SKY
                    )
                    StepStatus.WARNING to "${latestGps3.satellitesUsedInFix} used / ${latestGps3.satellitesTotal} visible"
                }
                else -> {
                    score -= 10
                    problems += "No GNSS satellites currently locked."
                    StepStatus.WARNING to "0 satellites locked yet (searching)"
                }
            }
        }

        // STEP 4: Location accuracy
        val latestGps4 = gpsEngine.state.value
        executeStep(3) {
            val acc = latestGps4.horizontalAccuracyMeters
            when {
                acc == null -> {
                    score -= 10
                    StepStatus.WARNING to "No active location accuracy reading yet"
                }
                acc <= 15f -> StepStatus.PASS to "${acc.roundToInt()} m horizontal accuracy"
                acc <= 35f -> {
                    score -= 5
                    StepStatus.WARNING to "${acc.roundToInt()} m (moderate accuracy)"
                }
                else -> {
                    score -= 15
                    problems += "Poor horizontal accuracy (${acc.roundToInt()} m)."
                    actions[DiagnosticActionType.MOVE_TO_OPEN_SKY] = RecommendedAction(
                        id = "open_sky",
                        title = "Wait for Better GPS Lock",
                        description = "Current accuracy is ${acc.roundToInt()} m. Hold outdoors briefly to tighten lock.",
                        actionType = DiagnosticActionType.MOVE_TO_OPEN_SKY
                    )
                    StepStatus.FAIL to "${acc.roundToInt()} m (low precision)"
                }
            }
        }

        // STEP 5: Location update delay
        val latestGps5 = gpsEngine.state.value
        executeStep(4) {
            val age = latestGps5.locationAgeSeconds
            when {
                age == null -> StepStatus.WARNING to "No recent fix timestamp"
                latestGps5.updateDelayDetected || age > 20L -> {
                    score -= 10
                    problems += "Location updates are delayed (last fix ${age}s ago)."
                    StepStatus.WARNING to "Stale / delayed (${age}s old)"
                }
                else -> StepStatus.PASS to "Fresh updates (${age}s old)"
            }
        }

        // STEP 6: Network connectivity
        networkEngine.refreshNetworkSnapshot()
        val latestNet6 = networkEngine.state.value
        executeStep(5) {
            when {
                latestNet6.isNetworkValidated ->
                    StepStatus.PASS to "${latestNet6.transportType.label} connected & validated"
                latestNet6.isNetworkAvailable -> {
                    score -= 10
                    problems += "Connected to ${latestNet6.transportType.label}, but internet validation failed."
                    actions[DiagnosticActionType.OPEN_NETWORK_SETTINGS] = RecommendedAction(
                        id = "net_settings",
                        title = "Check Network Connection",
                        description = "Verify mobile data balance or switch away from captive Wi-Fi.",
                        actionType = DiagnosticActionType.OPEN_NETWORK_SETTINGS
                    )
                    StepStatus.WARNING to "${latestNet6.transportType.label} unvalidated"
                }
                else -> {
                    score -= 20
                    problems += "No active internet connection detected."
                    actions[DiagnosticActionType.OPEN_NETWORK_SETTINGS] = RecommendedAction(
                        id = "net_settings",
                        title = "Enable Mobile Data or Wi-Fi",
                        description = "Delivery apps require an active internet link to receive orders.",
                        actionType = DiagnosticActionType.OPEN_NETWORK_SETTINGS
                    )
                    StepStatus.FAIL to "Disconnected"
                }
            }
        }

        // STEP 7: Network stability
        executeStep(6) {
            when {
                !latestNet6.isNetworkAvailable -> StepStatus.FAIL to "Offline (0 transitions)"
                latestNet6.connectionLossCount > 0 -> {
                    score -= 8
                    problems += "Detected ${latestNet6.connectionLossCount} network drop(s) during session."
                    StepStatus.WARNING to "${latestNet6.connectionLossCount} drop(s), ${latestNet6.networkTransitionCount} switch(es)"
                }
                else -> StepStatus.PASS to "Stable link (${latestNet6.networkTransitionCount} switches)"
            }
        }

        // STEP 8: Battery optimization
        systemEngine.refreshSystemChecks()
        val latestSys8 = systemEngine.state.value
        executeStep(7) {
            when {
                latestSys8.isPowerSaveMode -> {
                    score -= 12
                    problems += "Device Battery Saver is ON — Android throttles background GPS & network."
                    actions[DiagnosticActionType.OPEN_BATTERY_SETTINGS] = RecommendedAction(
                        id = "battery_settings",
                        title = "Review Battery Saver & Optimization",
                        description = "Disable Battery Saver during shifts and allow unrestricted battery for rider apps.",
                        actionType = DiagnosticActionType.OPEN_BATTERY_SETTINGS
                    )
                    StepStatus.WARNING to "Battery Saver active"
                }
                latestSys8.isBackgroundRestricted -> {
                    score -= 12
                    problems += "App background execution is restricted by Android."
                    actions[DiagnosticActionType.OPEN_APP_PERMISSIONS] = RecommendedAction(
                        id = "app_perms",
                        title = "Remove Background Restriction",
                        description = "Set battery usage to Unrestricted in App Info.",
                        actionType = DiagnosticActionType.OPEN_APP_PERMISSIONS
                    )
                    StepStatus.FAIL to "Background restricted"
                }
                !latestSys8.isBatteryOptimizationIgnored -> {
                    score -= 5
                    StepStatus.WARNING to "Standard battery optimization active"
                }
                else -> StepStatus.PASS to "Battery optimization unrestricted"
            }
        }

        // STEP 9: Background permission
        executeStep(8) {
            when {
                latestSys8.hasBackgroundLocationPermission ->
                    StepStatus.PASS to "All-the-time location enabled"
                latestSys8.hasFineLocationPermission -> {
                    StepStatus.PASS to "Foreground-only mode (Privacy Safe)"
                }
                else -> StepStatus.WARNING to "Location access not granted"
            }
        }

        // STEP 10: Generate Diagnostic Report
        val finalScore = score.coerceIn(0, 100)
        val finalGps = gpsEngine.state.value
        val gpsSummary = when (finalGps.fixStatus) {
            GpsFixStatus.FIX_ACQUIRED -> "Active Fix (${finalGps.satellitesUsedInFix}/${finalGps.satellitesTotal} sats)"
            GpsFixStatus.STALE_FIX -> "Stale Fix (${finalGps.locationAgeSeconds ?: 0}s old)"
            GpsFixStatus.SEARCHING -> "Searching Satellites"
            GpsFixStatus.GPS_DISABLED -> "GPS Disabled"
            GpsFixStatus.PERMISSION_DENIED -> "Permission Denied"
            GpsFixStatus.HARDWARE_UNAVAILABLE -> "GNSS Unavailable"
        }
        val accuracySummary = finalGps.horizontalAccuracyMeters?.let { "±${it.roundToInt()} m" } ?: "Unavailable"
        val networkSummary = when {
            latestNet6.isNetworkValidated -> "${latestNet6.transportType.label} • Validated"
            latestNet6.isNetworkAvailable -> "${latestNet6.transportType.label} • No Internet Validation"
            else -> "Offline"
        }
        val batterySummary = when {
            latestSys8.isPowerSaveMode -> "Battery Saver ON (${latestSys8.batteryLevelPercent}%)"
            latestSys8.isBackgroundRestricted -> "Background Restricted"
            latestSys8.isBatteryOptimizationIgnored -> "Unrestricted (${latestSys8.batteryLevelPercent}%)"
            else -> "Optimized (${latestSys8.batteryLevelPercent}%)"
        }
        val bgPermSummary = if (latestSys8.hasBackgroundLocationPermission) {
            "Allow all the time"
        } else if (latestSys8.hasFineLocationPermission) {
            "While using app (Foreground only)"
        } else {
            "Not granted"
        }

        val report = CompletedDiagnosticReport(
            timestampMs = System.currentTimeMillis(),
            overallScore = finalScore,
            gpsStatusSummary = gpsSummary,
            locationAccuracySummary = accuracySummary,
            networkStatusSummary = networkSummary,
            batteryRestrictionSummary = batterySummary,
            backgroundPermissionSummary = bgPermSummary,
            detectedProblems = problems.toList(),
            recommendedActions = actions.values.toList()
        )

        executeStep(9) {
            historyRepository.saveRecord(
                DiagnosticRecordEntity(
                    timestampMs = report.timestampMs,
                    overallScore = finalScore,
                    gpsStatus = gpsSummary,
                    horizontalAccuracyMeters = finalGps.horizontalAccuracyMeters,
                    satellitesTotal = finalGps.satellitesTotal,
                    satellitesUsedInFix = finalGps.satellitesUsedInFix,
                    networkStatus = networkSummary,
                    batteryStatus = batterySummary,
                    issuesCount = problems.size,
                    summaryNote = if (problems.isEmpty()) "All checks healthy" else problems.first()
                )
            )
            StepStatus.PASS to "Score $finalScore/100 • ${problems.size} issue(s)"
        }

        _state.update {
            it.copy(
                isRunning = false,
                progressFraction = 1f,
                steps = steps.toList(),
                report = report
            )
        }
    }

    fun dismissReport() {
        _state.update { it.copy(report = null) }
    }
}
