package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.BatteryIntelligenceState
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.DualSimState
import com.example.domain.model.PerformancePeriodSummary
import com.example.domain.model.RiderWorkspaceSummary
import com.example.domain.model.RiderZonesState
import com.example.ui.theme.LocalRiderColors

enum class SuperBoosterModule(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    INTELLIGENCE("intelligence", "Intelligence", Icons.Rounded.Memory, "hub_intelligence"),
    DUAL_SIM("dual_sim", "Dual SIM", Icons.Rounded.SimCard, "hub_dual_sim"),
    ZONES("rider_zones", "Rider Zones", Icons.Rounded.LocationSearching, "hub_rider_zones"),
    BATTERY("battery_health", "Battery Health", Icons.Rounded.BatteryChargingFull, "hub_battery_health"),
    WORKSPACE("rider_workspace", "Workspace", Icons.Rounded.WorkHistory, "hub_rider_workspace"),
    REPORTS("performance_reports", "Reports", Icons.Rounded.Analytics, "hub_performance_reports")
}

@Composable
fun SuperBoosterHubCard(
    deviceState: DeviceIntelligenceState,
    dualSimState: DualSimState,
    zonesState: RiderZonesState,
    batteryState: BatteryIntelligenceState,
    workspaceSummary: RiderWorkspaceSummary,
    hasActiveSession: Boolean,
    dailyReport: PerformancePeriodSummary,
    animationIntensity: AnimationIntensity,
    onNavigateModule: (SuperBoosterModule) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    GlassTelemetryCard(
        glowTone = GlowTone.CYAN_BLUE,
        animationIntensity = animationIntensity,
        modifier = modifier
            .fillMaxWidth()
            .testTag("super_booster_hub_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitleBar(
                title = "Super Booster Intelligence",
                subtitle = "Hardware Health, Dual SIM, Zones, Battery, Workspace & Reports",
                icon = Icons.Rounded.Memory,
                accentColor = colors.neonCyan
            )

            val activeSim = dualSimState.simSlots.firstOrNull { it.isDefaultData } ?: dualSimState.simSlots.firstOrNull()
            val items = listOf(
                Triple(
                    SuperBoosterModule.INTELLIGENCE,
                    "Score ${deviceState.performanceHealthScore}/100",
                    deviceState.thermalStatusLabel
                ),
                Triple(
                    SuperBoosterModule.DUAL_SIM,
                    "${dualSimState.activeSubscriptionCount} SIM${if (dualSimState.activeSubscriptionCount == 1) "" else "s"}",
                    activeSim?.signalStrengthDbm?.let { "$it dBm" } ?: if (dualSimState.hasPhoneStatePermission) "Ready" else "Perm Needed"
                ),
                Triple(
                    SuperBoosterModule.ZONES,
                    "${zonesState.zoneCells.size} Zones",
                    if (zonesState.repeatedWeakZoneWarnings.isNotEmpty()) "${zonesState.repeatedWeakZoneWarnings.size} Weak" else "Local Only"
                ),
                Triple(
                    SuperBoosterModule.BATTERY,
                    if (batteryState.levelPercent >= 0) "${batteryState.levelPercent}%" else "--",
                    batteryState.temperatureC?.let { String.format("%.1f°C", it) } ?: batteryState.chargingSource
                ),
                Triple(
                    SuperBoosterModule.WORKSPACE,
                    if (hasActiveSession) "LIVE SHIFT" else "${workspaceSummary.todayWorkingMinutes}m Today",
                    "${workspaceSummary.totalSessionsToday} Sessions"
                ),
                Triple(
                    SuperBoosterModule.REPORTS,
                    dailyReport.readinessScore?.let { "$it% Ready" } ?: "No Data",
                    "Daily & Weekly"
                )
            )

            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (mod, metric, sub) ->
                        HubQuickTile(
                            module = mod,
                            metric = metric,
                            subtitle = sub,
                            onClick = { onNavigateModule(mod) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubQuickTile(
    module: SuperBoosterModule,
    metric: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.navyBackground.copy(alpha = if (colors.isDark) 0.78f else 0.94f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(module.testTag),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = module.icon,
                contentDescription = module.title,
                tint = colors.neonCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = module.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = metric,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.premiumOrange,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SuperBoosterSubScreenHeader(
    title: String,
    subtitle: String,
    activeModule: SuperBoosterModule,
    pillLabel: String,
    pillColor: Color,
    animationIntensity: AnimationIntensity,
    onBack: () -> Unit,
    onSelectModule: (SuperBoosterModule) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("intel_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.neonCyan
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.neonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            LiveStatusPill(
                label = pillLabel,
                toneColor = pillColor,
                pulse = false,
                animationIntensity = animationIntensity
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuperBoosterModule.entries.forEach { mod ->
                val selected = mod == activeModule
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onSelectModule(mod) },
                    label = { Text(mod.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = mod.icon,
                            contentDescription = mod.title,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.electricBlue.copy(alpha = 0.25f),
                        selectedLabelColor = colors.neonCyan,
                        selectedLeadingIconColor = colors.neonCyan
                    ),
                    modifier = Modifier.testTag("chip_${mod.route}")
                )
            }
        }
    }
}

@Composable
fun AnimatedSparklineChart(
    values: List<Float>,
    minValue: Float = 0f,
    maxValue: Float = 100f,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.deepBackground.copy(alpha = 0.7f))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (values.size < 2) {
            Text(
                text = "Collecting live telemetry samples...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val range = (maxValue - minValue).coerceAtLeast(1f)
                val stepX = size.width / (values.size - 1).coerceAtLeast(1)

                // Subtle reference lines
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val y = size.height * frac
                    drawLine(
                        color = colors.electricBlue.copy(alpha = 0.14f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                val strokePath = Path()
                val fillPath = Path()
                values.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val norm = ((v - minValue) / range).coerceIn(0f, 1f)
                    val y = size.height - (norm * size.height)
                    if (idx == 0) {
                        strokePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == values.lastIndex) {
                        fillPath.lineTo(x, size.height)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
