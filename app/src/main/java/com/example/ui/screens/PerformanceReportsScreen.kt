package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppSettings
import com.example.domain.model.PerformancePeriodSummary
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SuperBoosterModule
import com.example.ui.components.SuperBoosterSubScreenHeader
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors

@Composable
fun PerformanceReportsScreen(
    dailyReport: PerformancePeriodSummary,
    weeklyReport: PerformancePeriodSummary,
    settings: AppSettings,
    onOpenWorkspace: () -> Unit,
    onSelectModule: (SuperBoosterModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalRiderColors.current
    var showWeekly by rememberSaveable { mutableStateOf(false) }
    val activeReport = if (showWeekly) weeklyReport else dailyReport

    val scoreColor = when {
        activeReport.readinessScore == null -> MaterialTheme.colorScheme.onSurfaceVariant
        activeReport.readinessScore >= 85 -> colors.successGreen
        activeReport.readinessScore >= 65 -> colors.warningAmber
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
                title = "RIDER PERFORMANCE REPORTS",
                subtitle = "Daily & Weekly Device Readiness Summaries",
                activeModule = SuperBoosterModule.REPORTS,
                pillLabel = activeReport.readinessScore?.let { "$it% READY" } ?: "NO DATA",
                pillColor = scoreColor,
                animationIntensity = settings.animationIntensity,
                onBack = onBack,
                onSelectModule = onSelectModule
            )
        }

        // Period Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = !showWeekly,
                    onClick = { showWeekly = false },
                    label = { Text("Today (${dailyReport.sessionsCount} sessions)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.electricBlue.copy(alpha = 0.25f),
                        selectedLabelColor = colors.neonCyan
                    ),
                    modifier = Modifier.weight(1f).testTag("report_daily_tab")
                )
                FilterChip(
                    selected = showWeekly,
                    onClick = { showWeekly = true },
                    label = { Text("Last 7 Days (${weeklyReport.sessionsCount} sessions)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.electricBlue.copy(alpha = 0.25f),
                        selectedLabelColor = colors.neonCyan
                    ),
                    modifier = Modifier.weight(1f).testTag("report_weekly_tab")
                )
            }
        }

        // Metrics Summary Card
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("report_metrics_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "${activeReport.periodLabel} Device Readiness",
                        subtitle = "Computed exclusively from your persisted local session & sensor telemetry",
                        icon = Icons.Rounded.Analytics,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "AVG GPS ACCURACY",
                            value = activeReport.avgGpsAccuracyMeters?.let { "±${String.format("%.1f", it)}m" } ?: "No Data",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "GNSS FIX AVAIL.",
                            value = activeReport.gnssFixAvailabilityPercent?.let { "$it%" } ?: "No Data",
                            valueColor = colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "NET AVAILABILITY",
                            value = activeReport.networkAvailabilityPercent?.let { "$it%" } ?: "No Data",
                            valueColor = colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "NET INTERRUPTIONS",
                            value = "${activeReport.networkInterruptionCount}",
                            valueColor = if (activeReport.networkInterruptionCount > 0) colors.warningAmber else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "BATTERY USED",
                            value = "${activeReport.batteryConsumedPercent}%",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "OVERHEAT EVENTS",
                            value = "${activeReport.overheatEventsCount}",
                            valueColor = if (activeReport.overheatEventsCount > 0) colors.errorRed else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "RIDER DURATION",
                            value = "${activeReport.totalRiderMinutes}m",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (activeReport.sessionsCount == 0) {
                        Button(
                            onClick = onOpenWorkspace,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Rider Workspace to Start Session")
                        }
                    }
                }
            }
        }

        // Detected Problems & Suggested Improvements
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.ORANGE_PRIORITY,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("report_insights_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Detected Problems & Improvements",
                        subtitle = "Actionable findings for ${activeReport.periodLabel}",
                        icon = Icons.Rounded.TipsAndUpdates,
                        accentColor = colors.premiumOrange
                    )

                    Text(
                        text = "Detected Problems",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.warningAmber
                    )
                    activeReport.detectedProblems.forEach { p ->
                        Text("• $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Text(
                        text = "Suggested Improvements",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.neonCyan
                    )
                    activeReport.suggestedImprovements.forEach { imp ->
                        Text("• $imp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Honest Measurement Boundary Notice
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = "Report scope notice", tint = colors.neonCyan)
                    Text(
                        text = "Readiness Report Notice: These metrics reflect device GPS, network, battery & thermal readiness only—not order acceptance, dispatch priority, or delivery earnings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
