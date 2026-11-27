package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppSettings
import com.example.domain.model.RiderZonesState
import com.example.domain.model.ZoneHeatCell
import com.example.domain.model.ZoneQualityTier
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SuperBoosterModule
import com.example.ui.components.SuperBoosterSubScreenHeader
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters

@Composable
fun RiderZonesScreen(
    zonesState: RiderZonesState,
    settings: AppSettings,
    onTogglePauseRecording: (Boolean) -> Unit,
    onDeleteAllHistory: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onSelectModule: (SuperBoosterModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalRiderColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Local Zone History?") },
            text = { Text("This permanently deletes all locally recorded GPS & network zone cells from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAllHistory()
                    },
                    modifier = Modifier.testTag("confirm_delete_zones_button")
                ) {
                    Text("Delete All", color = colors.errorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SuperBoosterSubScreenHeader(
                title = "RIDER ZONE INTELLIGENCE",
                subtitle = "100% On-Device Approximate (~200m) Signal Quality Heatmap",
                activeModule = SuperBoosterModule.ZONES,
                pillLabel = when {
                    zonesState.isRecordingPaused -> "PAUSED"
                    zonesState.isRecordingActiveNow -> "RECORDING"
                    else -> "STANDBY"
                },
                pillColor = when {
                    zonesState.isRecordingPaused -> colors.warningAmber
                    zonesState.isRecordingActiveNow -> colors.successGreen
                    else -> colors.neonCyan
                },
                animationIntensity = settings.animationIntensity,
                onBack = onBack,
                onSelectModule = onSelectModule
            )
        }

        // Privacy & Recording Controls
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("zone_privacy_controls_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Local Recording & Privacy Controls",
                        subtitle = "Records ONLY during user-enabled Rider Mode or Active Work Sessions. Zero cloud uploads.",
                        icon = Icons.Rounded.Shield,
                        accentColor = colors.successGreen
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "ZONES MAPPED",
                            value = "${zonesState.zoneCells.size}",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "OBSERVATIONS",
                            value = "${zonesState.totalObservations}",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "WEAK ALERTS",
                            value = "${zonesState.repeatedWeakZoneWarnings.size}",
                            valueColor = if (zonesState.repeatedWeakZoneWarnings.isNotEmpty()) colors.warningAmber else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onTogglePauseRecording(!zonesState.isRecordingPaused) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_zone_pause_button")
                        ) {
                            Icon(
                                imageVector = if (zonesState.isRecordingPaused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                                contentDescription = "Toggle zone recording"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (zonesState.isRecordingPaused) "Resume Recording" else "Pause Recording")
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = zonesState.totalObservations > 0,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delete_zones_button")
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete zone history", tint = colors.errorRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete History")
                        }
                    }

                    if (!zonesState.isRecordingActiveNow && !zonesState.isRecordingPaused) {
                        Button(
                            onClick = onOpenWorkspace,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Work Session to Map Current Rider Zone")
                        }
                    }
                }
            }
        }

        // Local Signal Heatmap Canvas
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("zone_heatmap_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Local Signal Quality Heatmap",
                        subtitle = "Spatial distribution of your recorded ~200m rider grid cells",
                        icon = Icons.Rounded.LocationSearching,
                        accentColor = colors.neonCyan
                    )

                    ZoneHeatmapGridCanvas(cells = zonesState.zoneCells)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("● Strong", style = MaterialTheme.typography.labelSmall, color = colors.successGreen)
                        Text("● Mixed", style = MaterialTheme.typography.labelSmall, color = colors.warningAmber)
                        Text("● Weak Signal", style = MaterialTheme.typography.labelSmall, color = colors.errorRed)
                    }
                }
            }
        }

        // Repeated Weak-Signal Area Warnings
        if (zonesState.repeatedWeakZoneWarnings.isNotEmpty()) {
            item {
                GlassTelemetryCard(
                    glowTone = GlowTone.WARNING,
                    animationIntensity = settings.animationIntensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .testTag("weak_zone_warnings_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitleBar(
                            title = "Repeated Weak-Signal Zones",
                            subtitle = "Areas where GNSS accuracy or network reliability dropped repeatedly",
                            icon = Icons.Rounded.Warning,
                            accentColor = colors.warningAmber
                        )
                        zonesState.repeatedWeakZoneWarnings.take(5).forEach { cell ->
                            Text(
                                text = "• Zone ${cell.zoneKey}: ${cell.weakCount}/${cell.sampleCount} weak readings • GPS Fix ${cell.gpsFixRatePercent}% • Net OK ${cell.networkOkRatePercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warningAmber
                            )
                        }
                    }
                }
            }
        }

        // Zone Cells List
        items(zonesState.zoneCells, key = { it.zoneKey }) { cell ->
            val tone = when (cell.qualityTier) {
                ZoneQualityTier.GOOD -> GlowTone.SUCCESS
                ZoneQualityTier.MIXED -> GlowTone.WARNING
                ZoneQualityTier.WEAK -> GlowTone.ERROR
            }
            val tierColor = when (cell.qualityTier) {
                ZoneQualityTier.GOOD -> colors.successGreen
                ZoneQualityTier.MIXED -> colors.warningAmber
                ZoneQualityTier.WEAK -> colors.errorRed
            }
            GlassTelemetryCard(
                glowTone = tone,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grid ${cell.zoneKey}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.neonCyan
                        )
                        Text(
                            text = cell.qualityTier.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tierColor
                        )
                    }
                    Text(
                        text = "Samples: ${cell.sampleCount} • Avg GPS Acc: ${cell.avgAccuracyMeters?.let { "±${it.toInt()}m" } ?: "--"} • Avg Cell: ${cell.avgSignalDbm?.let { "$it dBm" } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "GPS OK: ${cell.gpsFixRatePercent}% • Net OK: ${cell.networkOkRatePercent}% • Last seen ${TelemetryFormatters.formatTime(cell.lastObservedMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneHeatmapGridCanvas(cells: List<ZoneHeatCell>) {
    val colors = LocalRiderColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.deepBackground.copy(alpha = 0.85f))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cells.isEmpty()) {
            Text(
                text = "No Rider Zones recorded yet. Enable Rider Mode or start a Work Session with GPS active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minLat = cells.minOf { it.approximateLat }
                val maxLat = cells.maxOf { it.approximateLat }
                val minLon = cells.minOf { it.approximateLon }
                val maxLon = cells.maxOf { it.approximateLon }
                val latSpan = (maxLat - minLat).takeIf { it > 0.0005 } ?: 0.01
                val lonSpan = (maxLon - minLon).takeIf { it > 0.0005 } ?: 0.01

                // Background grid
                val cols = 6
                val rows = 4
                for (c in 1 until cols) {
                    val x = size.width * (c.toFloat() / cols)
                    drawLine(colors.electricBlue.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, size.height))
                }
                for (r in 1 until rows) {
                    val y = size.height * (r.toFloat() / rows)
                    drawLine(colors.electricBlue.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y))
                }

                cells.forEach { cell ->
                    val nx = if (cells.size == 1) 0.5f else ((cell.approximateLon - minLon) / lonSpan).toFloat().coerceIn(0.08f, 0.92f)
                    val ny = if (cells.size == 1) 0.5f else (1f - ((cell.approximateLat - minLat) / latSpan).toFloat()).coerceIn(0.12f, 0.88f)
                    val cx = nx * size.width
                    val cy = ny * size.height
                    val cellColor = when (cell.qualityTier) {
                        ZoneQualityTier.GOOD -> colors.successGreen
                        ZoneQualityTier.MIXED -> colors.warningAmber
                        ZoneQualityTier.WEAK -> colors.errorRed
                    }
                    val boxSize = (22f + (cell.sampleCount * 3f)).coerceAtMost(46f)
                    drawRoundRect(
                        color = cellColor.copy(alpha = 0.28f),
                        topLeft = Offset(cx - boxSize / 2f, cy - boxSize / 2f),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawCircle(
                        color = cellColor,
                        radius = 6.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}
