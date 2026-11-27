package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppSettings
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.ThermalSeverity
import com.example.domain.model.TroubleSeverity
import com.example.domain.model.TroubleshootingItem
import com.example.ui.components.AnimatedSparklineChart
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SuperBoosterModule
import com.example.ui.components.SuperBoosterSubScreenHeader
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors

@Composable
fun IntelligenceScreen(
    deviceState: DeviceIntelligenceState,
    troubleshootingItems: List<TroubleshootingItem>,
    settings: AppSettings,
    onRefresh: () -> Unit,
    onExecuteAction: (DiagnosticActionType) -> Unit,
    onSelectModule: (SuperBoosterModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalRiderColors.current

    val scoreColor = when {
        deviceState.performanceHealthScore >= 80 -> colors.successGreen
        deviceState.performanceHealthScore >= 55 -> colors.warningAmber
        else -> colors.errorRed
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SuperBoosterSubScreenHeader(
                title = "SMART RIDER INTELLIGENCE",
                subtitle = "Real-time RAM, CPU, Thermal & Troubleshooting Engine",
                activeModule = SuperBoosterModule.INTELLIGENCE,
                pillLabel = "${deviceState.performanceHealthScore}/100 ${deviceState.performanceHealthLabel.uppercase()}",
                pillColor = scoreColor,
                animationIntensity = settings.animationIntensity,
                onBack = onBack,
                onSelectModule = onSelectModule
            )
        }

        // Warnings banner when overheating or performance degradation detected
        if (deviceState.overheatingWarning != null || deviceState.degradationAlert != null) {
            item {
                GlassTelemetryCard(
                    glowTone = if (deviceState.thermalSeverity == ThermalSeverity.CRITICAL ||
                        deviceState.thermalSeverity == ThermalSeverity.SEVERE
                    ) GlowTone.ERROR else GlowTone.WARNING,
                    animationIntensity = settings.animationIntensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .testTag("device_alert_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitleBar(
                            title = "Active Device Warnings",
                            subtitle = "Live hardware health alerts",
                            icon = Icons.Rounded.Warning,
                            accentColor = colors.warningAmber
                        )
                        deviceState.overheatingWarning?.let {
                            Text(text = "• $it", style = MaterialTheme.typography.bodySmall, color = colors.errorRed)
                        }
                        deviceState.degradationAlert?.let {
                            Text(text = "• $it", style = MaterialTheme.typography.bodySmall, color = colors.warningAmber)
                        }
                    }
                }
            }
        }

        // RAM & CPU Performance Card
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("ram_cpu_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "Memory & CPU Intelligence",
                        subtitle = deviceState.cpuSourceLabel,
                        icon = Icons.Rounded.Memory,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "RAM USED",
                            value = "${deviceState.ramUsagePercent}% (${deviceState.usedRamMb} MB)",
                            valueColor = if (deviceState.ramUsagePercent >= 85) colors.warningAmber else colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "AVAILABLE RAM",
                            value = "${deviceState.availableRamMb} / ${deviceState.totalRamMb} MB",
                            valueColor = if (deviceState.isLowMemory) colors.errorRed else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (deviceState.ramUsagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = if (deviceState.ramUsagePercent >= 85) colors.warningAmber else colors.neonCyan
                    )

                    AnimatedSparklineChart(
                        values = deviceState.ramHistory.map { it.toFloat() },
                        minValue = 0f,
                        maxValue = 100f,
                        lineColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "CPU ESTIMATION",
                            value = if (deviceState.cpuUsageAvailable && deviceState.cpuUsagePercent != null) {
                                "${String.format("%.1f", deviceState.cpuUsagePercent)}%"
                            } else "Unavailable",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "HEALTH SCORE",
                            value = "${deviceState.performanceHealthScore}/100 (${deviceState.performanceHealthLabel})",
                            valueColor = scoreColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Thermal & Storage Card
        item {
            GlassTelemetryCard(
                glowTone = if (deviceState.thermalSeverity == ThermalSeverity.MODERATE) GlowTone.WARNING else GlowTone.ORANGE_PRIORITY,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("thermal_storage_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "Thermal & Storage Monitor",
                        subtitle = deviceState.cpuTempSourceLabel,
                        icon = Icons.Rounded.Thermostat,
                        accentColor = colors.premiumOrange
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "THERMAL STATUS",
                            value = deviceState.thermalStatusLabel,
                            valueColor = when (deviceState.thermalSeverity) {
                                ThermalSeverity.CRITICAL, ThermalSeverity.SEVERE -> colors.errorRed
                                ThermalSeverity.MODERATE -> colors.warningAmber
                                ThermalSeverity.NOMINAL -> colors.successGreen
                                ThermalSeverity.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "TEMPERATURE",
                            value = deviceState.cpuTempCelsius?.let { String.format("%.1f °C", it) } ?: "Unavailable",
                            valueColor = colors.premiumOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "STORAGE FREE",
                            value = "${String.format("%.1f", deviceState.storageAvailableGb)} / ${String.format("%.1f", deviceState.storageTotalGb)} GB",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "STORAGE USED",
                            value = "${deviceState.storageUsedPercent}%",
                            valueColor = if (deviceState.storageUsedPercent >= 90) colors.warningAmber else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("refresh_intel_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh device telemetry")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh Device Metrics")
                    }
                }
            }
        }

        // Intelligent Troubleshooting Engine Section
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("troubleshooting_header_card")
            ) {
                SectionTitleBar(
                    title = "Intelligent Troubleshooting Engine",
                    subtitle = "Rule-based diagnostics across live GNSS, network, RAM, SIM, battery & sessions",
                    icon = Icons.Rounded.BuildCircle,
                    accentColor = colors.neonCyan
                )
            }
        }

        items(troubleshootingItems, key = { it.id }) { item ->
            val tone = when (item.severity) {
                TroubleSeverity.CRITICAL -> GlowTone.ERROR
                TroubleSeverity.WARNING -> GlowTone.WARNING
                TroubleSeverity.OPTIMAL -> GlowTone.SUCCESS
            }
            val titleColor = when (item.severity) {
                TroubleSeverity.CRITICAL -> colors.errorRed
                TroubleSeverity.WARNING -> colors.warningAmber
                TroubleSeverity.OPTIMAL -> colors.successGreen
            }
            GlassTelemetryCard(
                glowTone = tone,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("trouble_item_${item.id}")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${item.category.uppercase()}] ${item.title}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = titleColor,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = item.severity.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = titleColor
                        )
                    }
                    Text(
                        text = item.observedEvidence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Recommendation: ${item.recommendation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.actionType != null && item.actionLabel != null) {
                        Button(
                            onClick = { onExecuteAction(item.actionType) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trouble_action_${item.id}")
                        ) {
                            Text(item.actionLabel)
                        }
                    }
                }
            }
        }
    }
}
