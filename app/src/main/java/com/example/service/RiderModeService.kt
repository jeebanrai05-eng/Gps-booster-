package com.example.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.RiderBoosterApplication
import com.example.domain.model.DetectedGpsProblem
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.SystemCheckState
import com.example.engine.RiderModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

class RiderModeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    private var lastAlertCategory: String? = null
    private var lastAlertTimeMs: Long = 0L
    private var lastOngoingText: String = ""
    private var searchingSinceMs: Long? = null
    private var hadFixDuringSession: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as RiderBoosterApplication).container
        val controller = container.riderModeController
        controller.refreshPermissionSnapshot()

        val action = intent?.action
        if (action == ACTION_STOP_RIDER_MODE) {
            stopMonitoringAndShutdown(controller, restrictionNote = null)
            return START_NOT_STICKY
        }

        // If recreated by Android (intent == null), verify Rider Mode was intentionally persisted
        if (intent == null) {
            val wasActive = runBlocking { controller.isRiderModePersistedActive() }
            if (!wasActive) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val hasLocationPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPerm) {
            stopMonitoringAndShutdown(
                controller,
                restrictionNote = "Location permission revoked. Rider Mode stopped."
            )
            return START_NOT_STICKY
        }

        val startedInForeground = promoteToForegroundSafely(controller, intent == null)
        if (!startedInForeground) {
            return START_NOT_STICKY
        }

        val now = System.currentTimeMillis()
        controller.markServiceActive(now)

        val intervalMs = if (controller.isUiInForeground) {
            container.gpsEngine.state.value.updateIntervalMs ?: 2000L
        } else {
            controller.backgroundMonitoringIntervalMs()
        }
        container.systemEngine.refreshSystemChecks()
        container.gpsEngine.startMonitoring(
            if (controller.isUiInForeground) 2000L.coerceAtLeast(1000L) else controller.backgroundMonitoringIntervalMs()
        )
        container.networkEngine.startMonitoring()

        startBackgroundWatchdog(container, controller)
        return START_STICKY
    }

    private fun promoteToForegroundSafely(
        controller: RiderModeController,
        isSystemRecreation: Boolean
    ): Boolean {
        val initialNotification = buildOngoingNotification(
            contentText = "Monitoring GPS freshness & network stability..."
        )
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        return try {
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                initialNotification,
                serviceType
            )
            true
        } catch (e: SecurityException) {
            handleForegroundStartBlocked(controller, isSystemRecreation)
            false
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                handleForegroundStartBlocked(controller, isSystemRecreation)
            } else {
                handleForegroundStartBlocked(controller, isSystemRecreation)
            }
            false
        }
    }

    private fun handleForegroundStartBlocked(
        controller: RiderModeController,
        isSystemRecreation: Boolean
    ) {
        val message = if (isSystemRecreation) {
            "Android prevented restarting Rider Mode in the background (grant 'Allow all the time' location if automatic restart is needed)."
        } else {
            "Android foreground service restrictions blocked Rider Mode start."
        }
        postProblemAlert(
            title = "Rider Mode Paused by Android",
            body = "Reopen Ultimate Rider Booster to resume background shift monitoring."
        )
        controller.markServiceStopped(restrictionNote = message)
        stopSelf()
    }

    private fun startBackgroundWatchdog(
        container: com.example.AppContainer,
        controller: RiderModeController
    ) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                container.gpsEngine.refreshStaticStatus()
                container.networkEngine.refreshNetworkSnapshot()
                container.systemEngine.refreshSystemChecks()
                container.smartGpsRecoveryEngine.updateSnapshotFromLiveEngines()

                val gps = container.gpsEngine.state.value
                val net = container.networkEngine.state.value
                val sys = container.systemEngine.state.value
                val problems = container.smartGpsRecoveryEngine.detectCurrentProblems(gps, sys)
                val now = System.currentTimeMillis()

                if (gps.fixStatus == GpsFixStatus.FIX_ACQUIRED) {
                    hadFixDuringSession = true
                    searchingSinceMs = null
                } else if (gps.fixStatus == GpsFixStatus.SEARCHING) {
                    if (searchingSinceMs == null) searchingSinceMs = now
                } else {
                    searchingSinceMs = null
                }

                val problemAlert = evaluateProblems(gps, net, sys, problems, now)
                controller.updateServiceTelemetrySummary(
                    activeAlertSummary = problemAlert?.second,
                    lastCheckMs = now
                )

                val summaryText = buildStatusSummary(gps, net)
                if (summaryText != lastOngoingText) {
                    lastOngoingText = summaryText
                    notificationManager.notify(
                        ONGOING_NOTIFICATION_ID,
                        buildOngoingNotification(summaryText)
                    )
                }

                if (problemAlert != null) {
                    val (category, message) = problemAlert
                    val cooldownElapsed = (now - lastAlertTimeMs) > ALERT_COOLDOWN_MS
                    if (category != lastAlertCategory || cooldownElapsed) {
                        lastAlertCategory = category
                        lastAlertTimeMs = now
                        postProblemAlert(
                            title = "Rider Booster Alert",
                            body = message
                        )
                    }
                } else {
                    if (lastAlertCategory != null) {
                        lastAlertCategory = null
                        notificationManager.cancel(ALERT_NOTIFICATION_ID)
                    }
                }

                delay(5000L)
            }
        }
    }

    private fun evaluateProblems(
        gps: GpsTelemetryState,
        net: NetworkDiagnosticsState,
        sys: SystemCheckState,
        problems: List<DetectedGpsProblem>,
        nowMs: Long
    ): Pair<String, String>? {
        return when {
            DetectedGpsProblem.LOCATION_PERMISSION_DENIED in problems ->
                "GPS_PERM" to "Location permission denied. GPS monitoring paused."
            DetectedGpsProblem.GPS_DISABLED in problems ->
                "GPS_OFF" to "Device GPS / Location Services are turned OFF."
            !net.isNetworkAvailable ->
                "NET_OFF" to "Internet disconnected. Rider apps cannot receive order updates."
            !net.isNetworkValidated ->
                "NET_UNVAL" to "Connected via ${net.transportType.label}, but internet validation failed."
            DetectedGpsProblem.STALE_LOCATION in problems ->
                "GPS_STALE" to "Location update delay: last fix was ${gps.locationAgeSeconds ?: 0}s ago."
            DetectedGpsProblem.LOCATION_UPDATE_TIMEOUT in problems ->
                "GPS_DELAY" to "GPS update delay detected (${gps.locationAgeSeconds ?: 0}s since last fix)."
            sys.isBackgroundRestricted ->
                "BG_RESTRICT" to "Android background restriction active — Rider monitoring may be throttled."
            sys.isPowerSaveMode ->
                "BATTERY_SAVER" to "Battery Saver active — Android may reduce background GNSS fix frequency."
            gps.fixStatus == GpsFixStatus.SEARCHING &&
                (hadFixDuringSession || ((nowMs - (searchingSinceMs ?: nowMs)) > 25_000L)) ->
                "GPS_SEARCH" to "Searching for GNSS satellites (${gps.satellitesTotal} visible) — move toward open sky."
            else -> null
        }
    }

    private fun buildStatusSummary(gps: GpsTelemetryState, net: NetworkDiagnosticsState): String {
        val gpsPart = when (gps.fixStatus) {
            GpsFixStatus.FIX_ACQUIRED -> {
                val acc = gps.horizontalAccuracyMeters?.let { "±${it.roundToInt()}m" } ?: "Locked"
                "GPS: $acc (${gps.satellitesUsedInFix} sats)"
            }
            GpsFixStatus.STALE_FIX -> "GPS: Delayed (${gps.locationAgeSeconds ?: 0}s)"
            GpsFixStatus.SEARCHING -> "GPS: Searching (${gps.satellitesTotal} vis)"
            GpsFixStatus.GPS_DISABLED -> "GPS: Disabled"
            GpsFixStatus.PERMISSION_DENIED -> "GPS: No Permission"
            GpsFixStatus.HARDWARE_UNAVAILABLE -> "GPS: No GNSS"
        }
        val netPart = when {
            net.isNetworkValidated -> "${net.transportType.label} OK"
            net.isNetworkAvailable -> "${net.transportType.label} Unverified"
            else -> "Offline"
        }
        return "$gpsPart • Net: $netPart"
    }

    private fun buildOngoingNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RiderModeService::class.java).apply {
                action = ACTION_STOP_RIDER_MODE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.rider_mode_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                getString(R.string.stop_rider_mode),
                stopIntent
            )
            .build()
    }

    private fun postProblemAlert(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannels() {
        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING_ID,
            "Rider Mode Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status notification while Background Rider Mode is active."
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS_ID,
            "GPS & Network Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when GPS delays, disabled location, or network drops occur during Rider Mode."
        }

        notificationManager.createNotificationChannel(ongoingChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }

    private fun stopMonitoringAndShutdown(
        controller: RiderModeController,
        restrictionNote: String?
    ) {
        monitorJob?.cancel()
        monitorJob = null
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        controller.markServiceStopped(restrictionNote)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        val container = (application as RiderBoosterApplication).container
        if (container.riderModeController.state.value.isActive) {
            container.riderModeController.markServiceStopped(restrictionNote = null)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_RIDER_MODE = "com.example.action.START_RIDER_MODE"
        const val ACTION_STOP_RIDER_MODE = "com.example.action.STOP_RIDER_MODE"

        const val CHANNEL_ONGOING_ID = "rider_mode_ongoing"
        const val CHANNEL_ALERTS_ID = "rider_mode_alerts"
        const val ONGOING_NOTIFICATION_ID = 4101
        const val ALERT_NOTIFICATION_ID = 4102
        private const val ALERT_COOLDOWN_MS = 45_000L
    }
}
