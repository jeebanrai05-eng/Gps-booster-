package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.AppSettings
import com.example.domain.model.AppUpdateUiState
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.GitHubReleaseManifest
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderModeState
import com.example.domain.model.SmartDiagnosticState
import com.example.domain.model.SmartGpsRecoveryState
import com.example.domain.model.SystemCheckState
import com.example.domain.model.TalabatDetectionResult
import com.example.domain.model.TalabatLaunchOutcome
import com.example.ui.components.AnimatedRadarHero
import com.example.ui.components.AppUpdateCard
import com.example.ui.components.BoostNowButton
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.RiderModeCard
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SmartDiagnosticProgressSection
import com.example.ui.components.SmartGpsRecoveryCard
import com.example.ui.components.TalabatDetectionCard
import com.example.ui.components.TelemetryStatTile
import com.example.ui.components.rememberIsAppResumed
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters
import kotlinx.coroutines.delay

@Composable
fun HomeDashboardScreen(
    gpsState: GpsTelemetryState,
    networkState: NetworkDiagnosticsState,
    systemState: SystemCheckState,
    riderModeState: RiderModeState,
    diagnosticState: SmartDiagnosticState,
    smartRecoveryState: SmartGpsRecoveryState,
    talabatDetection: TalabatDetectionResult,
    updateState: AppUpdateUiState,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onStartRiderMode: () -> Unit,
    onStopRiderMode: () -> Unit,
    onBoostNow: () -> Unit,
    onDismissReport: () -> Unit,
    onExecuteAction: (DiagnosticActionType) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppPermissions: (String?) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onRunSmartGpsRecovery: () -> Unit,
    onRefreshLocationRequest: () -> Unit,
    onStartGpsMonitoring: () -> Unit,
    onStopGpsMonitoring: () -> Unit,
    onLaunchTalabatRider: () -> TalabatLaunchOutcome,
    onRetryTalabatDetection: () -> Unit,
    onSelectManualRiderApp: (String?) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onDownloadAndInstallUpdate: (GitHubReleaseManifest) -> Unit,
    onOpenInstallUnknownSettings: () -> Unit,
    onRetryInstallVerifiedApk: () -> Unit,
    onOpenReleasesPage: () -> Unit,
    onOpenSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    val isResumed = rememberIsAppResumed()
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isResumed) {
        while (isResumed) {
            currentTimeMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val (gpsLabel, gpsColor, shouldPulse) = when (gpsState.fixStatus) {
        GpsFixStatus.FIX_ACQUIRED -> Triple("GPS LOCKED", colors.successGreen, true)
        GpsFixStatus.SEARCHING -> Triple("SEARCHING", colors.neonCyan, true)
        GpsFixStatus.STALE_FIX -> Triple("STALE FIX", colors.warningAmber, false)
        GpsFixStatus.GPS_DISABLED -> Triple("GPS OFF", colors.errorRed, false)
        GpsFixStatus.PERMISSION_DENIED -> Triple("NO PERM", colors.errorRed, false)
        GpsFixStatus.HARDWARE_UNAVAILABLE -> Triple("NO GNSS", colors.warningAmber, false)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // TOP HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ULTIMATE RIDER BOOSTER",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Stay Connected. Stay Accurate. Stay Ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.neonCyan
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        LiveStatusPill(
                            label = gpsLabel,
                            toneColor = gpsColor,
                            pulse = shouldPulse,
                            animationIntensity = settings.animationIntensity
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TelemetryFormatters.formatTime(currentTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenSettingsScreen,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings and Privacy",
                            tint = colors.neonCyan
                        )
                    }
                }
            }
        }

        // HERO SECTION: Satellite Radar + Live GPS Telemetry
        item {
            GlassTelemetryCard(
                glowTone = when (gpsState.fixStatus) {
                    GpsFixStatus.FIX_ACQUIRED -> GlowTone.CYAN_BLUE
                    GpsFixStatus.GPS_DISABLED, GpsFixStatus.PERMISSION_DENIED -> GlowTone.ERROR
                    else -> GlowTone.CYAN_BLUE
                },
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedRadarHero(
                            fixStatus = gpsState.fixStatus,
                            satellitesUsed = gpsState.satellitesUsedInFix,
                            satellitesTotal = gpsState.satellitesTotal,
                            animationIntensity = settings.animationIntensity
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LIVE GPS TELEMETRY",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.neonCyan
                            )
                            Text(
                                text = gpsState.signalQualityLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = gpsColor
                            )
                            Text(
                                text = "Location: ${TelemetryFormatters.formatCoordinates(gpsState.latitude, gpsState.longitude)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (gpsState.isLocationAvailable) {
                                    "Source: ${gpsState.locationSourceType.label} (${gpsState.providerName ?: "gnss"})"
                                } else {
                                    "Location Unavailable"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "H. ACCURACY",
                            value = TelemetryFormatters.formatDistanceMeters(
                                gpsState.horizontalAccuracyMeters,
                                settings.useMetricUnits
                            ),
                            valueColor = when {
                                gpsState.horizontalAccuracyMeters == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                gpsState.horizontalAccuracyMeters <= 12f -> colors.successGreen
                                gpsState.horizontalAccuracyMeters <= 30f -> colors.warningAmber
                                else -> colors.errorRed
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "LAST UPDATE",
                            value = TelemetryFormatters.formatTime(gpsState.lastUpdateWallTimeMs),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "LOCATION AGE",
                            value = TelemetryFormatters.formatAgeSeconds(gpsState.locationAgeSeconds),
                            valueColor = if ((gpsState.locationAgeSeconds ?: 0L) > 15L) colors.warningAmber
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Contextual recovery CTA if permission missing or GPS disabled
                    if (gpsState.fixStatus == GpsFixStatus.PERMISSION_DENIED) {
                        Button(
                            onClick = onRequestLocationPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_location_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue)
                        ) {
                            Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Precise Location Permission")
                        }
                    } else if (gpsState.fixStatus == GpsFixStatus.GPS_DISABLED) {
                        OutlinedButton(
                            onClick = onOpenLocationSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_gps_button")
                        ) {
                            Icon(Icons.Rounded.GpsOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enable GPS in Location Settings")
                        }
                    }
                }
            }
        }

        // BACKGROUND RIDER MODE CARD
        item {
            RiderModeCard(
                riderModeState = riderModeState,
                gpsState = gpsState,
                networkState = networkState,
                animationIntensity = settings.animationIntensity,
                onStartRiderMode = onStartRiderMode,
                onStopRiderMode = onStopRiderMode,
                onRequestLocationPermission = onRequestLocationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenAppPermissions = { onOpenAppPermissions(null) },
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        // MAIN BOOST BUTTON
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BoostNowButton(
                    isRunning = diagnosticState.isRunning,
                    animationIntensity = settings.animationIntensity,
                    onClick = onBoostNow
                )
                Text(
                    text = "Runs a real 10-step GNSS, network, battery & permission diagnostic without altering protected system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 10-STEP DIAGNOSTIC PROGRESS & REPORT
        item {
            SmartDiagnosticProgressSection(
                diagnosticState = diagnosticState,
                animationIntensity = settings.animationIntensity,
                onActionClick = onExecuteAction,
                onDismissReport = onDismissReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            )
        }

        // QUICK NETWORK & DEVICE STATUS CARDS
        item {
            GlassTelemetryCard(
                glowTone = if (networkState.isNetworkValidated) GlowTone.CYAN_BLUE else GlowTone.WARNING,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Connection & System Readiness",
                        subtitle = "Live Android ConnectivityManager & PowerManager telemetry",
                        icon = Icons.Rounded.NetworkCheck,
                        accentColor = colors.neonCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "NETWORK LINK",
                            value = if (networkState.isNetworkValidated) {
                                "${networkState.transportType.label} OK"
                            } else if (networkState.isNetworkAvailable) {
                                "${networkState.transportType.label} Unverified"
                            } else {
                                "Offline"
                            },
                            valueColor = if (networkState.isNetworkValidated) colors.successGreen else colors.warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "BATTERY",
                            value = if (systemState.batteryLevelPercent >= 0) {
                                "${systemState.batteryLevelPercent}% ${if (systemState.isCharging) "⚡" else ""}"
                            } else "Unknown",
                            valueColor = if (systemState.isPowerSaveMode) colors.warningAmber else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "BATTERY SAVER",
                            value = if (systemState.isPowerSaveMode) "Active (Throttled)" else "Off",
                            valueColor = if (systemState.isPowerSaveMode) colors.warningAmber else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "SAT LOCK",
                            value = if (gpsState.gnssStatusSupported) {
                                "${gpsState.satellitesUsedInFix} used / ${gpsState.satellitesTotal} vis"
                            } else "Unsupported",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // CARD 1: SMART GPS RECOVERY
        item {
            SmartGpsRecoveryCard(
                recoveryState = smartRecoveryState,
                gpsState = gpsState,
                animationIntensity = settings.animationIntensity,
                onRunRecovery = onRunSmartGpsRecovery,
                onRefreshLocationRequest = onRefreshLocationRequest,
                onStartGpsMonitoring = onStartGpsMonitoring,
                onStopGpsMonitoring = onStopGpsMonitoring,
                onOpenGpsSettings = onOpenLocationSettings,
                onOpenLocationPermissions = { onOpenAppPermissions(null) },
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenNetworkSettings = onOpenNetworkSettings,
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        // CARD 2: TALABAT RIDER DETECTION
        item {
            TalabatDetectionCard(
                detection = talabatDetection,
                animationIntensity = settings.animationIntensity,
                snackbarHostState = snackbarHostState,
                onLaunchTalabatRider = onLaunchTalabatRider,
                onRetryDetection = onRetryTalabatDetection,
                onSelectManualApp = onSelectManualRiderApp,
                onOpenAppInfo = onOpenAppPermissions,
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        // CARD 3: APP UPDATE CENTER
        item {
            AppUpdateCard(
                updateState = updateState,
                animationIntensity = settings.animationIntensity,
                onCheckForUpdates = onCheckForUpdates,
                onOpenUpdateDialog = onOpenUpdateDialog,
                onDownloadAndInstall = onDownloadAndInstallUpdate,
                onOpenInstallUnknownSettings = onOpenInstallUnknownSettings,
                onRetryInstallVerifiedApk = onRetryInstallVerifiedApk,
                onOpenReleasesPage = onOpenReleasesPage,
                modifier = Modifier.widthIn(max = 640.dp)
            )
        }

        // OFFLINE & PRIVACY BADGE
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = AnimationIntensity.MINIMAL,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Offline Privacy Guarantee",
                        tint = colors.successGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Offline-capable GPS & local diagnostics. Zero cloud tracking, zero fake signal claims.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
