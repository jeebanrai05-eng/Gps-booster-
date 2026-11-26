package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppSettings
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderModeState
import com.example.domain.model.SystemCheckState
import com.example.domain.model.TalabatDetectionResult
import com.example.domain.model.TalabatLaunchOutcome
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.RiderModeCard
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.TalabatDetectionCard
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors

@Composable
fun RiderScreen(
    gpsState: GpsTelemetryState,
    networkState: NetworkDiagnosticsState,
    systemState: SystemCheckState,
    riderModeState: RiderModeState,
    talabatDetection: TalabatDetectionResult,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onStartRiderMode: () -> Unit,
    onStopRiderMode: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onLaunchTalabatRider: () -> TalabatLaunchOutcome,
    onRetryTalabatDetection: () -> Unit,
    onSelectManualRiderApp: (String?) -> Unit,
    onOpenAppPermissions: (String?) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRunGpsDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        text = "TALABAT RIDER CHECK",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Kuwait Delivery Shift Readiness & Compatibility",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.premiumOrange
                    )
                }
                LiveStatusPill(
                    label = if (talabatDetection.isInstalled) "INSTALLED" else "NOT FOUND",
                    toneColor = if (talabatDetection.isInstalled) colors.successGreen else colors.warningAmber,
                    pulse = false,
                    animationIntensity = settings.animationIntensity
                )
            }
        }

        // Talabat Rider Multi-Method Detection & Launcher Card
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

        // Background Rider Mode Shift Control
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

        // Compatibility Report Card
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
                        title = "Rider Compatibility Report",
                        subtitle = if (talabatDetection.isInstalled) {
                            "Detected: ${talabatDetection.detectedPackageName} (v${talabatDetection.appVersion ?: "unknown"})"
                        } else {
                            "Talabat Rider package not auto-detected — use Scan Installed Apps above"
                        },
                        icon = Icons.Rounded.DeliveryDining,
                        accentColor = colors.premiumOrange
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "TALABAT RIDER",
                            value = if (talabatDetection.isInstalled) "Installed" else "Not Installed",
                            valueColor = if (talabatDetection.isInstalled) colors.successGreen else colors.warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "GPS FIX",
                            value = if (gpsState.fixStatus == GpsFixStatus.FIX_ACQUIRED) "Locked" else gpsState.fixStatus.name.replace("_", " "),
                            valueColor = if (gpsState.fixStatus == GpsFixStatus.FIX_ACQUIRED) colors.successGreen else colors.warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OptimizerCheckRow("Location Permission (Device Check)", systemState.hasFineLocationPermission)
                    OptimizerCheckRow(
                        "Background Location (All the time)",
                        systemState.hasBackgroundLocationPermission
                    )
                    OptimizerCheckRow("Device Location Services", systemState.isLocationServicesEnabled)
                    OptimizerCheckRow("GPS Hardware Provider Enabled", systemState.isGpsProviderEnabled)
                    OptimizerCheckRow("Network Connectivity Validated", networkState.isNetworkValidated)
                    OptimizerCheckRow("Battery Optimization Unrestricted", systemState.isBatteryOptimizationIgnored)
                    OptimizerCheckRow("Background Execution Unrestricted", !systemState.isBackgroundRestricted)
                }
            }
        }

        // Action Buttons
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Quick Rider Actions",
                        subtitle = "Direct shortcuts to Android location, battery, and app permission settings",
                        icon = Icons.Rounded.VerifiedUser,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenAppPermissions(talabatDetection.detectedPackageName) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rider_open_permissions_button")
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("App Permissions")
                        }
                        OutlinedButton(
                            onClick = onOpenLocationSettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rider_open_location_button")
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Location")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenBatterySettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rider_open_battery_button")
                        ) {
                            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Battery Settings")
                        }
                        Button(
                            onClick = onRunGpsDiagnostics,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rider_run_diagnostics_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue)
                        ) {
                            Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run GPS Check")
                        }
                    }
                }
            }
        }

        // Strict Independence & Compliance Notice
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Independent Rider Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.neonCyan
                    )
                    Text(
                        text = "Ultimate Rider Booster never reads private Talabat account data, never interacts with orders, and never spoofs GPS location. It strictly checks device readiness (GNSS, internet, and battery settings).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
