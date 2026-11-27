package com.example.data.local

import kotlinx.coroutines.flow.Flow

class RiderIntelligenceRepository(private val dao: RiderIntelligenceDao) {

    val activeSessionFlow: Flow<WorkSessionEntity?> = dao.observeActiveOrPausedSession()
    val workSessionsFlow: Flow<List<WorkSessionEntity>> = dao.observeWorkSessions()
    val recentTimelineEventsFlow: Flow<List<SessionTimelineEventEntity>> = dao.observeRecentTimelineEvents()
    val zoneObservationsFlow: Flow<List<ZoneObservationEntity>> = dao.observeZoneObservations()
    val batteryHistoryFlow: Flow<List<BatteryHistoryEntity>> = dao.observeBatteryHistory()
    val signalHandoversFlow: Flow<List<SignalHandoverEntity>> = dao.observeSignalHandovers()

    fun observeTimelineForSession(sessionId: Long): Flow<List<SessionTimelineEventEntity>> =
        dao.observeTimelineForSession(sessionId)

    suspend fun insertWorkSession(session: WorkSessionEntity): Long = dao.insertWorkSession(session)

    suspend fun updateWorkSession(session: WorkSessionEntity) = dao.updateWorkSession(session)

    suspend fun getActiveOrPausedSessionOnce(): WorkSessionEntity? = dao.getActiveOrPausedSessionOnce()

    suspend fun getWorkSessionsSince(sinceMs: Long): List<WorkSessionEntity> =
        dao.getWorkSessionsSince(sinceMs)

    suspend fun getWorkSessionById(sessionId: Long): WorkSessionEntity? =
        dao.getWorkSessionById(sessionId)

    suspend fun insertTimelineEvent(event: SessionTimelineEventEntity) = dao.insertTimelineEvent(event)

    suspend fun getTimelineForSessionOnce(sessionId: Long): List<SessionTimelineEventEntity> =
        dao.getTimelineForSessionOnce(sessionId)

    suspend fun insertZoneObservation(observation: ZoneObservationEntity) =
        dao.insertZoneObservation(observation)

    suspend fun getZoneObservationsSince(sinceMs: Long): List<ZoneObservationEntity> =
        dao.getZoneObservationsSince(sinceMs)

    suspend fun clearAllZoneObservations() = dao.clearAllZoneObservations()

    suspend fun insertBatterySnapshot(snapshot: BatteryHistoryEntity) =
        dao.insertBatterySnapshot(snapshot)

    suspend fun getBatteryHistorySince(sinceMs: Long): List<BatteryHistoryEntity> =
        dao.getBatteryHistorySince(sinceMs)

    suspend fun insertSignalHandover(handover: SignalHandoverEntity) =
        dao.insertSignalHandover(handover)

    suspend fun getSignalHandoversSince(sinceMs: Long): List<SignalHandoverEntity> =
        dao.getSignalHandoversSince(sinceMs)

    suspend fun clearSignalHandovers() = dao.clearSignalHandovers()
}
