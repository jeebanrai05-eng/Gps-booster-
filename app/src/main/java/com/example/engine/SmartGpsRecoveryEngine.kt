package com.example.engine

import com.example.domain.model.DetectedGpsProblem
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.LocationSourceType
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RecoveryOutcome
import com.example.domain.model.RecoveryStepItem
import com.example.domain.model.SmartGpsRecoveryState
import com.example.domain.model.StepStatus
import com.example.domain.model.SystemCheckState
import com.example.domain.model.defaultRecoverySteps
import com.example.util.TelemetryFormatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.roundToInt

class SmartGpsRecoveryEngine(
    private val gpsEngine: GpsMonitorEngine,
    private val networkEngine: NetworkDiagnosticsEngine,
    private val systemEngine: SystemOptimizerEngine
) {
    private val _state = MutableStateFlow(SmartGpsRecoveryState())
    val state: StateFlow<SmartGpsRecoveryState> = _state.asStateFlow()

    fun detectCurrentProblems(
        gps: GpsTelemetryState,
        sys: SystemCheckState
    ): List<DetectedGpsProblem> {
        val problems = mutableListOf<DetectedGpsProblem>()

        if (!sys.hasFineLocationPermission) {
            problems.add(DetectedGpsProblem.LOCATION_PERMISSION_DENIED)
        }
        if (!sys.isLocationServicesEnabled || !sys.isGpsProviderEnabled) {
            problems.add(DetectedGpsProblem.GPS_DISABLED)
        }
        if (!gps.isLocationAvailable ||
            (gps.locationSourceType != LocationSourceType.GNSS && gps.lastValidGnssFixWallTimeMs == null && gps.fixStatus != GpsFixStatus.FIX_ACQUIRED)
        ) {
            problems.add(DetectedGpsProblem.NO_SATELLITE_FIX)
        }
        if (gps.gnssStatusSupported && gps.isMonitoringActive && gps.satellitesUsedInFix < 4 && gps.satellitesTotal < 4) {
            problems.add(DetectedGpsProblem.LOW_SATELLITE_VISIBILITY)
        }
        val avgCn0 = gps.averageCn0DbHz
        if (gps.gnssStatusSupported && avgCn0 != null && avgCn0 < 22f) {
            problems.add(DetectedGpsProblem.WEAK_GNSS_SIGNAL)
        }
        val age = gps.locationAgeSeconds
        if (gps.fixStatus == GpsFixStatus.STALE_FIX || (age != null && age > 20L)) {
            problems.add(DetectedGpsProblem.STALE_LOCATION)
        }
        val acc = gps.horizontalAccuracyMeters
        if (acc != null && acc > 35f) {
            problems.add(DetectedGpsProblem.POOR_LOCATION_ACCURACY)
        }
        if (gps.updateDelayDetected) {
            problems.add(DetectedGpsProblem.LOCATION_UPDATE_TIMEOUT)
        }
        if (!sys.hasBackgroundLocationPermission || sys.isBackgroundRestricted) {
            problems.add(DetectedGpsProblem.BACKGROUND_LOCATION_RESTRICTED)
        }
        if (sys.isPowerSaveMode || !sys.isBatteryOptimizationIgnored) {
            problems.add(DetectedGpsProblem.BATTERY_RESTRICTIONS_ACTIVE)
        }

        return problems.distinct()
    }

    fun updateSnapshotFromLiveEngines() {
        val gps = gpsEngine.state.value
        val sys = systemEngine.state.value
        val problems = detectCurrentProblems(gps, sys)

        val signalStr = buildSignalMeasurement(gps)
        val accuracyStr = gps.horizontalAccuracyMeters?.let { "±${it.roundToInt()} m" } ?: "Unavailable"
        val lastFixStr = formatLastFixText(gps)

        _state.update { current ->
            current.copy(
                gpsProviderEnabled = sys.isLocationServicesEnabled && sys.isGpsProviderEnabled,
                gnssFixAcquired = gps.fixStatus == GpsFixStatus.FIX_ACQUIRED && gps.locationSourceType == LocationSourceType.GNSS,
                satellitesVisible = gps.satellitesTotal,
                satellitesUsed = gps.satellitesUsedInFix,
                signalQualityMeasurement = signalStr,
                locationAccuracyText = accuracyStr,
                lastValidFixText = lastFixStr,
                locationSourceLabel = gps.locationSourceType.label,
                detectedProblems = problems
            )
        }
    }

    suspend fun refreshSingleLocationRequest(): Boolean {
        systemEngine.refreshSystemChecks()
        val ok = gpsEngine.requestFreshLocationFix()
        updateSnapshotFromLiveEngines()
        return ok
    }

    suspend fun runRecoveryWorkflow() {
        if (_state.value.isRunning) return

        val steps = defaultRecoverySteps().toMutableList()
        _state.value = SmartGpsRecoveryState(
            isRunning = true,
            progressFraction = 0f,
            steps = steps,
            outcome = RecoveryOutcome.RUNNING
        )

        suspend fun executeStep(index: Int, evaluator: suspend () -> Pair<StepStatus, String>) {
            steps[index] = steps[index].copy(status = StepStatus.RUNNING, detail = "Inspecting...")
            _state.update {
                it.copy(
                    steps = steps.toList(),
                    progressFraction = index / 11f
                )
            }
            delay(130L)
            val (status, detail) = evaluator()
            steps[index] = steps[index].copy(status = status, detail = detail)
            _state.update {
                it.copy(
                    steps = steps.toList(),
                    progressFraction = (index + 1) / 11f
                )
            }
        }

        systemEngine.refreshSystemChecks()
        gpsEngine.refreshStaticStatus()
        networkEngine.refreshNetworkSnapshot("Smart GPS Recovery check")

        // STEP 1: Check whether Android Location Services are enabled
        executeStep(0) {
            val sys = systemEngine.state.value
            if (sys.isLocationServicesEnabled) {
                StepStatus.PASS to "Android Location Services enabled"
            } else {
                StepStatus.FAIL to "Android Location Services disabled — open Location Settings"
            }
        }

        // STEP 2: Check GPS provider availability
        executeStep(1) {
            val sys = systemEngine.state.value
            val gps = gpsEngine.state.value
            when {
                !gps.hasGnssHardware -> StepStatus.FAIL to "GNSS hardware unavailable on this device"
                sys.isGpsProviderEnabled -> StepStatus.PASS to "GPS_PROVIDER active & ready"
                else -> StepStatus.FAIL to "GPS hardware provider is disabled"
            }
        }

        // STEP 3: Check current GNSS satellite information
        executeStep(2) {
            val gps = gpsEngine.state.value
            when {
                !gps.gnssStatusSupported -> StepStatus.WARNING to "GnssStatus telemetry unavailable on device"
                gps.satellitesUsedInFix >= 4 -> StepStatus.PASS to "${gps.satellitesUsedInFix} used / ${gps.satellitesTotal} visible"
                gps.satellitesTotal > 0 -> StepStatus.WARNING to "Low fix lock: ${gps.satellitesUsedInFix} used / ${gps.satellitesTotal} visible"
                else -> StepStatus.WARNING to "0 GNSS satellites reported yet — awaiting sky view"
            }
        }

        // STEP 4: Check whether a valid GNSS fix exists
        executeStep(3) {
            val gps = gpsEngine.state.value
            when {
                gps.locationSourceType == LocationSourceType.GNSS && gps.isLocationAvailable ->
                    StepStatus.PASS to "Valid GNSS satellite fix present (${gps.horizontalAccuracyMeters?.let { "±${it.roundToInt()}m" } ?: "active"})"
                gps.lastValidGnssFixWallTimeMs != null ->
                    StepStatus.WARNING to "Fallback source (${gps.locationSourceType.label}); prior GNSS fix at ${TelemetryFormatters.formatTime(gps.lastValidGnssFixWallTimeMs)}"
                gps.isLocationAvailable ->
                    StepStatus.WARNING to "Only ${gps.locationSourceType.label} fix available (no pure GNSS fix yet)"
                else ->
                    StepStatus.FAIL to "No valid GNSS fix available"
            }
        }

        // STEP 5: Check the freshness of the last valid location
        executeStep(4) {
            val gps = gpsEngine.state.value
            val age = gps.locationAgeSeconds
            when {
                age == null -> StepStatus.FAIL to "No location timestamp recorded yet"
                age <= 10L -> StepStatus.PASS to "Fresh fix (${age}s old)"
                age <= 20L -> StepStatus.WARNING to "Aging fix (${age}s old)"
                else -> StepStatus.FAIL to "Stale location (${age}s old)"
            }
        }

        // STEP 6: Check location permissions
        executeStep(5) {
            val sys = systemEngine.state.value
            when {
                sys.hasFineLocationPermission -> StepStatus.PASS to "ACCESS_FINE_LOCATION granted"
                sys.hasCoarseLocationPermission -> StepStatus.WARNING to "Coarse location only — Precise (Fine) needed for GNSS"
                else -> StepStatus.FAIL to "Location permission denied"
            }
        }

        // STEP 7: Check background execution restrictions
        executeStep(6) {
            val sys = systemEngine.state.value
            val issues = mutableListOf<String>()
            if (!sys.hasBackgroundLocationPermission) issues.add("Background location not 'All the time'")
            if (sys.isBackgroundRestricted) issues.add("App background restricted")
            if (sys.isPowerSaveMode) issues.add("Battery Saver active")
            if (!sys.isBatteryOptimizationIgnored) issues.add("Battery optimization enabled")
            if (issues.isEmpty()) {
                StepStatus.PASS to "Unrestricted background execution & battery exempt"
            } else {
                StepStatus.WARNING to issues.joinToString("; ")
            }
        }

        // STEP 8: Check network-assisted location availability (A-GPS / ephemeris aid)
        executeStep(7) {
            val sys = systemEngine.state.value
            val net: NetworkDiagnosticsState = networkEngine.state.value
            when {
                sys.isNetworkProviderEnabled && net.isNetworkValidated ->
                    StepStatus.PASS to "Network-assisted location & validated ${net.transportType.label} available"
                sys.isNetworkProviderEnabled || sys.isGooglePlayServicesAvailable ->
                    StepStatus.WARNING to "Network provider enabled, but internet is ${if (net.isNetworkAvailable) "unverified" else "offline"}"
                else ->
                    StepStatus.WARNING to "Network-assisted location unavailable (offline GNSS mode)"
            }
        }

        // STEP 9: Start a fresh, appropriately configured location request when the user enables recovery
        val initialUpdateNanos = gpsEngine.state.value.lastUpdateElapsedNanos
        var freshRequestDispatched = false
        executeStep(8) {
            val sys = systemEngine.state.value
            if (!sys.hasFineLocationPermission && !sys.hasCoarseLocationPermission) {
                StepStatus.FAIL to "Skipped: Location permission denied"
            } else if (!sys.isLocationServicesEnabled) {
                StepStatus.FAIL to "Skipped: Device Location Services are turned OFF"
            } else {
                freshRequestDispatched = true
                if (!gpsEngine.state.value.isMonitoringActive) {
                    gpsEngine.startMonitoring(2000L)
                }
                val acquiredImmediately = gpsEngine.requestFreshLocationFix(timeoutMs = 2200L)
                if (acquiredImmediately) {
                    StepStatus.PASS to "Fresh location request succeeded"
                } else {
                    StepStatus.WARNING to "Fresh location request dispatched; awaiting GNSS lock"
                }
            }
        }

        // STEP 10: Observe whether a fresh location fix is obtained
        executeStep(9) {
            if (!freshRequestDispatched) {
                StepStatus.FAIL to "Cannot observe fix until permission & Location Services are enabled"
            } else {
                val postNanos = gpsEngine.state.value.lastUpdateElapsedNanos
                if (postNanos == null || postNanos == initialUpdateNanos) {
                    delay(800L)
                }
                gpsEngine.refreshStaticStatus()
                val latest = gpsEngine.state.value
                val obtainedFresh = latest.lastUpdateElapsedNanos != null &&
                    (latest.lastUpdateElapsedNanos != initialUpdateNanos || (latest.locationAgeSeconds ?: 99L) <= 10L)
                when {
                    obtainedFresh && latest.locationSourceType == LocationSourceType.GNSS ->
                        StepStatus.PASS to "Fresh GNSS fix acquired (${latest.horizontalAccuracyMeters?.let { "±${it.roundToInt()}m" } ?: "active"})"
                    obtainedFresh ->
                        StepStatus.WARNING to "Fresh ${latest.locationSourceType.label} fix obtained; waiting for pure GNSS"
                    latest.satellitesTotal > 0 ->
                        StepStatus.WARNING to "No fresh fix within window (${latest.satellitesTotal} satellites visible)"
                    else ->
                        StepStatus.FAIL to "No fresh location fix obtained — check open sky view or Location Settings"
                }
            }
        }

        // STEP 11: Generate a recovery report
        systemEngine.refreshSystemChecks()
        gpsEngine.refreshStaticStatus()
        val finalGps = gpsEngine.state.value
        val finalSys = systemEngine.state.value
        val problems = detectCurrentProblems(finalGps, finalSys)

        val outcome = when {
            !finalSys.hasFineLocationPermission || !finalSys.isLocationServicesEnabled || !finalSys.isGpsProviderEnabled ->
                RecoveryOutcome.FAILED
            finalGps.fixStatus == GpsFixStatus.FIX_ACQUIRED &&
                finalGps.locationSourceType == LocationSourceType.GNSS &&
                (finalGps.locationAgeSeconds ?: 0L) <= 15L ->
                RecoveryOutcome.SUCCESS
            finalGps.isLocationAvailable ->
                RecoveryOutcome.INCOMPLETE
            else ->
                RecoveryOutcome.FAILED
        }

        val summary = when (outcome) {
            RecoveryOutcome.SUCCESS ->
                "GNSS lock verified (${finalGps.satellitesUsedInFix} sats, ${finalGps.horizontalAccuracyMeters?.let { "±${it.roundToInt()}m" } ?: "active"})."
            RecoveryOutcome.INCOMPLETE ->
                if (problems.isEmpty()) "Location active via ${finalGps.locationSourceType.label}; awaiting stronger GNSS fix."
                else "Partial recovery (${finalGps.locationSourceType.label}): ${problems.first().title}."
            RecoveryOutcome.FAILED ->
                problems.firstOrNull()?.description ?: "GPS fix could not be recovered. Check Location Settings & permissions."
            else -> ""
        }

        executeStep(10) {
            val status = when (outcome) {
                RecoveryOutcome.SUCCESS -> StepStatus.PASS
                RecoveryOutcome.INCOMPLETE -> StepStatus.WARNING
                else -> StepStatus.FAIL
            }
            status to summary
        }

        _state.update {
            it.copy(
                isRunning = false,
                progressFraction = 1f,
                steps = steps.toList(),
                outcome = outcome,
                gpsProviderEnabled = finalSys.isLocationServicesEnabled && finalSys.isGpsProviderEnabled,
                gnssFixAcquired = finalGps.fixStatus == GpsFixStatus.FIX_ACQUIRED && finalGps.locationSourceType == LocationSourceType.GNSS,
                satellitesVisible = finalGps.satellitesTotal,
                satellitesUsed = finalGps.satellitesUsedInFix,
                signalQualityMeasurement = buildSignalMeasurement(finalGps),
                locationAccuracyText = finalGps.horizontalAccuracyMeters?.let { acc -> "±${acc.roundToInt()} m" } ?: "Unavailable",
                lastValidFixText = formatLastFixText(finalGps),
                locationSourceLabel = finalGps.locationSourceType.label,
                detectedProblems = problems,
                reportSummary = summary,
                completedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun buildSignalMeasurement(gps: GpsTelemetryState): String {
        val avg = gps.averageCn0DbHz
        return if (avg != null) {
            String.format(Locale.US, "%.1f dB-Hz • %s", avg, gps.signalQualityLabel)
        } else {
            gps.signalQualityLabel
        }
    }

    private fun formatLastFixText(gps: GpsTelemetryState): String {
        val gnssTime = gps.lastValidGnssFixWallTimeMs
        val anyTime = gps.lastUpdateWallTimeMs
        return when {
            gnssTime != null -> "${TelemetryFormatters.formatTime(gnssTime)} (GNSS)"
            anyTime != null -> "${TelemetryFormatters.formatTime(anyTime)} (${gps.locationSourceType.label})"
            else -> "No valid fix"
        }
    }
}
