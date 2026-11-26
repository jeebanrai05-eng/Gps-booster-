package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderModeState
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters

@Composable
fun RiderModeCard(
    riderModeState: RiderModeState,
    gpsState: GpsTelemetryState,
    networkState: NetworkDiagnosticsState,
    animationIntensity: AnimationIntensity,
    onStartRiderMode: () -> Unit,
    onStopRiderMode: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    val haptic = LocalHapticFeedback.current
    val isActive = riderModeState.isActive

    GlassTelemetryCard(
        glowTone = if (isActive) GlowTone.SUCCESS else GlowTone.CYAN_BLUE,
        animationIntensity = if (isActive) animationIntensity else AnimationIntensity.REDUCED,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row with animated toggle + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isActive) {
                        MiniAnimatedGpsRadar(animationIntensity = animationIntensity)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.electricBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radar,
                                contentDescription = "Background Rider Mode",
                                tint = colors.neonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "BACKGROUND RIDER MODE",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isActive) colors.successGreen else MaterialTheme.colorScheme.onSurface
                        )
                        LiveStatusPill(
                            label = if (isActive) "RIDER MODE ACTIVE" else "Monitoring Stopped",
                            toneColor = if (isActive) colors.successGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            pulse = isActive,
                            animationIntensity = animationIntensity
                        )
                    }
                }

                Switch(
                    checked = isActive,
                    onCheckedChange = { checked ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (checked) onStartRiderMode() else onStopRiderMode()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.deepBackground,
                        checkedTrackColor = colors.successGreen,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = colors.navyBackground
                    ),
                    modifier = Modifier.testTag("rider_mode_toggle")
                )
            }

            // Live monitoring indicators when active
            AnimatedVisibility(visible = isActive) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "GPS FRESHNESS",
                            value = when (gpsState.fixStatus) {
                                GpsFixStatus.FIX_ACQUIRED -> "Fresh (${gpsState.locationAgeSeconds ?: 0}s)"
                                GpsFixStatus.STALE_FIX -> "Delayed (${gpsState.locationAgeSeconds ?: 0}s)"
                                GpsFixStatus.SEARCHING -> "Searching"
                                else -> gpsState.fixStatus.name.replace("_", " ")
                            },
                            valueColor = if (gpsState.fixStatus == GpsFixStatus.FIX_ACQUIRED) colors.successGreen else colors.warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "NETWORK",
                            value = if (networkState.isNetworkValidated) "${networkState.transportType.label} OK"
                            else if (networkState.isNetworkAvailable) "Unverified"
                            else "Offline",
                            valueColor = if (networkState.isNetworkValidated) colors.successGreen else colors.errorRed,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "INTERVAL",
                            value = "${riderModeState.monitoringIntervalMs / 1000L}s Eco",
                            valueColor = colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    riderModeState.activeAlertSummary?.let { alert ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.warningAmber.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Active Alert",
                                tint = colors.warningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = alert,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warningAmber
                            )
                        }
                    }

                    riderModeState.startedAtMs?.let { started ->
                        Text(
                            text = "Persistent service running since ${TelemetryFormatters.formatTime(started)} • Monitors while minimized, screen locked, or in Talabat Rider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Inactive explanation & permission readiness
            if (!isActive) {
                Text(
                    text = "Keeps a battery-efficient location foreground service running with a persistent status notification so you get alerted if GPS stalls or internet drops while riding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Explicit START / STOP RIDER MODE Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isActive) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRiderMode()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_rider_mode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.successGreen,
                            contentColor = colors.deepBackground
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "START RIDER MODE",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStopRiderMode()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stop_rider_mode_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.errorRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STOP RIDER MODE",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Clear permission explanations & action buttons
            if (!riderModeState.hasForegroundLocationPermission) {
                PermissionExplanationBanner(
                    title = "Location Permission Needed",
                    explanation = "Android requires Foreground Location access before starting a location foreground service.",
                    actionLabel = "Grant Location",
                    onAction = onRequestLocationPermission,
                    testTag = "rider_mode_grant_location_btn"
                )
            } else if (!riderModeState.hasNotificationPermission) {
                PermissionExplanationBanner(
                    title = "Notification Permission Recommended (Android 13+)",
                    explanation = "Allows showing the persistent Rider Mode status bar notification and GPS/network drop alerts.",
                    actionLabel = "Enable Alerts",
                    onAction = onRequestNotificationPermission,
                    testTag = "rider_mode_grant_notifications_btn"
                )
            } else if (!riderModeState.hasBackgroundLocationPermission) {
                PermissionExplanationBanner(
                    title = "Optional: Allow All The Time (Android 14+ Recovery)",
                    explanation = "Rider Mode works from the foreground with 'While using app'. Setting location to 'Allow all the time' also lets Android restore Rider Mode automatically if the OS restarts the service.",
                    actionLabel = "App Permissions",
                    onAction = onOpenAppPermissions,
                    testTag = "rider_mode_bg_perm_btn"
                )
            }

            riderModeState.restrictionNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningAmber
                )
            }

            Text(
                text = "Note: Rider Mode monitors real GPS freshness and network connectivity only. It does not boost physical GPS signal strength or control Talabat's internal GPS behavior.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionExplanationBanner(
    title: String,
    explanation: String,
    actionLabel: String,
    onAction: () -> Unit,
    testTag: String
) {
    val colors = LocalRiderColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.navyBackground.copy(alpha = 0.78f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = colors.neonCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.neonCyan
            )
        }
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun MiniAnimatedGpsRadar(animationIntensity: AnimationIntensity) {
    val colors = LocalRiderColors.current
    val isResumed = rememberIsAppResumed()
    val shouldAnimate = isResumed && animationIntensity != AnimationIntensity.MINIMAL

    val sweepAngle = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "mini_rider_radar")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "mini_radar_sweep"
        )
        angle
    } else {
        45f
    }

    Box(modifier = Modifier.size(38.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.44f
            drawCircle(
                color = colors.successGreen.copy(alpha = 0.16f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = colors.successGreen.copy(alpha = 0.55f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            rotate(degrees = sweepAngle, pivot = center) {
                drawLine(
                    color = colors.successGreen,
                    start = center,
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = 2.dp.toPx()
                )
            }
            drawCircle(
                color = colors.successGreen,
                radius = 3.5.dp.toPx(),
                center = center
            )
        }
    }
}
