package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.CompletedDiagnosticReport
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.SmartDiagnosticState
import com.example.domain.model.StepStatus
import com.example.ui.theme.LocalRiderColors

@Composable
fun SmartDiagnosticProgressSection(
    diagnosticState: SmartDiagnosticState,
    animationIntensity: AnimationIntensity,
    onActionClick: (DiagnosticActionType) -> Unit,
    onDismissReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current

    AnimatedVisibility(
        visible = diagnosticState.isRunning || diagnosticState.report != null,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (diagnosticState.isRunning) {
                GlassTelemetryCard(
                    glowTone = GlowTone.ORANGE_PRIORITY,
                    animationIntensity = animationIntensity,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SMART DIAGNOSTICS IN PROGRESS",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.premiumOrange
                            )
                            Text(
                                text = "${(diagnosticState.progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.neonCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { diagnosticState.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.neonCyan,
                            trackColor = colors.navyBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        diagnosticState.steps.forEach { step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (step.status) {
                                    StepStatus.PASS -> Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Passed",
                                        tint = colors.successGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    StepStatus.WARNING -> Icon(
                                        Icons.Rounded.Warning,
                                        contentDescription = "Warning",
                                        tint = colors.warningAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    StepStatus.FAIL -> Icon(
                                        Icons.Rounded.Error,
                                        contentDescription = "Failed",
                                        tint = colors.errorRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    StepStatus.RUNNING -> CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.neonCyan
                                    )
                                    StepStatus.PENDING -> Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.outline)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "STEP ${step.stepNumber}: ${step.title}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = step.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            diagnosticState.report?.let { report ->
                DiagnosticReportCard(
                    report = report,
                    animationIntensity = animationIntensity,
                    onActionClick = onActionClick,
                    onDismiss = onDismissReport
                )
            }
        }
    }
}

@Composable
private fun DiagnosticReportCard(
    report: CompletedDiagnosticReport,
    animationIntensity: AnimationIntensity,
    onActionClick: (DiagnosticActionType) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRiderColors.current
    val scoreColor = when {
        report.overallScore >= 85 -> colors.successGreen
        report.overallScore >= 60 -> colors.warningAmber
        else -> colors.errorRed
    }
    val glowTone = when {
        report.overallScore >= 85 -> GlowTone.SUCCESS
        report.overallScore >= 60 -> GlowTone.WARNING
        else -> GlowTone.ERROR
    }

    GlassTelemetryCard(
        glowTone = glowTone,
        animationIntensity = animationIntensity,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIAGNOSTIC REPORT",
                        style = MaterialTheme.typography.titleMedium,
                        color = scoreColor
                    )
                    Text(
                        text = "Rider Readiness Score: ${report.overallScore}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ReportMetricRow("GPS Status", report.gpsStatusSummary)
            ReportMetricRow("Location Accuracy", report.locationAccuracySummary)
            ReportMetricRow("Network Status", report.networkStatusSummary)
            ReportMetricRow("Battery Restriction", report.batteryRestrictionSummary)
            ReportMetricRow("Background Permission", report.backgroundPermissionSummary)

            if (report.detectedProblems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Detected Problems",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.errorRed
                )
                report.detectedProblems.forEach { problem ->
                    Text(
                        text = "• $problem",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = "No critical problems detected. Your device is configured cleanly for delivery shifts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.successGreen
                )
            }

            if (report.recommendedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Recommended Actions",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.neonCyan
                )
                report.recommendedActions.forEach { action ->
                    if (action.actionType == DiagnosticActionType.MOVE_TO_OPEN_SKY) {
                        Text(
                            text = "• ${action.title}: ${action.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedButton(
                            onClick = { onActionClick(action.actionType) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = action.title)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
