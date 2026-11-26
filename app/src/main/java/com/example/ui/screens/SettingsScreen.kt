package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.data.local.DiagnosticRecordEntity
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.AppSettings
import com.example.domain.model.AppUpdateUiState
import com.example.domain.model.GitHubReleaseManifest
import com.example.domain.model.UpdateCheckStatus
import com.example.ui.components.DownloadProgressSection
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.update.UpdateConfig
import com.example.util.TelemetryFormatters

@Composable
fun SettingsScreen(
    settings: AppSettings,
    updateState: AppUpdateUiState,
    history: List<DiagnosticRecordEntity>,
    onBack: () -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAnimationIntensity: (AnimationIntensity) -> Unit,
    onSetUseMetricUnits: (Boolean) -> Unit,
    onSetGpsIntervalMs: (Long) -> Unit,
    onSetAutoUpdateCheck: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: (GitHubReleaseManifest) -> Unit,
    onOpenInstallUnknownSettings: () -> Unit,
    onRetryInstallVerifiedApk: () -> Unit,
    onOpenReleasesPage: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Local History?") },
            text = { Text("This deletes all locally stored diagnostic snapshots from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear", color = colors.errorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.neonCyan
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "SETTINGS & PRIVACY",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Preferences, Battery Controls & Local Data",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.neonCyan
                    )
                }
            }
        }

        // Visual & Units
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "Display & Units",
                        icon = Icons.Rounded.Palette,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Futuristic Theme", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Recommended for night shifts & OLED battery savings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.darkTheme,
                            onCheckedChange = onSetDarkTheme,
                            modifier = Modifier.testTag("dark_theme_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Metric Units (km/h, meters)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (settings.useMetricUnits) "Active: km/h & m" else "Active: mph & ft",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.useMetricUnits,
                            onCheckedChange = onSetUseMetricUnits
                        )
                    }

                    Text(
                        text = "Animation Intensity",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimationIntensity.entries.forEach { intensity ->
                            FilterChip(
                                selected = settings.animationIntensity == intensity,
                                onClick = { onSetAnimationIntensity(intensity) },
                                label = { Text(intensity.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // GPS Monitoring Interval
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "GPS Monitoring Interval",
                        subtitle = "Balances responsiveness against battery draw while app is open",
                        icon = Icons.Rounded.Timer,
                        accentColor = colors.electricBlue
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1000L to "1s Fast", 2000L to "2s Balanced", 5000L to "5s Eco").forEach { (ms, label) ->
                            FilterChip(
                                selected = settings.gpsMonitoringIntervalMs == ms,
                                onClick = { onSetGpsIntervalMs(ms) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // GitHub Update Settings Section
        item {
            val latestManifest = when (val st = updateState.checkStatus) {
                is UpdateCheckStatus.UpdateAvailable -> st.manifest
                is UpdateCheckStatus.UpToDate -> st.latestManifest
                else -> updateState.cachedManifest
            }
            GlassTelemetryCard(
                glowTone = if (updateState.checkStatus is UpdateCheckStatus.UpdateAvailable) GlowTone.SUCCESS else GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("settings_update_section")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Update Settings",
                        subtitle = "GitHub Release self-update verification",
                        icon = Icons.Rounded.SystemUpdate,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "CURRENT APP VERSION",
                            value = "v${updateState.currentVersionName} (${updateState.currentVersionCode})",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "LATEST GITHUB VERSION",
                            value = latestManifest?.let { "v${it.versionName} (${it.versionCode})" } ?: "Unchecked",
                            valueColor = if (updateState.checkStatus is UpdateCheckStatus.UpdateAvailable) colors.successGreen else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Update Check", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Throttled background check on app open over HTTPS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = updateState.autoUpdateCheckEnabled,
                            onCheckedChange = onSetAutoUpdateCheck,
                            modifier = Modifier.testTag("auto_update_switch")
                        )
                    }

                    Text(
                        text = "Last Update Check: " + (updateState.lastCheckTimestampMs?.let {
                            TelemetryFormatters.formatDateTime(it)
                        } ?: "Never"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (latestManifest != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.navyBackground.copy(alpha = 0.7f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Release Notes (${latestManifest.releaseTitle})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.neonCyan
                            )
                            Text(
                                text = latestManifest.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "GitHub Release Info: ${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPOSITORY}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "GitHub Release Info: ${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPOSITORY}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val checkStatus = updateState.checkStatus
                    if (checkStatus is UpdateCheckStatus.Error) {
                        Text(
                            text = checkStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.warningAmber
                        )
                    }

                    DownloadProgressSection(
                        downloadStatus = updateState.downloadStatus,
                        onOpenInstallUnknownSettings = onOpenInstallUnknownSettings,
                        onRetryInstallVerifiedApk = onRetryInstallVerifiedApk
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = updateState.checkStatus != UpdateCheckStatus.Checking,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_check_updates_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (updateState.checkStatus == UpdateCheckStatus.Checking) "Checking..."
                                else "Check for Updates"
                            )
                        }

                        if (checkStatus is UpdateCheckStatus.UpdateAvailable) {
                            Button(
                                onClick = { onDownloadAndInstallUpdate(checkStatus.manifest) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.successGreen)
                            ) {
                                Text("Update Now", color = MaterialTheme.colorScheme.onSecondary)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onOpenReleasesPage,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Releases")
                            }
                        }
                    }
                }
            }
        }

        // Local Diagnostic History
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitleBar(
                            title = "Local Diagnostic History (${history.size})",
                            subtitle = "Stored strictly on-device in Room SQLite",
                            icon = Icons.Rounded.History,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (history.isEmpty()) {
                        Text(
                            text = "No diagnostic reports saved yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        history.take(8).forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.navyBackground.copy(alpha = 0.65f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${TelemetryFormatters.formatDateTime(entry.timestampMs)} • Score ${entry.overallScore}/100",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "${entry.gpsStatus} | ${entry.networkStatus}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_history_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear Local History", color = colors.errorRed)
                        }
                    }
                }
            }
        }

        // Privacy, Permissions & About
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.SUCCESS,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Privacy & Permissions",
                        icon = Icons.Rounded.PrivacyTip,
                        accentColor = colors.successGreen
                    )
                    Text(
                        text = "• Diagnostic logs never leave your device.\n• Zero hidden background location tracking.\n• No ads, no analytics SDKs, no accessibility permissions, and no root required.\n• Location monitoring automatically pauses when you leave the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenAppPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage App Permissions in Android Settings")
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ultimate Rider Booster v${updateState.currentVersionName} • Stay Connected. Stay Accurate. Stay Ready.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
