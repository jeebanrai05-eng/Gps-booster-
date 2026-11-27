package com.example.engine

import com.example.data.local.RiderIntelligenceRepository
import com.example.data.local.SessionTimelineEventEntity
import com.example.data.local.WorkSessionEntity
import com.example.domain.model.BatteryIntelligenceState
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderWorkspaceSummary
import com.example.domain.model.ThermalSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class RiderWorkspaceEngine(
    private val repository: RiderIntelligenceRepository,
    private val batteryEngine: BatteryIntelligenceEngine,
    private val deviceEngine: DeviceIntelligenceEngine,
    private val gpsEngine: GpsMonitorEngine,
    private val networkEngine: NetworkDiagnosticsEngine,
    private val dualSimEngine: DualSimAnalyzerEngine,
    private val zoneEngine: RiderZoneEngine,
    private val scope: CoroutineScope
) {
    val activeSession: StateFlow<WorkSessionEntity?> = repository.activeSessionFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val allSessions: StateFlow<List<WorkSessionEntity>> = repository.workSessionsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val recentTimelineEvents: StateFlow<List<SessionTimelineEventEntity>> =
        repository.recentTimelineEventsFlow.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _liveSessionDurationMs = MutableStateFlow(0L)
    val liveSessionDurationMs: StateFlow<Long> = _liveSessionDurationMs.asStateFlow()

    private val _workspaceSummary = MutableStateFlow(RiderWorkspaceSummary())
    val workspaceSummary: StateFlow<RiderWorkspaceSummary> = _workspaceSummary.asStateFlow()

    private var tickerJob: Job? = null
    private var prevGpsOk: Boolean? = null
    private var prevNetOk: Boolean? = null
    private var prevOverheat: Boolean = false
    private var lastSampleRecordedMs: Long = 0L

    init {
        scope.launch {
            combine(activeSession, allSessions) { active, sessions ->
                val startOfToday = startOfTodayMs()
                val todaySessions = sessions.filter { it.startTimeMs >= startOfToday }
                val now = System.currentTimeMillis()
                val totalTodayMs = todaySessions.sumOf { computeEffectiveDurationMs(it, now) }
                RiderWorkspaceSummary(
                    todayWorkingMinutes = (totalTodayMs / 60_000L),
                    totalSessionsToday = todaySessions.size,
                    totalHistoricalSessions = sessions.size
                ) to active
            }.collect { (summary, active) ->
                _workspaceSummary.value = summary
                _liveSessionDurationMs.value = active?.let { computeEffectiveDurationMs(it, System.currentTimeMillis()) } ?: 0L
                zoneEngine.updateActiveSessionFlag(active?.status == "ACTIVE")
                if (active?.status == "ACTIVE") {
                    ensureTickerRunning()
                } else if (active == null) {
                    tickerJob?.cancel()
                    tickerJob = null
                }
            }
        }
    }

    private fun ensureTickerRunning() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val current = activeSession.value
                if (current == null || current.status != "ACTIVE") {
                    if (current == null) break
                    delay(1000L)
                    continue
                }
                val now = System.currentTimeMillis()
                _liveSessionDurationMs.value = computeEffectiveDurationMs(current, now)
                if (now - lastSampleRecordedMs >= 10_000L) {
                    recordSessionTelemetrySample(
                        gps = gpsEngine.state.value,
                        network = networkEngine.state.value,
                        battery = batteryEngine.refreshSnapshot(persistHistory = true, activeSessionId = current.id),
                        device = deviceEngine.refreshSnapshot(),
                        dualSim = dualSimEngine.state.value
                    )
                }
                delay(1000L)
            }
        }
    }

    fun startWorkSession(notes: String = "") {
        scope.launch {
            val existing = repository.getActiveOrPausedSessionOnce()
            if (existing != null) {
                if (existing.status == "PAUSED") {
                    resumeWorkSession()
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            val battery = batteryEngine.refreshSnapshot(persistHistory = true, activeSessionId = null)
            prevGpsOk = null
            prevNetOk = null
            prevOverheat = false
            lastSampleRecordedMs = 0L

            val session = WorkSessionEntity(
                startTimeMs = now,
                status = "ACTIVE",
                activeDurationMs = 0L,
                lastResumedAtMs = now,
                startBatteryPct = battery.levelPercent,
                maxBatteryTempC = battery.temperatureC,
                notes = notes
            )
            val id = repository.insertWorkSession(session)
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = id,
                    timestampMs = now,
                    category = "SESSION",
                    severity = "INFO",
                    title = "Work Session Started",
                    detail = "Battery ${if (battery.levelPercent >= 0) "${battery.levelPercent}%" else "Unknown"}"
                )
            )
            zoneEngine.updateActiveSessionFlag(true)
            ensureTickerRunning()
        }
    }

    fun pauseWorkSession() {
        scope.launch {
            val current = repository.getActiveOrPausedSessionOnce() ?: return@launch
            if (current.status != "ACTIVE") return@launch
            val now = System.currentTimeMillis()
            val updatedDuration = computeEffectiveDurationMs(current, now)
            repository.updateWorkSession(
                current.copy(
                    status = "PAUSED",
                    activeDurationMs = updatedDuration,
                    lastResumedAtMs = null
                )
            )
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "SESSION",
                    severity = "INFO",
                    title = "Work Session Paused",
                    detail = "Active elapsed: ${updatedDuration / 60_000L} min"
                )
            )
            zoneEngine.updateActiveSessionFlag(false)
        }
    }

    fun resumeWorkSession() {
        scope.launch {
            val current = repository.getActiveOrPausedSessionOnce() ?: return@launch
            if (current.status != "PAUSED") return@launch
            val now = System.currentTimeMillis()
            repository.updateWorkSession(
                current.copy(
                    status = "ACTIVE",
                    lastResumedAtMs = now
                )
            )
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "SESSION",
                    severity = "INFO",
                    title = "Work Session Resumed",
                    detail = "Resumed local telemetry & zone logging"
                )
            )
            zoneEngine.updateActiveSessionFlag(true)
            ensureTickerRunning()
        }
    }

    fun endWorkSession() {
        scope.launch {
            val current = repository.getActiveOrPausedSessionOnce() ?: return@launch
            val now = System.currentTimeMillis()
            val finalDuration = computeEffectiveDurationMs(current, now)
            val battery = batteryEngine.refreshSnapshot(persistHistory = true, activeSessionId = current.id)
            val maxTemp = listOfNotNull(current.maxBatteryTempC, battery.temperatureC).maxOrNull()

            repository.updateWorkSession(
                current.copy(
                    endTimeMs = now,
                    status = "ENDED",
                    activeDurationMs = finalDuration,
                    lastResumedAtMs = null,
                    endBatteryPct = battery.levelPercent.takeIf { it >= 0 },
                    maxBatteryTempC = maxTemp
                )
            )
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "SESSION",
                    severity = "INFO",
                    title = "Work Session Ended",
                    detail = "Total active: ${finalDuration / 60_000L}m • End Battery: ${if (battery.levelPercent >= 0) "${battery.levelPercent}%" else "Unknown"}"
                )
            )
            zoneEngine.updateActiveSessionFlag(false)
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    suspend fun recordSessionTelemetrySample(
        gps: GpsTelemetryState,
        network: NetworkDiagnosticsState,
        battery: BatteryIntelligenceState,
        device: DeviceIntelligenceState,
        dualSim: com.example.domain.model.DualSimState
    ) {
        val current = repository.getActiveOrPausedSessionOnce() ?: return
        if (current.status != "ACTIVE") return

        val now = System.currentTimeMillis()
        lastSampleRecordedMs = now

        val gpsOk = gps.fixStatus == GpsFixStatus.FIX_ACQUIRED
        val netOk = network.isNetworkValidated
        val isOverheat = device.thermalSeverity == ThermalSeverity.SEVERE ||
            device.thermalSeverity == ThermalSeverity.CRITICAL ||
            (battery.temperatureC != null && battery.temperatureC >= 43.0f)

        var networkLosses = current.networkLossCount
        var overheatEvents = current.overheatEventCount

        if (prevGpsOk == true && !gpsOk) {
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "GPS",
                    severity = "WARNING",
                    title = "GPS Fix Lost",
                    detail = "Status: ${gps.fixStatus.name} (${gps.satellitesTotal} visible)"
                )
            )
        } else if (prevGpsOk == false && gpsOk) {
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "GPS",
                    severity = "RECOVERY",
                    title = "GPS Fix Restored",
                    detail = "Accuracy: ±${gps.horizontalAccuracyMeters?.toInt() ?: 0}m (${gps.satellitesUsedInFix} sats)"
                )
            )
        }
        prevGpsOk = gpsOk

        if (prevNetOk == true && !netOk) {
            networkLosses += 1
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "NETWORK",
                    severity = "CRITICAL",
                    title = "Network Interruption",
                    detail = if (!network.isNetworkAvailable) "Device offline" else "${network.transportType.label} unvalidated"
                )
            )
        } else if (prevNetOk == false && netOk) {
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "NETWORK",
                    severity = "RECOVERY",
                    title = "Network Restored",
                    detail = "${network.transportType.label} internet validated"
                )
            )
        }
        prevNetOk = netOk

        if (!prevOverheat && isOverheat) {
            overheatEvents += 1
            repository.insertTimelineEvent(
                SessionTimelineEventEntity(
                    sessionId = current.id,
                    timestampMs = now,
                    category = "THERMAL",
                    severity = "WARNING",
                    title = "Device Overheating Event",
                    detail = "${device.thermalStatusLabel} • Battery ${battery.temperatureC?.let { String.format("%.1f°C", it) } ?: "N/A"}"
                )
            )
        }
        prevOverheat = isOverheat

        val acc = gps.horizontalAccuracyMeters
        val nextAccSum = current.totalAccuracySumMeters + (acc?.toDouble() ?: 0.0)
        val nextAccCount = current.accuracySampleCount + if (acc != null) 1 else 0
        val nextMaxTemp = listOfNotNull(current.maxBatteryTempC, battery.temperatureC).maxOrNull()

        repository.updateWorkSession(
            current.copy(
                totalSamples = current.totalSamples + 1,
                gpsFixedSamples = current.gpsFixedSamples + if (gpsOk) 1 else 0,
                totalAccuracySumMeters = nextAccSum,
                accuracySampleCount = nextAccCount,
                networkOkSamples = current.networkOkSamples + if (netOk) 1 else 0,
                networkLossCount = networkLosses,
                overheatEventCount = overheatEvents,
                endBatteryPct = battery.levelPercent.takeIf { it >= 0 },
                maxBatteryTempC = nextMaxTemp
            )
        )

        zoneEngine.recordObservationIfAllowed(
            isSessionOrRiderActive = true,
            activeSessionId = current.id,
            gps = gps,
            network = network,
            dualSim = dualSim
        )
    }

    companion object {
        fun computeEffectiveDurationMs(session: WorkSessionEntity, nowMs: Long): Long {
            return if (session.status == "ACTIVE" && session.lastResumedAtMs != null) {
                session.activeDurationMs + (nowMs - session.lastResumedAtMs).coerceAtLeast(0L)
            } else {
                session.activeDurationMs
            }
        }

        fun startOfTodayMs(nowMs: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
}
