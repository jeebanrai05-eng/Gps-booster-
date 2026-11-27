package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val status: String, // ACTIVE, PAUSED, ENDED
    val activeDurationMs: Long = 0L,
    val lastResumedAtMs: Long? = null,
    val totalSamples: Int = 0,
    val gpsFixedSamples: Int = 0,
    val totalAccuracySumMeters: Double = 0.0,
    val accuracySampleCount: Int = 0,
    val networkOkSamples: Int = 0,
    val networkLossCount: Int = 0,
    val overheatEventCount: Int = 0,
    val startBatteryPct: Int = -1,
    val endBatteryPct: Int? = null,
    val maxBatteryTempC: Float? = null,
    val notes: String = ""
)

@Entity(tableName = "session_timeline_events")
data class SessionTimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val category: String, // SESSION, GPS, NETWORK, THERMAL, BATTERY, SIM_HANDOVER
    val severity: String, // INFO, WARNING, CRITICAL, RECOVERY
    val title: String,
    val detail: String
)

@Entity(tableName = "zone_observations")
data class ZoneObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long? = null,
    val timestampMs: Long,
    val zoneKey: String,
    val approximateLat: Double,
    val approximateLon: Double,
    val gpsAccuracyMeters: Float? = null,
    val satellitesUsed: Int = 0,
    val gpsFixOk: Boolean,
    val networkAvailable: Boolean,
    val networkValidated: Boolean,
    val transportType: String,
    val signalDbm: Int? = null,
    val isWeakSignal: Boolean
)

@Entity(tableName = "battery_history")
data class BatteryHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long? = null,
    val timestampMs: Long,
    val levelPercent: Int,
    val temperatureC: Float? = null,
    val isCharging: Boolean,
    val chargingSource: String,
    val voltageMv: Int? = null,
    val currentMicroAmps: Int? = null,
    val isPowerSave: Boolean
)

@Entity(tableName = "signal_handovers")
data class SignalHandoverEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMs: Long,
    val slotIndex: Int,
    val subscriptionId: Int,
    val operatorName: String,
    val previousNetworkType: String? = null,
    val newNetworkType: String,
    val rssiDbm: Int? = null,
    val signalLevel: Int,
    val alertMessage: String? = null
)
