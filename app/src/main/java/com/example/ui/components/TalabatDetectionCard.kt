package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.InstalledAppOption
import com.example.domain.model.TalabatDetectionResult
import com.example.domain.model.TalabatLaunchOutcome
import com.example.ui.theme.LocalRiderColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TalabatDetectionCard(
    detection: TalabatDetectionResult,
    animationIntensity: AnimationIntensity,
    snackbarHostState: SnackbarHostState,
    onLaunchTalabatRider: () -> TalabatLaunchOutcome,
    onRetryDetection: () -> Unit,
    onSelectManualApp: (String?) -> Unit,
    onOpenAppInfo: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    val scope = rememberCoroutineScope()
    var showPickerDialog by remember { mutableStateOf(false) }

    if (showPickerDialog) {
        RiderAppPickerDialog(
            detection = detection,
            onDismiss = { showPickerDialog = false },
            onSelectPackage = { pkg ->
                onSelectManualApp(pkg)
                showPickerDialog = false
            }
        )
    }

    val (pillLabel, pillColor, glowTone) = when {
        detection.isInstalled && detection.launchActivityAvailable ->
            Triple("INSTALLED", colors.successGreen, GlowTone.ORANGE_PRIORITY)
        detection.isInstalled && detection.isAppDisabled ->
            Triple("DISABLED", colors.errorRed, GlowTone.ERROR)
        detection.isInstalled ->
            Triple("NO LAUNCHER", colors.warningAmber, GlowTone.WARNING)
        else ->
            Triple("NOT FOUND", colors.warningAmber, GlowTone.ORANGE_PRIORITY)
    }

    GlassTelemetryCard(
        glowTone = glowTone,
        animationIntensity = animationIntensity,
        modifier = modifier
            .fillMaxWidth()
            .testTag("talabat_detection_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitleBar(
                    title = "TALABAT RIDER DETECTION",
                    subtitle = "TALABAT RIDER STATUS • PackageManager verification",
                    icon = Icons.Rounded.DeliveryDining,
                    accentColor = colors.premiumOrange,
                    modifier = Modifier.weight(1f)
                )
                LiveStatusPill(
                    label = pillLabel,
                    toneColor = pillColor,
                    pulse = false,
                    animationIntensity = animationIntensity
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "APP INSTALLED",
                    value = if (detection.isInstalled) "Yes" else "No",
                    valueColor = if (detection.isInstalled) colors.successGreen else colors.warningAmber,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "DETECTED APP NAME",
                    value = detection.detectedAppName ?: "Not Detected",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "PACKAGE NAME",
                    value = detection.detectedPackageName ?: "Unavailable",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "LAUNCH ACTIVITY",
                    value = if (detection.launchActivityAvailable) "Available" else "Unavailable",
                    valueColor = if (detection.launchActivityAvailable) colors.successGreen else colors.warningAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "APP VERSION",
                    value = detection.appVersion ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "DETECTION METHOD",
                    value = detection.detectionMethod,
                    valueColor = colors.neonCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!detection.errorNote.isNullOrBlank()) {
                Text(
                    text = detection.errorNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detection.isAppDisabled) colors.errorRed else colors.warningAmber
                )
            }

            Button(
                onClick = {
                    when (val outcome = onLaunchTalabatRider()) {
                        is TalabatLaunchOutcome.Launched -> {
                            scope.launch {
                                snackbarHostState.showSnackbar("Opening ${outcome.appName}...")
                            }
                        }
                        TalabatLaunchOutcome.NotInstalled -> {
                            showPickerDialog = true
                            scope.launch {
                                snackbarHostState.showSnackbar("Talabat Rider was not found. Select your Rider app manually.")
                            }
                        }
                        is TalabatLaunchOutcome.AppDisabled -> {
                            onOpenAppInfo(outcome.packageName)
                            scope.launch {
                                snackbarHostState.showSnackbar("App is disabled (${outcome.packageName}). Opening App Info.")
                            }
                        }
                        is TalabatLaunchOutcome.LauncherUnavailable -> {
                            showPickerDialog = true
                            scope.launch {
                                snackbarHostState.showSnackbar("No launchable activity for ${outcome.packageName}.")
                            }
                        }
                        is TalabatLaunchOutcome.Failed -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(outcome.reason)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_talabat_rider_button"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.premiumOrange)
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPEN TALABAT RIDER", color = MaterialTheme.colorScheme.onTertiary)
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onRetryDetection()
                        showPickerDialog = true
                    },
                    modifier = Modifier.testTag("select_rider_app_button")
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (detection.isInstalled) "SELECT RIDER APP" else "SCAN INSTALLED APPS")
                }

                OutlinedButton(
                    onClick = onRetryDetection,
                    modifier = Modifier.testTag("retry_talabat_detection_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RETRY DETECTION")
                }

                OutlinedButton(
                    onClick = { onOpenAppInfo(detection.detectedPackageName) },
                    modifier = Modifier.testTag("talabat_app_info_button")
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("APP INFO")
                }
            }
        }
    }
}

@Composable
fun RiderAppPickerDialog(
    detection: TalabatDetectionResult,
    onDismiss: () -> Unit,
    onSelectPackage: (String?) -> Unit
) {
    val colors = LocalRiderColors.current
    var query by remember { mutableStateOf("") }

    val sourceList = remember(detection.allLaunchableApps, detection.matchingApps, query) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            detection.allLaunchableApps
        } else {
            detection.allLaunchableApps.filter {
                it.appName.lowercase().contains(trimmed) ||
                    it.packageName.lowercase().contains(trimmed)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.navyBackground,
        title = {
            Text(
                text = "SELECT RIDER APP",
                style = MaterialTheme.typography.titleMedium,
                color = colors.neonCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Detected ${detection.allLaunchableApps.size} launchable apps (${detection.matchingApps.size} Talabat/Rider matches). Select the Rider app to launch:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search app name or package") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rider_app_search_input")
                )

                if (sourceList.isEmpty()) {
                    Text(
                        text = "No matching launchable apps found on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warningAmber
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sourceList, key = { it.packageName }) { option ->
                            val isSelected = option.packageName == detection.detectedPackageName
                            RiderAppPickerRow(
                                option = option,
                                isSelected = isSelected,
                                onClick = { onSelectPackage(option.packageName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (detection.savedManualPackage != null) {
                TextButton(onClick = { onSelectPackage(null) }) {
                    Text("Reset Auto-Detect", color = colors.premiumOrange)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.neonCyan)
            }
        }
    )
}

@Composable
private fun RiderAppPickerRow(
    option: InstalledAppOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRiderColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) colors.electricBlue.copy(alpha = 0.22f)
                else colors.glassCard.copy(alpha = 0.75f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("picker_app_${option.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.appName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = option.packageName + (option.versionName?.let { " • v$it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (option.isPriorityMatch) colors.premiumOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = colors.successGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
