package com.example

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.DiagnosticsHistoryRepository
import com.example.data.local.PreferencesRepository
import com.example.data.local.RiderIntelligenceRepository
import com.example.engine.BatteryIntelligenceEngine
import com.example.engine.CompassSensorEngine
import com.example.engine.DeviceIntelligenceEngine
import com.example.engine.DualSimAnalyzerEngine
import com.example.engine.GpsMonitorEngine
import com.example.engine.NetworkDiagnosticsEngine
import com.example.engine.OneTapDiagnosticRunner
import com.example.engine.PerformanceReportEngine
import com.example.engine.RiderModeController
import com.example.engine.RiderWorkspaceEngine
import com.example.engine.RiderZoneEngine
import com.example.engine.SmartGpsRecoveryEngine
import com.example.engine.SystemOptimizerEngine
import com.example.engine.TalabatDetectorEngine
import com.example.engine.TroubleshootingEngine
import com.example.update.GitHubUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val historyRepository: DiagnosticsHistoryRepository by lazy {
        DiagnosticsHistoryRepository(database.diagnosticDao())
    }
    val riderIntelligenceRepository: RiderIntelligenceRepository by lazy {
        RiderIntelligenceRepository(database.riderIntelligenceDao())
    }
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(appContext)
    }

    val gpsEngine: GpsMonitorEngine by lazy { GpsMonitorEngine(appContext, appScope) }
    val networkEngine: NetworkDiagnosticsEngine by lazy { NetworkDiagnosticsEngine(appContext, appScope) }
    val systemEngine: SystemOptimizerEngine by lazy { SystemOptimizerEngine(appContext) }
    val compassEngine: CompassSensorEngine by lazy { CompassSensorEngine(appContext) }
    val smartGpsRecoveryEngine: SmartGpsRecoveryEngine by lazy {
        SmartGpsRecoveryEngine(
            gpsEngine = gpsEngine,
            networkEngine = networkEngine,
            systemEngine = systemEngine
        )
    }
    val talabatDetectorEngine: TalabatDetectorEngine by lazy {
        TalabatDetectorEngine(
            context = appContext,
            preferencesRepository = preferencesRepository,
            scope = appScope
        )
    }
    val gitHubUpdateManager: GitHubUpdateManager by lazy {
        GitHubUpdateManager(
            context = appContext,
            preferencesRepository = preferencesRepository,
            scope = appScope
        )
    }
    val riderModeController: RiderModeController by lazy {
        RiderModeController(
            context = appContext,
            scope = appScope,
            gpsEngine = gpsEngine,
            networkEngine = networkEngine,
            systemEngine = systemEngine,
            preferencesRepository = preferencesRepository
        )
    }
    val diagnosticRunner: OneTapDiagnosticRunner by lazy {
        OneTapDiagnosticRunner(
            gpsEngine = gpsEngine,
            networkEngine = networkEngine,
            systemEngine = systemEngine,
            historyRepository = historyRepository
        )
    }

    // Advanced Rider Intelligence Engines (v1.1.2)
    val deviceIntelligenceEngine: DeviceIntelligenceEngine by lazy {
        DeviceIntelligenceEngine(appContext, appScope)
    }
    val dualSimAnalyzerEngine: DualSimAnalyzerEngine by lazy {
        DualSimAnalyzerEngine(appContext, riderIntelligenceRepository, appScope)
    }
    val batteryIntelligenceEngine: BatteryIntelligenceEngine by lazy {
        BatteryIntelligenceEngine(appContext, riderIntelligenceRepository, appScope)
    }
    val riderZoneEngine: RiderZoneEngine by lazy {
        RiderZoneEngine(riderIntelligenceRepository, preferencesRepository, appScope)
    }
    val riderWorkspaceEngine: RiderWorkspaceEngine by lazy {
        RiderWorkspaceEngine(
            repository = riderIntelligenceRepository,
            batteryEngine = batteryIntelligenceEngine,
            deviceEngine = deviceIntelligenceEngine,
            gpsEngine = gpsEngine,
            networkEngine = networkEngine,
            dualSimEngine = dualSimAnalyzerEngine,
            zoneEngine = riderZoneEngine,
            scope = appScope
        )
    }
    val troubleshootingEngine: TroubleshootingEngine by lazy { TroubleshootingEngine() }
    val performanceReportEngine: PerformanceReportEngine by lazy { PerformanceReportEngine() }
}
