package com.example.domain.model

enum class DetectedGpsProblem(
    val title: String,
    val description: String,
    val isCritical: Boolean
) {
    GPS_DISABLED(
        title = "GPS Disabled",
        description = "Device Location Services or GPS hardware provider is turned off.",
        isCritical = true
    ),
    NO_SATELLITE_FIX(
        title = "No Satellite Fix",
        description = "No active GNSS satellite fix has been acquired yet.",
        isCritical = true
    ),
    LOW_SATELLITE_VISIBILITY(
        title = "Low Satellite Visibility",
        description = "Fewer than 4 GNSS satellites are visible or locked in fix.",
        isCritical = false
    ),
    WEAK_GNSS_SIGNAL(
        title = "Weak GNSS Signal",
        description = "Average carrier-to-noise (C/N0) density is below 22 dB-Hz (urban canyon / indoor shielding).",
        isCritical = false
    ),
    STALE_LOCATION(
        title = "Stale Location",
        description = "The most recent location fix is older than 20 seconds.",
        isCritical = true
    ),
    POOR_LOCATION_ACCURACY(
        title = "Poor Location Accuracy",
        description = "Horizontal accuracy radius exceeds 35 meters.",
        isCritical = false
    ),
    LOCATION_UPDATE_TIMEOUT(
        title = "Location Update Timeout",
        description = "Location updates are arriving slower than expected.",
        isCritical = false
    ),
    LOCATION_PERMISSION_DENIED(
        title = "Location Permission Denied",
        description = "Precise (Fine) location permission is missing.",
        isCritical = true
    ),
    BACKGROUND_LOCATION_RESTRICTED(
        title = "Background Location Restricted",
        description = "Background location ('Allow all the time') or background execution is restricted.",
        isCritical = false
    ),
    BATTERY_RESTRICTIONS_ACTIVE(
        title = "Battery Restrictions Affecting Monitoring",
        description = "Battery Saver or standard app battery optimization can throttle background GPS updates.",
        isCritical = false
    )
}

enum class RecoveryOutcome {
    IDLE,
    RUNNING,
    SUCCESS,
    INCOMPLETE,
    FAILED
}

data class RecoveryStepItem(
    val stepNumber: Int,
    val title: String,
    val detail: String = "Pending",
    val status: StepStatus = StepStatus.PENDING
)

data class SmartGpsRecoveryState(
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val steps: List<RecoveryStepItem> = defaultRecoverySteps(),
    val outcome: RecoveryOutcome = RecoveryOutcome.IDLE,
    val gpsProviderEnabled: Boolean = false,
    val gnssFixAcquired: Boolean = false,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val signalQualityMeasurement: String = "N/A",
    val locationAccuracyText: String = "Unavailable",
    val lastValidFixText: String = "None",
    val locationSourceLabel: String = "Unavailable",
    val detectedProblems: List<DetectedGpsProblem> = emptyList(),
    val reportSummary: String? = null,
    val completedAtMs: Long? = null
)

fun defaultRecoverySteps(): List<RecoveryStepItem> = listOf(
    RecoveryStepItem(1, "Android Location Services"),
    RecoveryStepItem(2, "GPS Provider Availability"),
    RecoveryStepItem(3, "GNSS Satellite Status"),
    RecoveryStepItem(4, "Valid GNSS Fix Check"),
    RecoveryStepItem(5, "Location Freshness Check"),
    RecoveryStepItem(6, "Location Permissions"),
    RecoveryStepItem(7, "Background Restrictions"),
    RecoveryStepItem(8, "Network-Assisted Location"),
    RecoveryStepItem(9, "Fresh Location Request"),
    RecoveryStepItem(10, "Fresh Fix Observation"),
    RecoveryStepItem(11, "Recovery Report")
)
