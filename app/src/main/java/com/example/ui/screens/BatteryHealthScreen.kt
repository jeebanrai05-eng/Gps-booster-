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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
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
import com.example.data.local.BatteryHistoryEntity
import com.example.domain.model.AppSettings
import com.example.domain.model.BatteryIntelligenceState
import com.example.ui.components.AnimatedSparklineChart
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SuperBoosterModule
import com.example.ui.components.SuperBoosterSubScreenHeader
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters

@Composable
fun BatteryHealthScreen(
    batteryState: BatteryIntelligenceState,
    batteryHistory: List<BatteryHistoryEntity>,
    settings: AppSettings,
    onRefresh: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSelectModule: (SuperBoosterModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalRiderColors.current

    val pillColor = when {
        batteryState.lowBatteryAlert != null || batteryState.thermalAlert != null -> colors.errorRed
        batteryState.isCharging -> colors.neonCyan
        else -> colors.successGreen
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SuperBoosterSubScreenHeader(
                title = "SMART BATTERY INTELLIGENCE",
                subtitle = "BatteryManager Telemetry, Thermal Alerts & Observed Drain Rate",
                activeModule = SuperBoosterModule.BATTERY,
                pillLabel = if (batteryState.levelPercent >= 0) "${batteryState.levelPercent}%" else "UNKNOWN",
                pillColor = pillColor,
                animationIntensity = settings.animationIntensity,
                onBack = onBack,
                onSelectModule = onSelectModule
            )
        }

        if (batteryState.lowBatteryAlert != null || batteryState.thermalAlert != null) {
            item {
                GlassTelemetryCard(
                    glowTone = GlowTone.ERROR,
                    animationIntensity = settings.animationIntensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .testTag("battery_alerts_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitleBar(
                            title = "Battery & Thermal Alerts",
                            subtitle = "Live threshold monitoring",
                            icon = Icons.Rounded.Warning,
                            accentColor = colors.errorRed
                        )
                        batteryState.lowBatteryAlert?.let {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.errorRed)
                        }
                        batteryState.thermalAlert?.let {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.warningAmber)
                        }
                    }
                }
            }
        }

        item {
            GlassTelemetryCard(
                glowTone = if (batteryState.isCharging) GlowTone.CYAN_BLUE else GlowTone.ORANGE_PRIORITY,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("battery_live_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "Live Battery & Power Telemetry",
                        subtitle = batteryState.chargingSource,
                        icon = Icons.Rounded.BatteryChargingFull,
                        accentColor = colors.premiumOrange
                    )

                    LinearProgressIndicator(
                        progress = { ((batteryState.levelPercent.coerceAtLeast(0)) / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (batteryState.levelPercent in 0..20) colors.errorRed else colors.successGreen
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "LEVEL",
                            value = if (batteryState.levelPercent >= 0) "${batteryState.levelPercent}%" else "Unavailable",
                            valueColor = if (batteryState.levelPercent in 0..20) colors.errorRed else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "TEMPERATURE",
                            value = batteryState.temperatureC?.let { String.format("%.1f °C", it) } ?: "Unavailable",
                            valueColor = if ((batteryState.temperatureC ?: 0f) >= 41f) colors.warningAmber else colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "VOLTAGE",
                            value = batteryState.voltageMv?.let { "${String.format("%.2f", it / 1000f)} V" } ?: "Unavailable",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "CURRENT",
                            value = if (batteryState.currentSupported && batteryState.currentMicroAmps != null) {
                                "${batteryState.currentMicroAmps / 1000} mA"
                            } else "Unsupported",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TelemetryStatTile(
                        label = "ESTIMATED REMAINING RUNTIME (ESTIMATE)",
                        value = batteryState.estimationLabel,
                        valueColor = colors.neonCyan,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Note: Estimated runtime is derived solely from observed %/hr discharge during non-charging telemetry. This app never alters charging speed or hardware capacity.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("refresh_battery_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh battery")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                        Button(
                            onClick = onOpenBatterySettings,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_battery_opt_button")
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Open battery optimization settings")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Battery Settings")
                        }
                    }
                }
            }
        }

        // Battery Drain History Chart & Log
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("battery_history_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Battery Drain History",
                        subtitle = "Persisted locally during active Rider Mode & Work Sessions",
                        icon = Icons.Rounded.History,
                        accentColor = colors.neonCyan
                    )
                    val chartValues = batteryHistory.reversed().takeLast(24).map { it.levelPercent.toFloat() }
                    AnimatedSparklineChart(
                        values = chartValues,
                        minValue = 0f,
                        maxValue = 100f,
                        lineColor = colors.premiumOrange
                    )
                }
            }
        }

        items(batteryHistory.take(15), key = { it.id }) { item ->
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${item.levelPercent}% • ${item.chargingSource}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.neonCyan
                        )
                        Text(
                            text = "${TelemetryFormatters.formatTime(item.timestampMs)} • ${item.temperatureC?.let { String.format("%.1f°C", it) } ?: "Temp N/A"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.isPowerSave) {
                        Text(
                            text = "Power Save",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.warningAmber
                        )
                    }
                }
            }
        }
    }
}
