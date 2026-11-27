package com.example.domain.model

enum class ThermalSeverity(val label: String) {
    NOMINAL("Nominal"),
    MODERATE("Warm / Moderate"),
    SEVERE("Hot / Severe"),
    CRITICAL("Critical Overheat"),
    UNAVAILABLE("Unavailable")
}

data class DeviceIntelligenceState(
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val usedRamMb: Long = 0L,
    val ramUsagePercent: Int = 0,
    val isLowMemory: Boolean = false,
    val storageTotalGb: Float = 0f,
    val storageAvailableGb: Float = 0f,
    val storageUsedPercent: Int = 0,
    val cpuUsagePercent: Float? = null,
    val cpuUsageAvailable: Boolean = false,
    val cpuSourceLabel: String = "Checking...",
    val cpuTempCelsius: Float? = null,
    val cpuTempSourceLabel: String = "Unavailable",
    val thermalStatusCode: Int = -1,
    val thermalSeverity: ThermalSeverity = ThermalSeverity.UNAVAILABLE,
    val thermalStatusLabel: String = "Unavailable",
    val performanceHealthScore: Int = 100,
    val performanceHealthLabel: String = "Optimal",
    val overheatingWarning: String? = null,
    val degradationAlert: String? = null,
    val ramHistory: List<Int> = emptyList(),
    val lastUpdatedMs: Long = 0L
)

data class SimSlotTelemetry(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val networkOperatorName: String,
    val isActiveSlot: Boolean,
    val isDefaultData: Boolean,
    val isDefaultVoice: Boolean,
    val isDefaultSms: Boolean,
    val isRoaming: Boolean,
    val networkTypeLabel: String,
    val signalStrengthDbm: Int?,
    val signalLevel: Int,
    val signalQualityLabel: String,
    val dataAvailable: Boolean,
    val unavailableReason: String? = null
)

data class DualSimState(
    val hasPhoneStatePermission: Boolean = false,
    val hasTelephonyFeature: Boolean = false,
    val activeSubscriptionCount: Int = 0,
    val simSlots: List<SimSlotTelemetry> = emptyList(),
    val comparisonSummary: String = "Grant Phone State permission to inspect SIM 1 vs SIM 2 telemetry.",
    val signalDegradationAlert: String? = null,
    val lastUpdatedMs: Long = 0L
)

enum class ZoneQualityTier(val label: String) {
    GOOD("Strong Zone"),
    MIXED("Moderate Zone"),
    WEAK("Weak Signal Zone")
}

data class ZoneHeatCell(
    val zoneKey: String,
    val approximateLat: Double,
    val approximateLon: Double,
    val sampleCount: Int,
    val weakCount: Int,
    val avgAccuracyMeters: Float?,
    val avgSignalDbm: Int?,
    val gpsFixRatePercent: Int,
    val networkOkRatePercent: Int,
    val qualityTier: ZoneQualityTier,
    val lastObservedMs: Long
)

data class RiderZonesState(
    val isRecordingActiveNow: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val totalObservations: Int = 0,
    val zoneCells: List<ZoneHeatCell> = emptyList(),
    val repeatedWeakZoneWarnings: List<ZoneHeatCell> = emptyList(),
    val currentZoneCell: ZoneHeatCell? = null
)

data class BatteryIntelligenceState(
    val levelPercent: Int = -1,
    val temperatureC: Float? = null,
    val isCharging: Boolean = false,
    val chargingSource: String = "Unknown",
    val voltageMv: Int? = null,
    val currentMicroAmps: Int? = null,
    val currentSupported: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val observedDrainPerHourPct: Float? = null,
    val estimatedRemainingMinutes: Int? = null,
    val estimationLabel: String = "Calibrating drain rate...",
    val lowBatteryAlert: String? = null,
    val thermalAlert: String? = null,
    val lastUpdatedMs: Long = 0L
)

enum class TroubleSeverity(val label: String) {
    CRITICAL("Critical"),
    WARNING("Warning"),
    OPTIMAL("Healthy")
}

data class TroubleshootingItem(
    val id: String,
    val severity: TroubleSeverity,
    val category: String,
    val title: String,
    val observedEvidence: String,
    val recommendation: String,
    val actionLabel: String? = null,
    val actionType: DiagnosticActionType? = null
)

data class PerformancePeriodSummary(
    val periodLabel: String,
    val sessionsCount: Int,
    val totalRiderMinutes: Long,
    val avgGpsAccuracyMeters: Float?,
    val gnssFixAvailabilityPercent: Int?,
    val networkAvailabilityPercent: Int?,
    val networkInterruptionCount: Int,
    val batteryConsumedPercent: Int,
    val overheatEventsCount: Int,
    val readinessScore: Int?,
    val detectedProblems: List<String>,
    val suggestedImprovements: List<String>
)

data class RiderWorkspaceSummary(
    val todayWorkingMinutes: Long = 0L,
    val totalSessionsToday: Int = 0,
    val totalHistoricalSessions: Int = 0
)
