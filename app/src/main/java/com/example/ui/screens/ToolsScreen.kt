package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppSettings
import com.example.domain.model.CompassState
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.SystemCheckState
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class RiderToolTab(val label: String, val icon: ImageVector) {
    COMPASS("Compass", Icons.Rounded.Explore),
    SPEEDOMETER("Speed", Icons.Rounded.Speed),
    ALTITUDE("Altitude", Icons.Rounded.Terrain),
    MAP("GPS Map", Icons.Rounded.Map),
    DEVICE("Device", Icons.Rounded.Memory)
}

@Composable
fun ToolsScreen(
    gpsState: GpsTelemetryState,
    networkState: NetworkDiagnosticsState,
    systemState: SystemCheckState,
    compassState: CompassState,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onStartCompass: () -> Unit,
    onStopCompass: () -> Unit,
    onResetSpeedStats: () -> Unit,
    onOpenCoordinateInMaps: (Double, Double) -> Boolean,
    onRefreshSystemChecks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    var selectedTool by rememberSaveable { mutableStateOf(RiderToolTab.COMPASS) }

    if (selectedTool != RiderToolTab.COMPASS) {
        BackHandler {
            selectedTool = RiderToolTab.COMPASS
        }
    }

    DisposableEffect(selectedTool) {
        if (selectedTool == RiderToolTab.COMPASS) {
            onStartCompass()
        } else {
            onStopCompass()
        }
        onDispose {
            onStopCompass()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Text(
                    text = "RIDER TOOLS SUITE",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Live Hardware Sensors, GPS Speedometer, Altitude & Offline Grid",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.neonCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RiderToolTab.entries.forEach { tab ->
                        val selected = selectedTool == tab
                        FilterChip(
                            selected = selected,
                            onClick = { selectedTool = tab },
                            label = { Text(tab.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.electricBlue.copy(alpha = 0.25f),
                                selectedLabelColor = colors.neonCyan,
                                selectedLeadingIconColor = colors.neonCyan
                            ),
                            modifier = Modifier.testTag("tool_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                when (selectedTool) {
                    RiderToolTab.COMPASS -> CompassToolCard(compassState, settings)
                    RiderToolTab.SPEEDOMETER -> SpeedometerToolCard(
                        gpsState = gpsState,
                        useMetricDefault = settings.useMetricUnits,
                        settings = settings,
                        onResetStats = onResetSpeedStats
                    )
                    RiderToolTab.ALTITUDE -> AltitudeToolCard(gpsState, settings)
                    RiderToolTab.MAP -> OfflineGpsMapCard(
                        gpsState = gpsState,
                        settings = settings,
                        snackbarHostState = snackbarHostState,
                        onOpenCoordinateInMaps = onOpenCoordinateInMaps
                    )
                    RiderToolTab.DEVICE -> DeviceDiagnosticsToolCard(
                        systemState = systemState,
                        networkState = networkState,
                        settings = settings,
                        onRefresh = onRefreshSystemChecks
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassToolCard(
    compassState: CompassState,
    settings: AppSettings
) {
    val colors = LocalRiderColors.current
    GlassTelemetryCard(
        glowTone = GlowTone.CYAN_BLUE,
        animationIntensity = settings.animationIntensity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitleBar(
                title = "GPS & Magnetometer Compass",
                subtitle = if (compassState.hasCompassSensors) {
                    "Live Android SensorManager heading"
                } else {
                    "Compass magnetometer sensor unavailable on this device"
                },
                icon = Icons.Rounded.Explore,
                accentColor = colors.neonCyan
            )

            if (!compassState.hasCompassSensors) {
                Text(
                    text = "This device does not expose rotation vector or magnetometer hardware sensors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.warningAmber
                )
            } else {
                val heading = compassState.azimuthDegrees ?: 0f
                Box(
                    modifier = Modifier.size(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension * 0.44f
                        drawCircle(
                            color = colors.electricBlue.copy(alpha = 0.25f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = colors.neonCyan.copy(alpha = 0.14f),
                            radius = radius * 0.72f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        rotate(degrees = -heading, pivot = center) {
                            // North needle (Orange)
                            val northPath = Path().apply {
                                moveTo(center.x, center.y - radius * 0.82f)
                                lineTo(center.x - 10.dp.toPx(), center.y)
                                lineTo(center.x + 10.dp.toPx(), center.y)
                                close()
                            }
                            drawPath(northPath, color = colors.premiumOrange)

                            // South needle (Cyan)
                            val southPath = Path().apply {
                                moveTo(center.x, center.y + radius * 0.75f)
                                lineTo(center.x - 10.dp.toPx(), center.y)
                                lineTo(center.x + 10.dp.toPx(), center.y)
                                close()
                            }
                            drawPath(southPath, color = colors.electricBlue.copy(alpha = 0.7f))
                        }
                        drawCircle(color = colors.neonCyan, radius = 6.dp.toPx(), center = center)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(140.dp))
                        Text(
                            text = compassState.azimuthDegrees?.let { "${it.roundToInt()}° ${compassState.cardinalDirection}" }
                                ?: "Calibrating...",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.neonCyan
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryStatTile(
                        label = "HEADING",
                        value = compassState.azimuthDegrees?.let { "${it.roundToInt()}°" } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryStatTile(
                        label = "DIRECTION",
                        value = compassState.cardinalDirection,
                        valueColor = colors.premiumOrange,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (compassState.needsCalibration) {
                    Text(
                        text = "Calibration recommended: Rotate your phone slowly in a figure-8 motion away from metal mounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warningAmber
                    )
                } else {
                    Text(
                        text = "Tip: Keep magnetic phone mounts away from the compass sensor for best accuracy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedometerToolCard(
    gpsState: GpsTelemetryState,
    useMetricDefault: Boolean,
    settings: AppSettings,
    onResetStats: () -> Unit
) {
    val colors = LocalRiderColors.current
    var useMetric by rememberSaveable(useMetricDefault) { mutableStateOf(useMetricDefault) }
    val unit = TelemetryFormatters.speedUnitLabel(useMetric)

    GlassTelemetryCard(
        glowTone = GlowTone.ORANGE_PRIORITY,
        animationIntensity = settings.animationIntensity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitleBar(
                title = "GPS Speedometer",
                subtitle = "Real Location.getSpeed() telemetry in $unit",
                icon = Icons.Rounded.Speed,
                accentColor = colors.premiumOrange
            )

            Text(
                text = TelemetryFormatters.formatSpeedValue(gpsState.speedMps, useMetric),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = colors.neonCyan
            )
            Text(
                text = unit.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = colors.premiumOrange
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "MAX SPEED",
                    value = "${TelemetryFormatters.formatSpeedValue(gpsState.maxSpeedMps, useMetric)} $unit",
                    valueColor = colors.successGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "AVG SPEED",
                    value = "${TelemetryFormatters.formatSpeedValue(gpsState.avgSpeedMps, useMetric)} $unit",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { useMetric = !useMetric },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (useMetric) "Switch to mph" else "Switch to km/h")
                }
                OutlinedButton(
                    onClick = onResetStats,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset Trip")
                }
            }
        }
    }
}

@Composable
private fun AltitudeToolCard(
    gpsState: GpsTelemetryState,
    settings: AppSettings
) {
    val colors = LocalRiderColors.current
    GlassTelemetryCard(
        glowTone = GlowTone.CYAN_BLUE,
        animationIntensity = settings.animationIntensity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitleBar(
                title = "GPS Altitude Meter",
                subtitle = "WGS84 ellipsoid altitude & vertical accuracy when provided by GNSS",
                icon = Icons.Rounded.Terrain,
                accentColor = colors.neonCyan
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "ALTITUDE",
                    value = TelemetryFormatters.formatAltitude(gpsState.altitudeMeters, settings.useMetricUnits),
                    valueColor = colors.neonCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "VERTICAL ACCURACY",
                    value = TelemetryFormatters.formatDistanceMeters(
                        gpsState.verticalAccuracyMeters,
                        settings.useMetricUnits
                    ),
                    valueColor = colors.successGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = if (gpsState.altitudeMeters == null) {
                    "Altitude unavailable until a 3D GNSS fix (4+ satellites) is acquired."
                } else {
                    "Source: ${gpsState.providerName ?: "GNSS"} • Updated ${TelemetryFormatters.formatAgeSeconds(gpsState.locationAgeSeconds)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfflineGpsMapCard(
    gpsState: GpsTelemetryState,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onOpenCoordinateInMaps: (Double, Double) -> Boolean
) {
    val colors = LocalRiderColors.current
    val scope = rememberCoroutineScope()

    GlassTelemetryCard(
        glowTone = GlowTone.CYAN_BLUE,
        animationIntensity = settings.animationIntensity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitleBar(
                title = "Offline Coordinate & Accuracy Grid",
                subtitle = "Zero-API offline radar plot + external maps handoff when online",
                icon = Icons.Rounded.Map,
                accentColor = colors.neonCyan
            )

            // Offline coordinate radar plot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.deepBackground),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 28.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = colors.electricBlue.copy(alpha = 0.14f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = colors.electricBlue.copy(alpha = 0.14f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                    val center = Offset(size.width / 2f, size.height / 2f)
                    if (gpsState.isLocationAvailable) {
                        val acc = gpsState.horizontalAccuracyMeters ?: 20f
                        val ringRadius = (acc.coerceIn(5f, 80f) / 80f) * (size.minDimension * 0.42f)
                        drawCircle(
                            color = colors.neonCyan.copy(alpha = 0.16f),
                            radius = ringRadius,
                            center = center
                        )
                        drawCircle(
                            color = colors.neonCyan,
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawCircle(
                            color = colors.premiumOrange,
                            radius = 6.dp.toPx(),
                            center = center
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(125.dp))
                    Text(
                        text = TelemetryFormatters.formatCoordinates(gpsState.latitude, gpsState.longitude),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neonCyan
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "ACCURACY RADIUS",
                    value = TelemetryFormatters.formatDistanceMeters(
                        gpsState.horizontalAccuracyMeters,
                        settings.useMetricUnits
                    ),
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "PROVIDER",
                    value = gpsState.providerName?.uppercase() ?: "NONE",
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val lat = gpsState.latitude
                    val lon = gpsState.longitude
                    if (lat != null && lon != null) {
                        val opened = onOpenCoordinateInMaps(lat, lon)
                        if (!opened) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No map application installed to open coordinates.")
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Waiting for a valid GPS location before opening maps.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue)
            ) {
                Text("Open Coordinates in Installed Map App")
            }
        }
    }
}

@Composable
private fun DeviceDiagnosticsToolCard(
    systemState: SystemCheckState,
    networkState: NetworkDiagnosticsState,
    settings: AppSettings,
    onRefresh: () -> Unit
) {
    val colors = LocalRiderColors.current
    GlassTelemetryCard(
        glowTone = GlowTone.CYAN_BLUE,
        animationIntensity = settings.animationIntensity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitleBar(
                title = "Device Hardware & Battery Diagnostics",
                subtitle = "BatteryManager, PowerManager & LocationManager providers",
                icon = Icons.Rounded.BatteryChargingFull,
                accentColor = colors.neonCyan
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "BATTERY LEVEL",
                    value = if (systemState.batteryLevelPercent >= 0) "${systemState.batteryLevelPercent}%" else "Unknown",
                    valueColor = if (systemState.batteryLevelPercent in 0..20) colors.errorRed else colors.successGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "CHARGING",
                    value = systemState.chargingTypeLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "BATTERY SAVER",
                    value = if (systemState.isPowerSaveMode) "ON" else "OFF",
                    valueColor = if (systemState.isPowerSaveMode) colors.warningAmber else colors.successGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "NETWORK TYPE",
                    value = networkState.transportType.label,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Available Location Providers: ${
                    if (systemState.availableLocationProviders.isEmpty()) "None"
                    else systemState.availableLocationProviders.joinToString(", ")
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Device Diagnostics")
            }
        }
    }
}
