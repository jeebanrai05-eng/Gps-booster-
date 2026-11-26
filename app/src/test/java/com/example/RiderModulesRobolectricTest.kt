package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesRepository
import com.example.domain.model.ApkDownloadStatus
import com.example.domain.model.DetectedGpsProblem
import com.example.domain.model.GitHubReleaseManifest
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.LocationSourceType
import com.example.domain.model.RecoveryOutcome
import com.example.domain.model.SystemCheckState
import com.example.domain.model.TalabatLaunchOutcome
import com.example.domain.model.UpdateCheckStatus
import com.example.engine.GpsMonitorEngine
import com.example.engine.NetworkDiagnosticsEngine
import com.example.engine.SmartGpsRecoveryEngine
import com.example.engine.SystemOptimizerEngine
import com.example.engine.TalabatDetectorEngine
import com.example.update.GitHubUpdateManager
import com.example.update.VersionManifestParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RiderModulesRobolectricTest {

    @Test
    fun versionManifestParser_acceptsValidAndRejectsInvalidManifests() {
        val validSha = "a".repeat(64)
        val raw = """
            {
              "versionCode": 2,
              "versionName": "1.01",
              "releaseTitle": "Rider Booster v1.01",
              "releaseNotes": "Smart GPS Recovery & Auto Update",
              "apkUrl": "https://github.com/org/repo/releases/download/v1.01/app.apk",
              "sha256": "$validSha",
              "mandatory": false,
              "minSupportedVersionCode": 1,
              "apkSizeBytes": 12582912,
              "releaseDate": "2026-11-26"
            }
        """.trimIndent()
        val parsed = VersionManifestParser.parseAndValidate(raw)
        assertTrue(parsed.isSuccess)
        val m = parsed.getOrThrow()
        assertEquals(2, m.versionCode)
        assertEquals("1.01", m.versionName)
        assertEquals(validSha, m.sha256)
        assertEquals(12582912L, m.apkSizeBytes)

        val httpUrlJson = """
            {
              "versionCode": 2,
              "versionName": "1.01",
              "releaseNotes": "Test",
              "apkUrl": "http://example.com/app.apk",
              "sha256": "$validSha"
            }
        """.trimIndent()
        assertTrue(VersionManifestParser.parseAndValidate(httpUrlJson).isFailure)

        val badShaJson = """
            {
              "versionCode": 2,
              "versionName": "1.01",
              "releaseNotes": "Test",
              "apkUrl": "https://github.com/org/repo/releases/download/v1.01/app.apk",
              "sha256": "not-a-valid-sha"
            }
        """.trimIndent()
        assertTrue(VersionManifestParser.parseAndValidate(badShaJson).isFailure)
        assertTrue(VersionManifestParser.parseAndValidate("{broken json").isFailure)
    }

    @Test
    fun smartGpsRecoveryEngine_detectsAllProblemCategoriesAndRuns11Steps() = runBlocking {
        withTimeout(15_000L) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gpsEngine = GpsMonitorEngine(context, scope)
        val networkEngine = NetworkDiagnosticsEngine(context, scope)
        val systemEngine = SystemOptimizerEngine(context)
        val recoveryEngine = SmartGpsRecoveryEngine(gpsEngine, networkEngine, systemEngine)

        val degradedGps = GpsTelemetryState(
            hasFineLocationPermission = false,
            isLocationServicesEnabled = false,
            isGpsProviderEnabled = false,
            gnssStatusSupported = true,
            satellitesTotal = 2,
            satellitesUsedInFix = 1,
            averageCn0DbHz = 17.5f,
            horizontalAccuracyMeters = 62f,
            locationAgeSeconds = 35L,
            updateDelayDetected = true,
            locationSourceType = LocationSourceType.NETWORK,
            fixStatus = GpsFixStatus.STALE_FIX,
            isMonitoringActive = true,
            isLocationAvailable = false
        )
        val degradedSys = SystemCheckState(
            isLocationServicesEnabled = false,
            isGpsProviderEnabled = false,
            hasFineLocationPermission = false,
            hasBackgroundLocationPermission = false,
            isBatteryOptimizationIgnored = false,
            isPowerSaveMode = true,
            isBackgroundRestricted = true
        )

        val problems = recoveryEngine.detectCurrentProblems(degradedGps, degradedSys)
        assertTrue(DetectedGpsProblem.LOCATION_PERMISSION_DENIED in problems)
        assertTrue(DetectedGpsProblem.GPS_DISABLED in problems)
        assertTrue(DetectedGpsProblem.NO_SATELLITE_FIX in problems)
        assertTrue(DetectedGpsProblem.LOW_SATELLITE_VISIBILITY in problems)
        assertTrue(DetectedGpsProblem.WEAK_GNSS_SIGNAL in problems)
        assertTrue(DetectedGpsProblem.STALE_LOCATION in problems)
        assertTrue(DetectedGpsProblem.POOR_LOCATION_ACCURACY in problems)
        assertTrue(DetectedGpsProblem.LOCATION_UPDATE_TIMEOUT in problems)
        assertTrue(DetectedGpsProblem.BACKGROUND_LOCATION_RESTRICTED in problems)
        assertTrue(DetectedGpsProblem.BATTERY_RESTRICTIONS_ACTIVE in problems)

        recoveryEngine.runRecoveryWorkflow()
        val finalState = recoveryEngine.state.value
        assertEquals(11, finalState.steps.size)
        assertEquals(1f, finalState.progressFraction, 0.01f)
        assertEquals(RecoveryOutcome.FAILED, finalState.outcome)
        }
    }

    @Test
    fun talabatDetectorEngine_ignoresCustomerAppAndDetectsAndLaunchesRiderApp() = runBlocking {
        withTimeout(15_000L) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val prefs = PreferencesRepository(context)
        val shadowPm = Shadows.shadowOf(context.packageManager)

        // Install regular consumer Talabat app ONLY — must NOT auto-select
        installFakeLaunchablePackage(
            shadowPm = shadowPm,
            packageName = "com.talabat",
            appLabel = "talabat",
            activityName = "com.talabat.MainActivity",
            versionName = "12.1.0",
            enabled = true
        )

        val detector = TalabatDetectorEngine(context, prefs, scope)
        val firstScan = detector.refreshDetectionNow()
        assertFalse(firstScan.isInstalled)
        assertTrue(firstScan.matchingApps.any { it.packageName == "com.talabat" })

        // Now install Talabat Rider app
        installFakeLaunchablePackage(
            shadowPm = shadowPm,
            packageName = "com.talabat.rider",
            appLabel = "Talabat Rider",
            activityName = "com.talabat.rider.HomeActivity",
            versionName = "4.42.1",
            enabled = true
        )

        val secondScan = detector.refreshDetectionNow()
        assertTrue(secondScan.isInstalled)
        assertEquals("com.talabat.rider", secondScan.detectedPackageName)
        assertEquals("Talabat Rider", secondScan.detectedAppName)
        assertTrue(secondScan.launchActivityAvailable)
        assertEquals("Verified Package ID", secondScan.detectionMethod)

        val launchOutcome = detector.launchTalabatRider()
        assertTrue(launchOutcome is TalabatLaunchOutcome.Launched)
        }
    }

    @Test
    fun gitHubUpdateManager_handlesUpdatesDowngradesRateLimitsAndSha256Mismatch() = runBlocking {
        withTimeout(15_000L) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val prefs = PreferencesRepository(context)
        ensureOnlineNetwork(context)

        val validSha = GitHubUpdateManager.sha256Hex("valid-apk-bytes".toByteArray())
        val tamperedBytes = "tampered-apk-bytes".toByteArray()

        var mode = "UPDATE_AVAILABLE"
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.endsWith("version.json") && mode == "RATE_LIMIT" -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(429)
                            .message("Too Many Requests")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.endsWith("version.json") && mode == "DOWNGRADE" -> {
                        val json = """
                            {
                              "versionCode": 1,
                              "versionName": "1.0",
                              "releaseTitle": "Old",
                              "releaseNotes": "Older",
                              "apkUrl": "https://example.com/old.apk",
                              "sha256": "$validSha"
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(json.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.endsWith("version.json") -> {
                        val json = """
                            {
                              "versionCode": 5,
                              "versionName": "2.0",
                              "releaseTitle": "Rider Booster v2.0",
                              "releaseNotes": "Improved GNSS recovery",
                              "apkUrl": "https://example.com/update.apk",
                              "sha256": "$validSha",
                              "mandatory": false,
                              "minSupportedVersionCode": 1
                            }
                        """.trimIndent()
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(json.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(tamperedBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                            .build()
                    }
                }
            })
            .build()

        val manager = GitHubUpdateManager(
            context = context,
            preferencesRepository = prefs,
            scope = scope,
            versionJsonUrl = "https://raw.githubusercontent.com/acme/rider-booster/main/version.json",
            httpClient = mockClient,
            currentVersionCodeProvider = { 2 },
            currentVersionNameProvider = { "1.1" }
        )

        // 1. Update available
        manager.checkForUpdates(isManual = true)
        assertTrue(manager.state.value.checkStatus is UpdateCheckStatus.UpdateAvailable)
        assertTrue(manager.state.value.showUpdateDialog)

        // 2. Tampered download rejected by SHA-256 verification
        val manifest = (manager.state.value.checkStatus as UpdateCheckStatus.UpdateAvailable).manifest
        val downloadedOk = manager.downloadAndVerifyUpdate(manifest)
        assertFalse(downloadedOk)
        assertTrue(manager.state.value.downloadStatus is ApkDownloadStatus.Failed)

        // 3. Downgrade rejected
        mode = "DOWNGRADE"
        manager.checkForUpdates(isManual = true)
        assertTrue(manager.state.value.checkStatus is UpdateCheckStatus.UpToDate)

        // 4. Rate limit handled gracefully
        mode = "RATE_LIMIT"
        manager.checkForUpdates(isManual = true)
        val err = manager.state.value.checkStatus as UpdateCheckStatus.Error
        assertTrue(err.isRateLimited)

        // 5. Reject direct downgrade download
        val downgradeAttempt = manager.downloadAndVerifyUpdate(
            GitHubReleaseManifest(
                versionCode = 1,
                versionName = "1.0",
                releaseTitle = "Old",
                releaseNotes = "Old",
                apkUrl = "https://example.com/old.apk",
                sha256 = validSha,
                mandatory = false,
                minSupportedVersionCode = 1
            )
        )
        assertFalse(downgradeAttempt)
        }
    }

    private fun installFakeLaunchablePackage(
        shadowPm: org.robolectric.shadows.ShadowPackageManager,
        packageName: String,
        appLabel: String,
        activityName: String,
        versionName: String,
        enabled: Boolean
    ) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.nonLocalizedLabel = appLabel
            this.enabled = enabled
        }
        val actInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = activityName
            this.applicationInfo = appInfo
            this.exported = true
        }
        val pkgInfo = PackageInfo().apply {
            this.packageName = packageName
            this.versionName = versionName
            this.applicationInfo = appInfo
            this.activities = arrayOf(actInfo)
        }
        shadowPm.installPackage(pkgInfo)

        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = actInfo
            this.nonLocalizedLabel = appLabel
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)

        val pkgLauncherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        shadowPm.addResolveInfoForIntent(pkgLauncherIntent, resolveInfo)

        val componentIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(packageName, activityName)
        }
        shadowPm.addResolveInfoForIntent(componentIntent, resolveInfo)
    }

    private fun ensureOnlineNetwork(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)
        val network = cm.activeNetwork ?: return
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowCm.setNetworkCapabilities(network, caps)
    }
}
