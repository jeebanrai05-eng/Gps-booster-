package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.RecoveryOutcome
import com.example.domain.model.SmartGpsRecoveryState
import com.example.domain.model.StepStatus
import com.example.ui.theme.LocalRiderColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartGpsRecoveryCard(
    recoveryState: SmartGpsRecoveryState,
    gpsState: GpsTelemetryState,
    animationIntensity: AnimationIntensity,
    onRunRecovery: () -> Unit,
    onRefreshLocationRequest: () -> Unit,
    onStartGpsMonitoring: () -> Unit,
    onStopGpsMonitoring: () -> Unit,
    onOpenGpsSettings: () -> Unit,
    onOpenLocationPermissions: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showFullStepsByDefault: Boolean = false
) {
    val colors = LocalRiderColors.current
    var showSteps by remember(showFullStepsByDefault) { mutableStateOf(showFullStepsByDefault) }

    val (outcomeLabel, outcomeColor, glowTone) = when (recoveryState.outcome) {
        RecoveryOutcome.IDLE -> Triple("READY", colors.neonCyan, GlowTone.CYAN_BLUE)
        RecoveryOutcome.RUNNING -> Triple("RECOVERING", colors.electricBlue, GlowTone.CYAN_BLUE)
        RecoveryOutcome.SUCCESS -> Triple("SUCCESS", colors.successGreen, GlowTone.SUCCESS)
        RecoveryOutcome.INCOMPLETE -> Triple("INCOMPLETE", colors.warningAmber, GlowTone.WARNING)
        RecoveryOutcome.FAILED -> Triple("FAILED", colors.errorRed, GlowTone.ERROR)
    }

    GlassTelemetryCard(
        glowTone = glowTone,
        animationIntensity = animationIntensity,
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_gps_recovery_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitleBar(
                    title = "SMART GPS RECOVERY",
                    subtitle = "11-step GNSS diagnostics & fix recovery assistant",
                    icon = Icons.Rounded.SatelliteAlt,
                    accentColor = outcomeColor,
                    modifier = Modifier.weight(1f)
                )
                LiveStatusPill(
                    label = outcomeLabel,
                    toneColor = outcomeColor,
                    pulse = recoveryState.isRunning,
                    animationIntensity = animationIntensity
                )
            }

            if (recoveryState.isRunning || recoveryState.outcome != RecoveryOutcome.IDLE) {
                LinearProgressIndicator(
                    progress = { recoveryState.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = outcomeColor,
                    trackColor = colors.navyBackground
                )
            }

            // GPS RECOVERY STATUS metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "GPS PROVIDER",
                    value = if (recoveryState.gpsProviderEnabled) "Enabled" else "Disabled",
                    valueColor = if (recoveryState.gpsProviderEnabled) colors.successGreen else colors.errorRed,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "GNSS FIX",
                    value = if (recoveryState.gnssFixAcquired) "Locked" else recoveryState.locationSourceLabel,
                    valueColor = if (recoveryState.gnssFixAcquired) colors.successGreen else colors.warningAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "SAT VISIBLE / USED",
                    value = "${recoveryState.satellitesVisible} vis / ${recoveryState.satellitesUsed} used",
                    valueColor = if (recoveryState.satellitesUsed >= 4) colors.successGreen else colors.neonCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "ACCURACY",
                    value = recoveryState.locationAccuracyText,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "SIGNAL QUALITY",
                    value = recoveryState.signalQualityMeasurement,
                    valueColor = colors.neonCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "LAST VALID FIX",
                    value = recoveryState.lastValidFixText,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!recoveryState.reportSummary.isNullOrBlank()) {
                Text(
                    text = "Recovery Status (${outcomeLabel}): ${recoveryState.reportSummary}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = outcomeColor
                )
            }

            // Detected real-time problems
            if (recoveryState.detectedProblems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.navyBackground.copy(alpha = 0.72f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Detected Issues (${recoveryState.detectedProblems.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.warningAmber
                    )
                    recoveryState.detectedProblems.take(5).forEach { problem ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (problem.isCritical) Icons.Rounded.ErrorOutline else Icons.Rounded.Warning,
                                contentDescription = problem.title,
                                tint = if (problem.isCritical) colors.errorRed else colors.warningAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${problem.title}: ${problem.description}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Primary Recovery Action
            Button(
                onClick = {
                    showSteps = true
                    onRunRecovery()
                },
                enabled = !recoveryState.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_gps_recovery_button"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue)
            ) {
                Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (recoveryState.isRunning) "Running 11-Step Recovery..." else "Run 11-Step Smart GPS Recovery"
                )
            }

            // Toggle steps drawer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recovery Actions & Diagnostics",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showSteps = !showSteps },
                    modifier = Modifier.testTag("toggle_recovery_steps_button")
                ) {
                    Text(if (showSteps) "Hide 11 Steps" else "Show 11 Steps", color = colors.neonCyan)
                }
            }

            AnimatedVisibility(visible = showSteps || recoveryState.isRunning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.navyBackground.copy(alpha = 0.65f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recoveryState.steps.forEach { step ->
                        val (icon, tint) = when (step.status) {
                            StepStatus.PASS -> Icons.Rounded.CheckCircle to colors.successGreen
                            StepStatus.WARNING -> Icons.Rounded.Warning to colors.warningAmber
                            StepStatus.FAIL -> Icons.Rounded.ErrorOutline to colors.errorRed
                            StepStatus.RUNNING -> Icons.Rounded.LocationSearching to colors.neonCyan
                            StepStatus.PENDING -> Icons.Rounded.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = step.status.name,
                                tint = tint,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${step.stepNumber}. ${step.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = step.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tint
                                )
                            }
                        }
                    }
                }
            }

            // All requested recovery action buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefreshLocationRequest,
                    modifier = Modifier.testTag("refresh_location_request_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Location Request")
                }

                if (gpsState.isMonitoringActive) {
                    OutlinedButton(
                        onClick = onStopGpsMonitoring,
                        modifier = Modifier.testTag("recovery_stop_gps_monitoring")
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop GPS Monitoring")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartGpsMonitoring,
                        modifier = Modifier.testTag("recovery_start_gps_monitoring")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start GPS Monitoring")
                    }
                }

                OutlinedButton(
                    onClick = onOpenGpsSettings,
                    modifier = Modifier.testTag("recovery_open_gps_settings")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open GPS Settings")
                }

                OutlinedButton(
                    onClick = onOpenLocationPermissions,
                    modifier = Modifier.testTag("recovery_open_permissions")
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Location Permissions")
                }

                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    modifier = Modifier.testTag("recovery_open_battery_settings")
                ) {
                    Icon(Icons.Rounded.BatteryAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Battery Settings")
                }

                OutlinedButton(
                    onClick = onOpenNetworkSettings,
                    modifier = Modifier.testTag("recovery_open_network_settings")
                ) {
                    Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Network Settings")
                }
            }

            Text(
                text = "Diagnostics only: Android apps cannot physically amplify satellite signals or silently toggle system GPS.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
