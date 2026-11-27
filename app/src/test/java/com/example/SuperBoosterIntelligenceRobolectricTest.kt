package com.example

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.BatteryManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.BatteryHistoryEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.RiderIntelligenceRepository
import com.example.data.local.SignalHandoverEntity
import com.example.data.local.WorkSessionEntity
import com.example.data.local.ZoneObservationEntity
import com.example.domain.model.BatteryIntelligenceState
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.DualSimState
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.NetworkTransportType
import com.example.domain.model.RiderZonesState
import com.example.domain.model.SimSlotTelemetry
import com.example.domain.model.SystemCheckState
import com.example.domain.model.ThermalSeverity
import com.example.domain.model.TroubleSeverity
import com.example.engine.BatteryIntelligenceEngine
import com.example.engine.DeviceIntelligenceEngine
import com.example.engine.DualSimAnalyzerEngine
import com.example.engine.GpsMonitorEngine
import com.example.engine.NetworkDiagnosticsEngine
import com.example.engine.PerformanceReportEngine
import com.example.engine.RiderWorkspaceEngine
import com.example.engine.RiderZoneEngine
import com.example.engine.SystemOptimizerEngine
import com.example.engine.TalabatDetectorEngine
import com.example.engine.TroubleshootingEngine
import com.example.util.ReportExportHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class SuperBoosterIntelligenceRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun talabatDetector_recognizesDeliveryHeroAndMultilineLabel() = runBlocking {
        withTimeout(10_000L) {
            assertTrue(TalabatDetectorEngine.matchesTalabatRiderLabel("talabat\nrider", "com.deliveryhero.pandora.talabat.rider"))
            assertFalse(TalabatDetectorEngine.matchesTalabatRiderLabel("talabat", "com.talabat"))

            val context = ApplicationProvider.getApplicationContext<Context>()
            val shadowPm = Shadows.shadowOf(context.packageManager)
            val appInfo = ApplicationInfo().apply {
                packageName = "com.deliveryhero.pandora.talabat.rider"
                nonLocalizedLabel = "Talabat Rider"
                enabled = true
            }
            val actInfo = ActivityInfo().apply {
                packageName = "com.deliveryhero.pandora.talabat.rider"
                name = "com.deliveryhero.pandora.talabat.rider.MainActivity"
                applicationInfo = appInfo
                exported = true
            }
            val pkgInfo = PackageInfo().apply {
                packageName = "com.deliveryhero.pandora.talabat.rider"
                versionName = "4.88.0"
                applicationInfo = appInfo
                activities = arrayOf(actInfo)
            }
            shadowPm.installPackage(pkgInfo)
            val ri = ResolveInfo().apply {
                activityInfo = actInfo
                nonLocalizedLabel = "Talabat Rider"
            }
            shadowPm.addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                ri
            )
            shadowPm.addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.deliveryhero.pandora.talabat.rider"),
                ri
            )

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val detector = TalabatDetectorEngine(context, PreferencesRepository(context), scope)
            val result = detector.refreshDetectionNow()
            assertTrue(result.isInstalled)
            assertEquals("com.deliveryhero.pandora.talabat.rider", result.detectedPackageName)
        }
    }

    @Test
    fun deviceIntelligenceEngine_computesRamStorageAndHealthScore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = DeviceIntelligenceEngine(context, scope)
        val snap = engine.refreshSnapshot()
        assertTrue(snap.totalRamMb >= 0L)
        assertTrue(snap.storageTotalGb >= 0f)
        assertTrue(snap.performanceHealthScore in 0..100)

        val degradedScore = DeviceIntelligenceEngine.computeHealthScore(
            ramUsagePct = 92,
            isLowMemory = true,
            storageUsedPct = 96,
            thermalSeverity = ThermalSeverity.CRITICAL,
            cpuTempC = 47f
        )
        assertTrue(degradedScore < 40)
    }

    @Test
    fun dualSimAnalyzer_handlesPermissionAndComparisonLogic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RiderIntelligenceRepository(db.riderIntelligenceDao())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = DualSimAnalyzerEngine(context, repo, scope)

        val shadowApp = Shadows.shadowOf(context.applicationContext as Application)
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
        shadowApp.denyPermissions(Manifest.permission.READ_PHONE_STATE)

        val deniedState = engine.refreshSnapshot()
        assertFalse(deniedState.hasPhoneStatePermission)
        assertTrue(deniedState.comparisonSummary.contains("Permission", ignoreCase = true))

        shadowApp.grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val grantedState = engine.refreshSnapshot()
        assertTrue(grantedState.hasPhoneStatePermission)

        val sim1 = SimSlotTelemetry(0, 1, "SIM 1", "Zain KW", "Zain", true, true, true, true, false, "5G NR", -82, 4, "Excellent", true)
        val sim2 = SimSlotTelemetry(1, 2, "SIM 2", "Ooredoo", "Ooredoo", true, false, false, false, false, "4G LTE", -105, 2, "Fair", true)
        val summary = DualSimAnalyzerEngine.buildComparisonSummary(listOf(sim1, sim2))
        assertTrue(summary.contains("SIM 1"))
        assertTrue(summary.contains("-82 dBm"))

        val weakSim = sim1.copy(signalStrengthDbm = -115, signalLevel = 1)
        assertNotNull(DualSimAnalyzerEngine.evaluateDegradationAlert(listOf(weakSim)))
        db.close()
    }

    @Test
    fun batteryIntelligenceEngine_readsStickyIntentAndEstimatesDrain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RiderIntelligenceRepository(db.riderIntelligenceDao())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val stickyBattery = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 18)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
            putExtra(BatteryManager.EXTRA_TEMPERATURE, 455) // 45.5 C
            putExtra(BatteryManager.EXTRA_VOLTAGE, 3850)
        }
        context.sendStickyBroadcast(stickyBattery)

        val engine = BatteryIntelligenceEngine(context, repo, scope)
        val snap = engine.refreshSnapshot(persistHistory = false)
        assertEquals(18, snap.levelPercent)
        assertEquals(45.5f, snap.temperatureC ?: 0f, 0.2f)
        assertEquals(3850, snap.voltageMv)
        assertNotNull(snap.lowBatteryAlert)
        assertNotNull(snap.thermalAlert)

        val (rate, mins, label) = BatteryIntelligenceEngine.estimateDrainAndRuntime(
            levelPct = 50,
            isCharging = false,
            samples = listOf(0L to 60, 3_600_000L to 50)
        )
        assertEquals(10f, rate ?: 0f, 0.2f)
        assertEquals(300, mins)
        assertTrue(label.contains("est."))
        db.close()
    }

    @Test
    fun roomWorkspaceZonesAndReports_persistAggregateAndExport() = runBlocking {
        withTimeout(15_000L) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val dao = db.riderIntelligenceDao()
            val repo = RiderIntelligenceRepository(dao)
            val prefs = PreferencesRepository(context)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

            val zoneEngine = RiderZoneEngine(repo, prefs, scope)
            val deviceEngine = DeviceIntelligenceEngine(context, scope)
            val batteryEngine = BatteryIntelligenceEngine(context, repo, scope)
            val gpsEngine = GpsMonitorEngine(context, scope)
            val netEngine = NetworkDiagnosticsEngine(context, scope)
            val simEngine = DualSimAnalyzerEngine(context, repo, scope)
            val workspaceEngine = RiderWorkspaceEngine(repo, batteryEngine, deviceEngine, gpsEngine, netEngine, simEngine, zoneEngine, scope)

            // Ensure Zone Engine ignores recording when session is inactive
            val gpsSample = GpsTelemetryState(
                latitude = 29.3759,
                longitude = 47.9774,
                horizontalAccuracyMeters = 8f,
                satellitesUsedInFix = 9,
                fixStatus = GpsFixStatus.FIX_ACQUIRED,
                isLocationAvailable = true
            )
            val netSample = NetworkDiagnosticsState(isNetworkAvailable = true, isNetworkValidated = true, transportType = NetworkTransportType.CELLULAR)
            zoneEngine.recordObservationIfAllowed(false, null, gpsSample, netSample, DualSimState())
            assertEquals(0, dao.observeZoneObservations().first().size)

            // Record weak zone samples when active
            val weakGps = gpsSample.copy(fixStatus = GpsFixStatus.STALE_FIX, horizontalAccuracyMeters = 55f)
            val (_, _, zoneKey) = RiderZoneEngine.quantizeCoordinates(29.3759, 47.9774)
            dao.insertZoneObservation(
                ZoneObservationEntity(1, 1L, 1000L, zoneKey, 29.376, 47.978, 55f, 2, false, true, false, "Cellular", -114, true)
            )
            dao.insertZoneObservation(
                ZoneObservationEntity(2, 1L, 2000L, zoneKey, 29.376, 47.978, 48f, 3, false, true, false, "Cellular", -112, true)
            )
            val cells = RiderZoneEngine.aggregateObservations(dao.observeZoneObservations().first())
            assertEquals(1, cells.size)
            assertEquals(2, cells.first().weakCount)

            // Work session lifecycle & reports
            val now = System.currentTimeMillis()
            val sessionId = dao.insertWorkSession(
                WorkSessionEntity(
                    startTimeMs = now - 3_600_000L,
                    endTimeMs = now,
                    status = "ENDED",
                    activeDurationMs = 3_600_000L,
                    totalSamples = 10,
                    gpsFixedSamples = 9,
                    totalAccuracySumMeters = 100.0,
                    accuracySampleCount = 10,
                    networkOkSamples = 8,
                    networkLossCount = 2,
                    overheatEventCount = 1,
                    startBatteryPct = 90,
                    endBatteryPct = 70
                )
            )
            val sessions = dao.observeWorkSessions().first()
            assertTrue(sessions.any { it.id == sessionId })

            val reportEngine = PerformanceReportEngine()
            val summary = reportEngine.buildSummary(
                periodLabel = "Today",
                sinceMs = now - 7_200_000L,
                sessions = sessions,
                zoneObservations = emptyList(),
                batteryHistory = listOf(BatteryHistoryEntity(1, sessionId, now, 70, 44f, false, "On Battery", 3900, null, false)),
                handovers = listOf(SignalHandoverEntity(1, now, 0, 1, "Zain", "5G NR", "3G", -112, 1, "Downgrade")),
                nowMs = now
            )
            assertEquals(1, summary.sessionsCount)
            assertEquals(60L, summary.totalRiderMinutes)
            assertEquals(90, summary.gnssFixAvailabilityPercent)
            assertEquals(20, summary.batteryConsumedPercent)
            assertNotNull(summary.readinessScore)

            // CSV & PDF generation
            val csv = ReportExportHelper.buildSessionCsv(sessions, emptyList(), now)
            assertTrue(csv.contains("session_id,start_time"))
            assertTrue(csv.contains("ENDED"))

            val pdfOut = ByteArrayOutputStream()
            ReportExportHelper.writeSessionPdfToStream(pdfOut, sessions, emptyList(), now)
            assertTrue(pdfOut.size() > 200)

            // Clear zone history
            zoneEngine.deleteAllLocationHistory()
            kotlinx.coroutines.delay(150L)
            assertEquals(0, dao.observeZoneObservations().first().size)
            db.close()
        }
    }

    @Test
    fun troubleshootingEngine_detectsIssuesAndNominalState() {
        val engine = TroubleshootingEngine()
        val critical = engine.analyze(
            gps = GpsTelemetryState(fixStatus = GpsFixStatus.PERMISSION_DENIED),
            network = NetworkDiagnosticsState(isNetworkAvailable = false, isNetworkValidated = false),
            system = SystemCheckState(hasFineLocationPermission = false, isLocationServicesEnabled = false, isPowerSaveMode = true),
            device = DeviceIntelligenceState(ramUsagePercent = 93, isLowMemory = true, thermalSeverity = ThermalSeverity.CRITICAL, thermalStatusLabel = "Critical"),
            battery = BatteryIntelligenceState(levelPercent = 9, isCharging = false, temperatureC = 45f),
            dualSim = DualSimState(signalDegradationAlert = "Weak signal"),
            zones = RiderZonesState(),
            recentSessions = emptyList()
        )
        assertTrue(critical.any { it.severity == TroubleSeverity.CRITICAL })

        val nominal = engine.analyze(
            gps = GpsTelemetryState(fixStatus = GpsFixStatus.FIX_ACQUIRED, horizontalAccuracyMeters = 6f),
            network = NetworkDiagnosticsState(isNetworkAvailable = true, isNetworkValidated = true),
            system = SystemCheckState(
                hasFineLocationPermission = true,
                isLocationServicesEnabled = true,
                isGpsProviderEnabled = true,
                isBatteryOptimizationIgnored = true,
                isPowerSaveMode = false,
                isBackgroundRestricted = false
            ),
            device = DeviceIntelligenceState(ramUsagePercent = 45, storageUsedPercent = 40, thermalSeverity = ThermalSeverity.NOMINAL, thermalStatusLabel = "Nominal"),
            battery = BatteryIntelligenceState(levelPercent = 82, isCharging = false, temperatureC = 33f),
            dualSim = DualSimState(),
            zones = RiderZonesState(),
            recentSessions = emptyList()
        )
        assertEquals(1, nominal.size)
        assertEquals(TroubleSeverity.OPTIMAL, nominal.first().severity)
    }

    @Test
    fun superBoosterHubNavigationFlowsAcrossAllNewScreens() {
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("super_booster_hub_card"))
        composeTestRule.onNodeWithTag("super_booster_hub_card").assertIsDisplayed()

        // 1. Intelligence
        composeTestRule.onNodeWithTag("hub_intelligence").performClick()
        waitForText("SMART RIDER INTELLIGENCE")

        // 2. Dual SIM via quick chip
        composeTestRule.onNodeWithTag("chip_dual_sim").performClick()
        waitForText("DUAL SIM INTELLIGENCE")

        // 3. Rider Zones
        composeTestRule.onNodeWithTag("chip_rider_zones").performClick()
        waitForText("RIDER ZONE INTELLIGENCE")

        // 4. Battery Health
        composeTestRule.onNodeWithTag("chip_battery_health").performClick()
        waitForText("SMART BATTERY INTELLIGENCE")

        // 5. Workspace
        composeTestRule.onNodeWithTag("chip_rider_workspace").performClick()
        waitForText("SMART RIDER WORKSPACE")

        // 6. Performance Reports
        composeTestRule.onNodeWithTag("chip_performance_reports").performClick()
        waitForText("RIDER PERFORMANCE REPORTS")

        // Back returns cleanly
        composeTestRule.onNodeWithTag("intel_back_button").performClick()
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }
}
