package com.example.engine

import com.example.data.local.PreferencesRepository
import com.example.data.local.RiderIntelligenceRepository
import com.example.data.local.ZoneObservationEntity
import com.example.domain.model.DualSimState
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderZonesState
import com.example.domain.model.ZoneHeatCell
import com.example.domain.model.ZoneQualityTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RiderZoneEngine(
    private val repository: RiderIntelligenceRepository,
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(RiderZonesState())
    val state: StateFlow<RiderZonesState> = _state.asStateFlow()

    @Volatile
    private var lastRecordedAtMs: Long = 0L

    @Volatile
    private var lastSessionActiveFlag: Boolean = false

    init {
        scope.launch {
            combine(
                repository.zoneObservationsFlow,
                preferencesRepository.zoneRecordingPausedFlow
            ) { observations, isPaused ->
                val cells = aggregateObservations(observations)
                val weakWarnings = cells.filter { cell ->
                    cell.weakCount >= 2 || (cell.sampleCount >= 2 && (cell.weakCount.toFloat() / cell.sampleCount) >= 0.45f)
                }.sortedByDescending { it.weakCount }
                RiderZonesState(
                    isRecordingActiveNow = lastSessionActiveFlag && !isPaused,
                    isRecordingPaused = isPaused,
                    totalObservations = observations.size,
                    zoneCells = cells,
                    repeatedWeakZoneWarnings = weakWarnings,
                    currentZoneCell = observations.firstOrNull()?.let { latest ->
                        cells.firstOrNull { it.zoneKey == latest.zoneKey }
                    }
                )
            }.collect { next ->
                _state.value = next
            }
        }
    }

    fun updateActiveSessionFlag(isSessionOrRiderActive: Boolean) {
        lastSessionActiveFlag = isSessionOrRiderActive
        _state.value = _state.value.copy(
            isRecordingActiveNow = isSessionOrRiderActive && !_state.value.isRecordingPaused
        )
    }

    suspend fun recordObservationIfAllowed(
        isSessionOrRiderActive: Boolean,
        activeSessionId: Long?,
        gps: GpsTelemetryState,
        network: NetworkDiagnosticsState,
        dualSim: DualSimState
    ) {
        updateActiveSessionFlag(isSessionOrRiderActive)
        if (!isSessionOrRiderActive || _state.value.isRecordingPaused) return
        val lat = gps.latitude ?: return
        val lon = gps.longitude ?: return

        val now = System.currentTimeMillis()
        if (now - lastRecordedAtMs < 15_000L) return
        lastRecordedAtMs = now

        val (approxLat, approxLon, zoneKey) = quantizeCoordinates(lat, lon)
        val activeSim = dualSim.simSlots.firstOrNull { it.isDefaultData } ?: dualSim.simSlots.firstOrNull()
        val signalDbm = activeSim?.signalStrengthDbm

        val gpsFixOk = gps.fixStatus == GpsFixStatus.FIX_ACQUIRED && (gps.horizontalAccuracyMeters ?: 999f) <= 30f
        val weakCell = signalDbm != null && signalDbm <= -108
        val isWeakSignal = !gpsFixOk || !network.isNetworkValidated || weakCell

        repository.insertZoneObservation(
            ZoneObservationEntity(
                sessionId = activeSessionId,
                timestampMs = now,
                zoneKey = zoneKey,
                approximateLat = approxLat,
                approximateLon = approxLon,
                gpsAccuracyMeters = gps.horizontalAccuracyMeters,
                satellitesUsed = gps.satellitesUsedInFix,
                gpsFixOk = gpsFixOk,
                networkAvailable = network.isNetworkAvailable,
                networkValidated = network.isNetworkValidated,
                transportType = network.transportType.label,
                signalDbm = signalDbm,
                isWeakSignal = isWeakSignal
            )
        )
    }

    fun setRecordingPaused(paused: Boolean) {
        scope.launch {
            preferencesRepository.setZoneRecordingPaused(paused)
        }
    }

    fun deleteAllLocationHistory() {
        scope.launch {
            repository.clearAllZoneObservations()
        }
    }

    companion object {
        /**
         * Quantize coordinates to an approximate ~200m grid cell for local rider zone aggregation
         * without storing exact street/door coordinates unnecessarily.
         */
        fun quantizeCoordinates(lat: Double, lon: Double): Triple<Double, Double, String> {
            val qLat = (lat * 500.0).roundToInt() / 500.0
            val qLon = (lon * 500.0).roundToInt() / 500.0
            val key = String.format("%.3f,%.3f", qLat, qLon)
            return Triple(qLat, qLon, key)
        }

        fun aggregateObservations(observations: List<ZoneObservationEntity>): List<ZoneHeatCell> {
            if (observations.isEmpty()) return emptyList()
            return observations
                .groupBy { it.zoneKey }
                .map { (key, list) ->
                    val count = list.size
                    val weak = list.count { it.isWeakSignal }
                    val accs = list.mapNotNull { it.gpsAccuracyMeters }
                    val avgAcc = if (accs.isNotEmpty()) accs.average().toFloat() else null
                    val dbms = list.mapNotNull { it.signalDbm }
                    val avgDbm = if (dbms.isNotEmpty()) dbms.average().roundToInt() else null
                    val gpsOkPct = ((list.count { it.gpsFixOk }.toFloat() / count) * 100f).roundToInt()
                    val netOkPct = ((list.count { it.networkValidated }.toFloat() / count) * 100f).roundToInt()
                    val weakRatio = weak.toFloat() / count.toFloat()
                    val tier = when {
                        weakRatio >= 0.45f -> ZoneQualityTier.WEAK
                        weakRatio >= 0.15f -> ZoneQualityTier.MIXED
                        else -> ZoneQualityTier.GOOD
                    }
                    ZoneHeatCell(
                        zoneKey = key,
                        approximateLat = list.first().approximateLat,
                        approximateLon = list.first().approximateLon,
                        sampleCount = count,
                        weakCount = weak,
                        avgAccuracyMeters = avgAcc,
                        avgSignalDbm = avgDbm,
                        gpsFixRatePercent = gpsOkPct,
                        networkOkRatePercent = netOkPct,
                        qualityTier = tier,
                        lastObservedMs = list.maxOf { it.timestampMs }
                    )
                }
                .sortedByDescending { it.lastObservedMs }
        }
    }
}
