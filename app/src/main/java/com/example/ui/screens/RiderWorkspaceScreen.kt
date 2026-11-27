package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SessionTimelineEventEntity
import com.example.data.local.WorkSessionEntity
import com.example.domain.model.AppSettings
import com.example.domain.model.RiderWorkspaceSummary
import com.example.engine.RiderWorkspaceEngine
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SuperBoosterModule
import com.example.ui.components.SuperBoosterSubScreenHeader
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters
import kotlinx.coroutines.launch

@Composable
fun RiderWorkspaceScreen(
    activeSession: WorkSessionEntity?,
    liveDurationMs: Long,
    workspaceSummary: RiderWorkspaceSummary,
    allSessions: List<WorkSessionEntity>,
    timelineEvents: List<SessionTimelineEventEntity>,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onStartSession: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onEndSession: () -> Unit,
    onExportCsv: () -> Boolean,
    onExportPdf: () -> Boolean,
    onSelectModule: (SuperBoosterModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalRiderColors.current
    val scope = rememberCoroutineScope()

    val statusPill = when (activeSession?.status) {
        "ACTIVE" -> "SHIFT ACTIVE" to colors.successGreen
        "PAUSED" -> "PAUSED" to colors.warningAmber
        else -> "IDLE" to colors.neonCyan
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SuperBoosterSubScreenHeader(
                title = "SMART RIDER WORKSPACE",
                subtitle = "Local Work Session Timer, Timelines & CSV/PDF Report Export",
                activeModule = SuperBoosterModule.WORKSPACE,
                pillLabel = statusPill.first,
                pillColor = statusPill.second,
                animationIntensity = settings.animationIntensity,
                onBack = onBack,
                onSelectModule = onSelectModule
            )
        }

        // Active Work Session Controls Card
        item {
            GlassTelemetryCard(
                glowTone = if (activeSession?.status == "ACTIVE") GlowTone.SUCCESS else GlowTone.ORANGE_PRIORITY,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("workspace_session_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "Work Session Control",
                        subtitle = "Tracks device readiness, working hours, GPS/network drops & thermal events locally",
                        icon = Icons.Rounded.WorkHistory,
                        accentColor = colors.premiumOrange
                    )

                    Text(
                        text = formatDurationHms(liveDurationMs),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (activeSession?.status == "ACTIVE") colors.successGreen else colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "TODAY HOURS",
                            value = formatMinutesHm(workspaceSummary.todayWorkingMinutes),
                            valueColor = colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "TODAY SESSIONS",
                            value = "${workspaceSummary.totalSessionsToday}",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "ALL SESSIONS",
                            value = "${workspaceSummary.totalHistoricalSessions}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    when (activeSession?.status) {
                        null -> {
                            Button(
                                onClick = onStartSession,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_work_session_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Start work session")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Work Session")
                            }
                        }
                        "ACTIVE" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPauseSession,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pause_work_session_button")
                                ) {
                                    Icon(Icons.Rounded.Pause, contentDescription = "Pause session")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause")
                                }
                                Button(
                                    onClick = onEndSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.errorRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("end_work_session_button")
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "End session")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("End Session")
                                }
                            }
                        }
                        "PAUSED" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onResumeSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resume_work_session_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume session")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume")
                                }
                                OutlinedButton(
                                    onClick = onEndSession,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("end_work_session_button")
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "End session")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("End")
                                }
                            }
                        }
                    }

                    // Export CSV / PDF Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val ok = onExportCsv()
                                if (!ok) {
                                    scope.launch { snackbarHostState.showSnackbar("Unable to export CSV report.") }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_csv_button")
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = "Export CSV")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            onClick = {
                                val ok = onExportPdf()
                                if (!ok) {
                                    scope.launch { snackbarHostState.showSnackbar("Unable to export PDF report.") }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }

                    Text(
                        text = "Independence Notice: Rider Workspace tracks your phone's GPS, network, thermal & battery logs only. It never accesses Talabat account or order records.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Session GPS / Network / Thermal Timeline
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("workspace_timeline_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitleBar(
                        title = "GPS, Network & Thermal Timeline",
                        subtitle = "Chronological session events recorded on-device",
                        icon = Icons.Rounded.Timeline,
                        accentColor = colors.neonCyan
                    )
                    if (timelineEvents.isEmpty()) {
                        Text(
                            text = "No session timeline events recorded yet. Start a Work Session to track GNSS lock transitions, network interruptions, and thermal alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        timelineEvents.take(20).forEach { ev ->
                            val badgeColor = when (ev.severity) {
                                "CRITICAL" -> colors.errorRed
                                "WARNING" -> colors.warningAmber
                                "RECOVERY" -> colors.successGreen
                                else -> colors.neonCyan
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.navyBackground.copy(alpha = 0.72f))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "[${ev.category}] ${ev.title}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = badgeColor
                                    )
                                    Text(
                                        text = TelemetryFormatters.formatTime(ev.timestampMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = ev.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Work Sessions Summary List
        items(allSessions.take(15), key = { it.id }) { session ->
            val mins = RiderWorkspaceEngine.computeEffectiveDurationMs(session, System.currentTimeMillis()) / 60_000L
            val avgAcc = if (session.accuracySampleCount > 0) {
                "±${(session.totalAccuracySumMeters / session.accuracySampleCount).toInt()}m"
            } else "--"
            GlassTelemetryCard(
                glowTone = if (session.status == "ACTIVE") GlowTone.SUCCESS else GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Session #${session.id} (${session.status})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.neonCyan
                        )
                        Text(
                            text = formatMinutesHm(mins),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.premiumOrange
                        )
                    }
                    Text(
                        text = "Started ${TelemetryFormatters.formatTime(session.startTimeMs)} • Avg GPS $avgAcc • Net Drops ${session.networkLossCount} • Overheats ${session.overheatEventCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDurationHms(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatMinutesHm(mins: Long): String {
    val h = mins / 60L
    val m = mins % 60L
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
