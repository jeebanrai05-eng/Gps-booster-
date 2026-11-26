package com.example.domain.model

enum class GpsFixStatus {
    PERMISSION_DENIED,
    GPS_DISABLED,
    HARDWARE_UNAVAILABLE,
    SEARCHING,
    FIX_ACQUIRED,
    STALE_FIX
}

enum class LocationSourceType(val label: String) {
    GNSS("GNSS Satellite"),
    FUSED("Fused Provider"),
    NETWORK("Network Assisted"),
    NONE("Unavailable")
}

data class SatelliteInfo(
    val svid: Int,
    val constellation: String,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean
)

data class GpsTelemetryState(
    val hasFineLocationPermission: Boolean = false,
    val hasCoarseLocationPermission: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    val isGpsProviderEnabled: Boolean = false,
    val isNetworkProviderEnabled: Boolean = false,
    val isFusedLocationAvailable: Boolean = false,
    val hasGnssHardware: Boolean = true,
    val gnssStatusSupported: Boolean = true,
    val satellitesTotal: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val averageCn0DbHz: Float? = null,
    val maxCn0DbHz: Float? = null,
    val constellationCounts: Map<String, Int> = emptyMap(),
    val satelliteDetails: List<SatelliteInfo> = emptyList(),
    val signalQualityLabel: String = "Awaiting Monitoring",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val horizontalAccuracyMeters: Float? = null,
    val verticalAccuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val speedSampleCount: Int = 0,
    val bearingDegrees: Float? = null,
    val lastUpdateWallTimeMs: Long? = null,
    val lastUpdateElapsedNanos: Long? = null,
    val lastValidGnssFixWallTimeMs: Long? = null,
    val lastValidGnssFixElapsedNanos: Long? = null,
    val locationAgeSeconds: Long? = null,
    val updateIntervalMs: Long? = null,
    val updateDelayDetected: Boolean = false,
    val locationStabilityLabel: String = "Insufficient Samples",
    val providerName: String? = null,
    val locationSourceType: LocationSourceType = LocationSourceType.NONE,
    val fixStatus: GpsFixStatus = GpsFixStatus.SEARCHING,
    val isMonitoringActive: Boolean = false,
    val isLocationAvailable: Boolean = false
)
