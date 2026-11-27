package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RiderIntelligenceDao {
    // Work Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkSession(session: WorkSessionEntity): Long

    @Update
    suspend fun updateWorkSession(session: WorkSessionEntity)

    @Query("SELECT * FROM work_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY startTimeMs DESC LIMIT 1")
    fun observeActiveOrPausedSession(): Flow<WorkSessionEntity?>

    @Query("SELECT * FROM work_sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY startTimeMs DESC LIMIT 1")
    suspend fun getActiveOrPausedSessionOnce(): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions ORDER BY startTimeMs DESC LIMIT 60")
    fun observeWorkSessions(): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_sessions WHERE startTimeMs >= :sinceMs ORDER BY startTimeMs DESC")
    suspend fun getWorkSessionsSince(sinceMs: Long): List<WorkSessionEntity>

    @Query("SELECT * FROM work_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getWorkSessionById(sessionId: Long): WorkSessionEntity?

    // Session Timeline Events
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEvent(event: SessionTimelineEventEntity)

    @Query("SELECT * FROM session_timeline_events WHERE sessionId = :sessionId ORDER BY timestampMs DESC LIMIT 120")
    fun observeTimelineForSession(sessionId: Long): Flow<List<SessionTimelineEventEntity>>

    @Query("SELECT * FROM session_timeline_events WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getTimelineForSessionOnce(sessionId: Long): List<SessionTimelineEventEntity>

    @Query("SELECT * FROM session_timeline_events ORDER BY timestampMs DESC LIMIT 80")
    fun observeRecentTimelineEvents(): Flow<List<SessionTimelineEventEntity>>

    // Zone Observations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZoneObservation(observation: ZoneObservationEntity)

    @Query("SELECT * FROM zone_observations ORDER BY timestampMs DESC LIMIT 1000")
    fun observeZoneObservations(): Flow<List<ZoneObservationEntity>>

    @Query("SELECT * FROM zone_observations WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun getZoneObservationsSince(sinceMs: Long): List<ZoneObservationEntity>

    @Query("DELETE FROM zone_observations")
    suspend fun clearAllZoneObservations()

    // Battery History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatterySnapshot(snapshot: BatteryHistoryEntity)

    @Query("SELECT * FROM battery_history ORDER BY timestampMs DESC LIMIT 120")
    fun observeBatteryHistory(): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    suspend fun getBatteryHistorySince(sinceMs: Long): List<BatteryHistoryEntity>

    @Query("DELETE FROM battery_history")
    suspend fun clearBatteryHistory()

    // Signal Handovers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignalHandover(handover: SignalHandoverEntity)

    @Query("SELECT * FROM signal_handovers ORDER BY timestampMs DESC LIMIT 80")
    fun observeSignalHandovers(): Flow<List<SignalHandoverEntity>>

    @Query("SELECT * FROM signal_handovers WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun getSignalHandoversSince(sinceMs: Long): List<SignalHandoverEntity>

    @Query("DELETE FROM signal_handovers")
    suspend fun clearSignalHandovers()
}
