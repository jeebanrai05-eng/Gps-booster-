package com.example.domain.model

data class InstalledAppOption(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val launcherActivityName: String?,
    val isEnabled: Boolean,
    val isPriorityMatch: Boolean
)

data class TalabatDetectionResult(
    val isInstalled: Boolean = false,
    val detectedAppName: String? = null,
    val detectedPackageName: String? = null,
    val launchActivityAvailable: Boolean = false,
    val launchActivityName: String? = null,
    val appVersion: String? = null,
    val detectionMethod: String = "Not Scanned",
    val isAppDisabled: Boolean = false,
    val savedManualPackage: String? = null,
    val matchingApps: List<InstalledAppOption> = emptyList(),
    val allLaunchableApps: List<InstalledAppOption> = emptyList(),
    val errorNote: String? = null
)

sealed interface TalabatLaunchOutcome {
    data class Launched(val appName: String, val packageName: String) : TalabatLaunchOutcome
    data object NotInstalled : TalabatLaunchOutcome
    data class AppDisabled(val packageName: String) : TalabatLaunchOutcome
    data class LauncherUnavailable(val packageName: String) : TalabatLaunchOutcome
    data class Failed(val reason: String) : TalabatLaunchOutcome
}
