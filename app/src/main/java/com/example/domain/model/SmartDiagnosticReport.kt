package com.example.domain.model

enum class StepStatus {
    PENDING,
    RUNNING,
    PASS,
    WARNING,
    FAIL
}

data class DiagnosticStepItem(
    val stepNumber: Int,
    val title: String,
    val detail: String = "Queued",
    val status: StepStatus = StepStatus.PENDING
)

data class RecommendedAction(
    val id: String,
    val title: String,
    val description: String,
    val actionType: DiagnosticActionType
)

enum class DiagnosticActionType {
    REQUEST_LOCATION_PERMISSION,
    OPEN_LOCATION_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_APP_PERMISSIONS,
    OPEN_NETWORK_SETTINGS,
    MOVE_TO_OPEN_SKY
}

data class CompletedDiagnosticReport(
    val timestampMs: Long,
    val overallScore: Int,
    val gpsStatusSummary: String,
    val locationAccuracySummary: String,
    val networkStatusSummary: String,
    val batteryRestrictionSummary: String,
    val backgroundPermissionSummary: String,
    val detectedProblems: List<String>,
    val recommendedActions: List<RecommendedAction>
)

data class SmartDiagnosticState(
    val isRunning: Boolean = false,
    val currentStepIndex: Int = 0,
    val progressFraction: Float = 0f,
    val steps: List<DiagnosticStepItem> = defaultDiagnosticSteps(),
    val report: CompletedDiagnosticReport? = null
)

fun defaultDiagnosticSteps(): List<DiagnosticStepItem> = listOf(
    DiagnosticStepItem(1, "GPS Availability"),
    DiagnosticStepItem(2, "Location Permission"),
    DiagnosticStepItem(3, "GNSS Satellite Info"),
    DiagnosticStepItem(4, "Location Accuracy"),
    DiagnosticStepItem(5, "Location Update Delay"),
    DiagnosticStepItem(6, "Network Connectivity"),
    DiagnosticStepItem(7, "Network Stability"),
    DiagnosticStepItem(8, "Battery Optimization"),
    DiagnosticStepItem(9, "Background Permission"),
    DiagnosticStepItem(10, "Diagnostic Report")
)

enum class AnimationIntensity(val label: String) {
    FULL("Full"),
    REDUCED("Balanced"),
    MINIMAL("Battery Saver")
}

data class AppSettings(
    val darkTheme: Boolean = true,
    val animationIntensity: AnimationIntensity = AnimationIntensity.FULL,
    val useMetricUnits: Boolean = true,
    val gpsMonitoringIntervalMs: Long = 2000L
)
