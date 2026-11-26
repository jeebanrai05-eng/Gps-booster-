package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DiagnosticRecordEntity
import com.example.domain.model.AppSettings
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.LocationSourceType
import com.example.domain.model.SatelliteInfo
import com.example.domain.model.SmartGpsRecoveryState
import com.example.domain.model.SystemCheckState
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SmartGpsRecoveryCard
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters
import java.util.Locale

@Composable
fun GpsScreen(
    gpsState: GpsTelemetryState,
    systemState: SystemCheckState,
    smartRecoveryState: SmartGpsRecoveryState,
    recentHistory: List<DiagnosticRecordEntity>,
    settings: AppSettings,
    onToggleMonitoring: () -> Unit,
    onStartGpsMonitoring: () -> Unit,
    onStopGpsMonitoring: () -> Unit,
    onRunSmartGpsRecovery: () -> Unit,
    onRefreshLocationRequest: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Monitoring Toggle
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
                        text = "GPS PERFORMANCE ENGINE",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real GNSS Status & Smart Location Optimizer",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.neonCyan
                    )
                }
                LiveStatusPill(
                    label = if (gpsState.isMonitoringActive) "ACTIVE" else "PAUSED",
                    toneColor = if (gpsState.isMonitoringActive) colors.successGreen else colors.warningAmber,
                    pulse = gpsState.isMonitoringActive,
                    animationIntensity = settings.animationIntensity
                )
            }
        }

        // GNSS Satellite Telemetry Card
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "GNSS Satellite & Fix Analysis",
                        subtitle = if (gpsState.gnssStatusSupported) {
                            "GnssStatus.Callback active • Visible vs Used in Fix separated"
                        } else {
                            "GnssStatus hardware callback unavailable on this device"
                        },
                        icon = Icons.Rounded.SatelliteAlt,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "VISIBLE SATS",
                            value = "${gpsState.satellitesTotal}",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "USED IN FIX",
                            value = "${gpsState.satellitesUsedInFix}",
                            valueColor = if (gpsState.satellitesUsedInFix >= 4) colors.successGreen else colors.warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "AVG C/N0",
                            value = gpsState.averageCn0DbHz?.let { String.format(Locale.US, "%.1f dB-Hz", it) } ?: "N/A",
                            valueColor = colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
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
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "V. ACCURACY",
                            value = TelemetryFormatters.formatDistanceMeters(
                                gpsState.verticalAccuracyMeters,
                                settings.useMetricUnits
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "MAX C/N0",
                            value = gpsState.maxCn0DbHz?.let { String.format(Locale.US, "%.1f dB-Hz", it) } ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "UPDATE INTERVAL",
                            value = gpsState.updateIntervalMs?.let { "${it} ms" } ?: "Awaiting",
                            valueColor = if (gpsState.updateDelayDetected) colors.warningAmber else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "STABILITY",
                            value = gpsState.locationStabilityLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "LOCATION SOURCE",
                            value = gpsState.locationSourceType.label,
                            valueColor = when (gpsState.locationSourceType) {
                                LocationSourceType.GNSS -> colors.successGreen
                                LocationSourceType.FUSED -> colors.neonCyan
                                LocationSourceType.NETWORK -> colors.warningAmber
                                LocationSourceType.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "LAST GNSS FIX",
                            value = TelemetryFormatters.formatTime(gpsState.lastValidGnssFixWallTimeMs),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (gpsState.updateDelayDetected) {
                        Text(
                            text = "Update delay detected: fixes are arriving slower than ${settings.gpsMonitoringIntervalMs} ms interval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.warningAmber
                        )
                    }

                    if (gpsState.constellationCounts.isNotEmpty()) {
                        Text(
                            text = "Constellations: " + gpsState.constellationCounts.entries.joinToString(" • ") {
                                "${it.key}: ${it.value}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.electricBlue
                        )
                    }

                    Button(
                        onClick = onToggleMonitoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_gps_monitoring_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gpsState.isMonitoringActive) colors.glassCardElevated else colors.electricBlue
                        )
                    ) {
                        Icon(Icons.Rounded.LocationSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (gpsState.isMonitoringActive) "Pause Live GNSS Monitoring" else "Resume Live GNSS Monitoring",
                            color = if (gpsState.isMonitoringActive) colors.neonCyan else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Top Satellite Signal Bars (Real C/N0 Measurements)
        if (gpsState.satelliteDetails.isNotEmpty()) {
            item {
                GlassTelemetryCard(
                    glowTone = GlowTone.CYAN_BLUE,
                    animationIntensity = settings.animationIntensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitleBar(
                            title = "Top Satellites (C/N0 Signal)",
                            subtitle = "Showing up to 8 strongest satellites tracked in real time",
                            icon = Icons.Rounded.GpsFixed
                        )
                        gpsState.satelliteDetails.take(8).forEach { sat ->
                            SatelliteSignalRow(sat = sat)
                        }
                    }
                }
            }
        }

        // Smart GPS Recovery Engine
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
                onOpenLocationPermissions = onOpenAppPermissions,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenNetworkSettings = onOpenNetworkSettings,
                modifier = Modifier.widthIn(max = 640.dp),
                showFullStepsByDefault = true
            )
        }

        // Smart Location Optimizer
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.ORANGE_PRIORITY,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "SMART LOCATION OPTIMIZER",
                        subtitle = "Real Android configuration checks & direct system shortcuts",
                        icon = Icons.Rounded.Tune,
                        accentColor = colors.premiumOrange
                    )

                    OptimizerCheckRow("Device Location Services", systemState.isLocationServicesEnabled)
                    OptimizerCheckRow("GPS Hardware Provider", systemState.isGpsProviderEnabled)
                    OptimizerCheckRow("Network Location Provider", systemState.isNetworkProviderEnabled)
                    OptimizerCheckRow("Google Play Services Fused Accuracy", systemState.isGooglePlayServicesAvailable)
                    OptimizerCheckRow("Precise (Fine) Location Permission", systemState.hasFineLocationPermission)
                    OptimizerCheckRow("Background Location (All the time)", systemState.hasBackgroundLocationPermission)
                    OptimizerCheckRow("Battery Optimization Exempt", systemState.isBatteryOptimizationIgnored)
                    OptimizerCheckRow("Background Execution Unrestricted", !systemState.isBackgroundRestricted)

                    Text(
                        text = "Note: Android controls hardware GNSS scheduling. Use the shortcuts below to fix disabled settings safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!systemState.hasFineLocationPermission) {
                        Button(
                            onClick = onRequestLocationPermission,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.premiumOrange)
                        ) {
                            Text("Request Precise Location Permission", color = MaterialTheme.colorScheme.onTertiary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenLocationSettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gps_open_location_settings")
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Location")
                        }
                        OutlinedButton(
                            onClick = onOpenBatterySettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gps_open_battery_settings")
                        ) {
                            Icon(Icons.Rounded.BatteryAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Battery")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenAppPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App Permissions & Battery Info")
                    }
                }
            }
        }

        // Local GPS Diagnostics History
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitleBar(
                        title = "Recent GPS Diagnostic Runs",
                        subtitle = "Stored locally via Room (offline private history)",
                        icon = Icons.Rounded.History
                    )
                    if (recentHistory.isEmpty()) {
                        Text(
                            text = "No diagnostic runs saved yet. Press ⚡ BOOST NOW on the Home screen to log a baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentHistory.take(5).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.navyBackground.copy(alpha = 0.65f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${TelemetryFormatters.formatDateTime(item.timestampMs)} • ${item.gpsStatus}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Accuracy: ${TelemetryFormatters.formatDistanceMeters(item.horizontalAccuracyMeters, settings.useMetricUnits)} • ${item.summaryNote}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${item.overallScore}/100",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (item.overallScore >= 80) colors.successGreen else colors.warningAmber
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SatelliteSignalRow(sat: SatelliteInfo) {
    val colors = LocalRiderColors.current
    val normalized = (sat.cn0DbHz / 50f).coerceIn(0f, 1f)
    val barColor = when {
        sat.usedInFix && sat.cn0DbHz >= 28f -> colors.successGreen
        sat.usedInFix -> colors.neonCyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${sat.constellation.take(3)}#${sat.svid}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(58.dp)
        )
        LinearProgressIndicator(
            progress = { normalized },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = colors.navyBackground
        )
        Text(
            text = String.format(Locale.US, "%.0f dB", sat.cn0DbHz),
            style = MaterialTheme.typography.labelSmall,
            color = barColor,
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (sat.usedInFix) colors.successGreen.copy(alpha = 0.16f)
                    else colors.navyBackground
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (sat.usedInFix) "FIX" else "VIS",
                style = MaterialTheme.typography.labelSmall,
                color = if (sat.usedInFix) colors.successGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OptimizerCheckRow(label: String, isHealthy: Boolean) {
    val colors = LocalRiderColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isHealthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = if (isHealthy) "Enabled" else "Needs Attention",
                tint = if (isHealthy) colors.successGreen else colors.warningAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isHealthy) "OK" else "Check",
                style = MaterialTheme.typography.labelSmall,
                color = if (isHealthy) colors.successGreen else colors.warningAmber
            )
        }
    }
}
