package com.example.domain.model

sealed interface TalabatInstallationStatus {
    data class Installed(
        val packageName: String,
        val versionName: String?
    ) : TalabatInstallationStatus

    data object NotInstalled : TalabatInstallationStatus
}

data class SystemCheckState(
    val isLocationServicesEnabled: Boolean = false,
    val isGpsProviderEnabled: Boolean = false,
    val isNetworkProviderEnabled: Boolean = false,
    val isGooglePlayServicesAvailable: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasCoarseLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isBackgroundRestricted: Boolean = false,
    val batteryLevelPercent: Int = -1,
    val isCharging: Boolean = false,
    val chargingTypeLabel: String = "Not Charging",
    val availableLocationProviders: List<String> = emptyList(),
    val talabatStatus: TalabatInstallationStatus = TalabatInstallationStatus.NotInstalled
)

data class CompassState(
    val azimuthDegrees: Float? = null,
    val cardinalDirection: String = "--",
    val sensorAccuracy: Int = 0,
    val needsCalibration: Boolean = false,
    val hasCompassSensors: Boolean = true,
    val isActive: Boolean = false
)
