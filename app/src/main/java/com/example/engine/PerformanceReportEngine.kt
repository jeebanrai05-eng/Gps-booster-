package com.example.engine

import com.example.data.local.BatteryHistoryEntity
import com.example.data.local.SignalHandoverEntity
import com.example.data.local.WorkSessionEntity
import com.example.data.local.ZoneObservationEntity
import com.example.domain.model.PerformancePeriodSummary
import kotlin.math.roundToInt

class PerformanceReportEngine {

    fun buildSummary(
        periodLabel: String,
        sinceMs: Long,
        sessions: List<WorkSessionEntity>,
        zoneObservations: List<ZoneObservationEntity>,
        batteryHistory: List<BatteryHistoryEntity>,
        handovers: List<SignalHandoverEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): PerformancePeriodSummary {
        val periodSessions = sessions.filter { it.startTimeMs >= sinceMs }
        val periodZones = zoneObservations.filter { it.timestampMs >= sinceMs }
        val periodHandovers = handovers.filter { it.timestampMs >= sinceMs }
        val periodBattery = batteryHistory.filter { it.timestampMs >= sinceMs }

        val totalMinutes = periodSessions.sumOf {
            RiderWorkspaceEngine.computeEffectiveDurationMs(it, nowMs)
        } / 60_000L

        // GPS accuracy from sessions or zone observations
        val sessionAccSum = periodSessions.sumOf { it.totalAccuracySumMeters }
        val sessionAccCount = periodSessions.sumOf { it.accuracySampleCount }
        val avgGpsAcc: Float? = when {
            sessionAccCount > 0 -> (sessionAccSum / sessionAccCount).toFloat()
            periodZones.any { it.gpsAccuracyMeters != null } ->
                periodZones.mapNotNull { it.gpsAccuracyMeters }.average().toFloat()
            else -> null
        }

        val totalSessionSamples = periodSessions.sumOf { it.totalSamples }
        val fixedSessionSamples = periodSessions.sumOf { it.gpsFixedSamples }
        val gnssAvailabilityPct: Int? = when {
            totalSessionSamples > 0 -> ((fixedSessionSamples.toFloat() / totalSessionSamples) * 100f).roundToInt()
            periodZones.isNotEmpty() -> ((periodZones.count { it.gpsFixOk }.toFloat() / periodZones.size) * 100f).roundToInt()
            else -> null
        }

        val netOkSamples = periodSessions.sumOf { it.networkOkSamples }
        val netAvailabilityPct: Int? = when {
            totalSessionSamples > 0 -> ((netOkSamples.toFloat() / totalSessionSamples) * 100f).roundToInt()
            periodZones.isNotEmpty() -> ((periodZones.count { it.networkValidated }.toFloat() / periodZones.size) * 100f).roundToInt()
            else -> null
        }

        val networkInterruptions = periodSessions.sumOf { it.networkLossCount } +
            periodHandovers.count { !it.alertMessage.isNullOrBlank() }

        val overheatEvents = periodSessions.sumOf { it.overheatEventCount } +
            periodBattery.count { (it.temperatureC ?: 0f) >= 43.0f }

        val batteryConsumed = periodSessions.sumOf { s ->
            val start = s.startBatteryPct
            val end = s.endBatteryPct ?: -1
            if (start in 0..100 && end in 0..100 && start > end) (start - end) else 0
        }

        val readinessScore: Int? = if (gnssAvailabilityPct != null && netAvailabilityPct != null) {
            var score = ((gnssAvailabilityPct * 0.45f) + (netAvailabilityPct * 0.45f) + 10f).roundToInt()
            score -= (networkInterruptions * 4).coerceAtMost(20)
            score -= (overheatEvents * 5).coerceAtMost(20)
            if (avgGpsAcc != null && avgGpsAcc > 25f) score -= 8
            score.coerceIn(0, 100)
        } else null

        val problems = buildList {
            if (periodSessions.isEmpty() && periodZones.isEmpty()) {
                add("No Rider Work Sessions recorded for this period yet.")
            }
            if (avgGpsAcc != null && avgGpsAcc > 25f) {
                add("Elevated average GPS error (±${String.format("%.1f", avgGpsAcc)}m).")
            }
            if (gnssAvailabilityPct != null && gnssAvailabilityPct < 85) {
                add("GNSS fix availability dropped to $gnssAvailabilityPct%.")
            }
            if (networkInterruptions > 0) {
                add("$networkInterruptions network interruption/degradation events detected.")
            }
            if (overheatEvents > 0) {
                add("$overheatEvents thermal elevation events recorded.")
            }
        }

        val improvements = buildList {
            if (periodSessions.isEmpty()) {
                add("Start a Work Session in Rider Workspace to build device-readiness history.")
            }
            if (networkInterruptions > 0) {
                add("Review Dual SIM signal comparison and avoid weak-signal dead zones identified in Rider Zones.")
            }
            if (overheatEvents > 0) {
                add("Keep phone shaded on handlebar mount and avoid charging while exposed to direct sun.")
            }
            if (avgGpsAcc != null && avgGpsAcc > 20f) {
                add("Verify Precise Location + Unrestricted Battery Optimization for Rider Booster & Talabat Rider.")
            }
            if (isEmpty()) {
                add("Device telemetry performed consistently across recorded sessions.")
            }
        }

        return PerformancePeriodSummary(
            periodLabel = periodLabel,
            sessionsCount = periodSessions.size,
            totalRiderMinutes = totalMinutes,
            avgGpsAccuracyMeters = avgGpsAcc,
            gnssFixAvailabilityPercent = gnssAvailabilityPct,
            networkAvailabilityPercent = netAvailabilityPct,
            networkInterruptionCount = networkInterruptions,
            batteryConsumedPercent = batteryConsumed,
            overheatEventsCount = overheatEvents,
            readinessScore = readinessScore,
            detectedProblems = problems,
            suggestedImprovements = improvements
        )
    }
}
