package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.ApkDownloadStatus
import com.example.domain.model.AppUpdateUiState
import com.example.domain.model.GitHubReleaseManifest
import com.example.domain.model.UpdateCheckStatus
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters
import java.util.Locale

@Composable
fun AppUpdateCard(
    updateState: AppUpdateUiState,
    animationIntensity: AnimationIntensity,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onDownloadAndInstall: (GitHubReleaseManifest) -> Unit,
    onOpenInstallUnknownSettings: () -> Unit,
    onRetryInstallVerifiedApk: () -> Unit,
    onOpenReleasesPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    val checkStatus = updateState.checkStatus
    val downloadStatus = updateState.downloadStatus

    val (pillLabel, pillColor, glowTone) = when (checkStatus) {
        is UpdateCheckStatus.UpdateAvailable -> Triple("UPDATE READY", colors.successGreen, GlowTone.SUCCESS)
        UpdateCheckStatus.Checking -> Triple("CHECKING", colors.electricBlue, GlowTone.CYAN_BLUE)
        is UpdateCheckStatus.UpToDate -> Triple("UP TO DATE", colors.neonCyan, GlowTone.CYAN_BLUE)
        is UpdateCheckStatus.Error -> Triple(
            if (checkStatus.isOffline) "OFFLINE" else "CHECK NOTICE",
            colors.warningAmber,
            GlowTone.WARNING
        )
        UpdateCheckStatus.Idle -> Triple("READY", colors.neonCyan, GlowTone.CYAN_BLUE)
    }

    val latestVersionLabel = when (checkStatus) {
        is UpdateCheckStatus.UpdateAvailable -> "v${checkStatus.manifest.versionName} (${checkStatus.manifest.versionCode})"
        is UpdateCheckStatus.UpToDate -> "v${checkStatus.latestManifest.versionName} (${checkStatus.latestManifest.versionCode})"
        else -> updateState.cachedManifest?.let { "v${it.versionName} (${it.versionCode})" } ?: "Not checked yet"
    }

    GlassTelemetryCard(
        glowTone = glowTone,
        animationIntensity = animationIntensity,
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_update_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitleBar(
                    title = "APP UPDATE CENTER",
                    subtitle = "GitHub Releases • SHA-256 & signature verified",
                    icon = Icons.Rounded.SystemUpdate,
                    accentColor = pillColor,
                    modifier = Modifier.weight(1f)
                )
                LiveStatusPill(
                    label = pillLabel,
                    toneColor = pillColor,
                    pulse = checkStatus == UpdateCheckStatus.Checking,
                    animationIntensity = animationIntensity
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "CURRENT VERSION",
                    value = "v${updateState.currentVersionName} (${updateState.currentVersionCode})",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "LATEST GITHUB",
                    value = latestVersionLabel,
                    valueColor = if (checkStatus is UpdateCheckStatus.UpdateAvailable) colors.successGreen else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            if (updateState.lastCheckTimestampMs != null) {
                Text(
                    text = "Last Update Check: ${TelemetryFormatters.formatDateTime(updateState.lastCheckTimestampMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (checkStatus is UpdateCheckStatus.Error) {
                Text(
                    text = checkStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningAmber
                )
            }

            DownloadProgressSection(
                downloadStatus = downloadStatus,
                onOpenInstallUnknownSettings = onOpenInstallUnknownSettings,
                onRetryInstallVerifiedApk = onRetryInstallVerifiedApk
            )

            if (checkStatus is UpdateCheckStatus.UpdateAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = checkStatus != UpdateCheckStatus.Checking,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_check_updates_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check for Updates")
                    }

                    Button(
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_view_update_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.successGreen)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Now", color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = checkStatus != UpdateCheckStatus.Checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_check_updates_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check for Updates")
                }
            }
        }
    }
}

@Composable
fun DownloadProgressSection(
    downloadStatus: ApkDownloadStatus,
    onOpenInstallUnknownSettings: () -> Unit,
    onRetryInstallVerifiedApk: () -> Unit
) {
    val colors = LocalRiderColors.current
    when (downloadStatus) {
        ApkDownloadStatus.Idle -> Unit
        is ApkDownloadStatus.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.navyBackground.copy(alpha = 0.72f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val pct = (downloadStatus.progressFraction * 100f).toInt()
                Text(
                    text = "Downloading signed APK ($pct% • ${formatBytes(downloadStatus.downloadedBytes)} / ${formatBytes(downloadStatus.totalBytes)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.neonCyan
                )
                LinearProgressIndicator(
                    progress = { downloadStatus.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colors.neonCyan,
                    trackColor = colors.glassCard
                )
            }
        }
        is ApkDownloadStatus.Verifying -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = colors.neonCyan, modifier = Modifier.size(16.dp))
                Text(downloadStatus.stage, style = MaterialTheme.typography.labelSmall, color = colors.neonCyan)
            }
        }
        is ApkDownloadStatus.ReadyToInstall -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.successGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = "Verified (${downloadStatus.fileName}). Launching Android package installer...",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.successGreen
                )
            }
        }
        is ApkDownloadStatus.NeedsInstallPermission -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.navyBackground.copy(alpha = 0.78f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "APK SHA-256 & signature verified! Allow 'Install unknown apps' for Ultimate Rider Booster to complete installation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningAmber
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenInstallUnknownSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Allow Installs")
                    }
                    Button(
                        onClick = onRetryInstallVerifiedApk,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.successGreen)
                    ) {
                        Text("Install APK", color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            }
        }
        is ApkDownloadStatus.Failed -> {
            Text(
                text = "Update Verification Failed: ${downloadStatus.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.errorRed
            )
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    updateState: AppUpdateUiState,
    manifest: GitHubReleaseManifest,
    onUpdateNow: (GitHubReleaseManifest) -> Unit,
    onLater: () -> Unit,
    onOpenInstallUnknownSettings: () -> Unit,
    onRetryInstallVerifiedApk: () -> Unit
) {
    val colors = LocalRiderColors.current
    val isDownloadingOrVerifying = updateState.downloadStatus is ApkDownloadStatus.Downloading ||
        updateState.downloadStatus is ApkDownloadStatus.Verifying

    AlertDialog(
        onDismissRequest = {
            if (!manifest.mandatory && !isDownloadingOrVerifying) {
                onLater()
            }
        },
        containerColor = colors.navyBackground,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = colors.neonCyan
                )
                Text(
                    text = "NEW UPDATE AVAILABLE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.neonCyan
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = manifest.releaseTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryStatTile(
                        label = "CURRENT VERSION",
                        value = "v${updateState.currentVersionName} (${updateState.currentVersionCode})",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryStatTile(
                        label = "LATEST VERSION",
                        value = "v${manifest.versionName} (${manifest.versionCode})",
                        valueColor = colors.successGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryStatTile(
                        label = "APK FILE SIZE",
                        value = manifest.apkSizeBytes?.let { formatBytes(it) } ?: "Check Release",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryStatTile(
                        label = "UPDATE DATE",
                        value = manifest.releaseDate ?: "Latest Release",
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deepBackground.copy(alpha = 0.72f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "RELEASE NOTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.neonCyan
                    )
                    Text(
                        text = manifest.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DownloadProgressSection(
                    downloadStatus = updateState.downloadStatus,
                    onOpenInstallUnknownSettings = onOpenInstallUnknownSettings,
                    onRetryInstallVerifiedApk = onRetryInstallVerifiedApk
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdateNow(manifest) },
                enabled = !isDownloadingOrVerifying,
                modifier = Modifier.testTag("update_now_button"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.successGreen)
            ) {
                Text("UPDATE NOW", color = MaterialTheme.colorScheme.onSecondary)
            }
        },
        dismissButton = {
            if (!manifest.mandatory) {
                TextButton(
                    onClick = onLater,
                    enabled = !isDownloadingOrVerifying,
                    modifier = Modifier.testTag("update_later_button")
                ) {
                    Text("LATER", color = colors.neonCyan)
                }
            }
        }
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%d KB", (bytes / 1024L).coerceAtLeast(1L))
    }
}
