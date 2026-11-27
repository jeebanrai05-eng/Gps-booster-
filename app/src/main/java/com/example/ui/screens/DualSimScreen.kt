package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SignalHandoverEntity
import com.example.domain.model.AppSettings
import com.example.domain.model.DualSimState
import com.example.domain.model.SimSlotTelemetry
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.SuperBoosterModule
import com.example.ui.components.SuperBoosterSubScreenHeader
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters

@Composable
fun DualSimScreen(
    dualSimState: DualSimState,
    handovers: List<SignalHandoverEntity>,
    settings: AppSettings,
    onRequestPhoneStatePermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSimSettings: () -> Unit,
    onOpenInternetPanel: () -> Unit,
    onSelectModule: (SuperBoosterModule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val colors = LocalRiderColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SuperBoosterSubScreenHeader(
                title = "DUAL SIM INTELLIGENCE",
                subtitle = "SubscriptionManager & TelephonyCallback Signal Analyzer",
                activeModule = SuperBoosterModule.DUAL_SIM,
                pillLabel = "${dualSimState.activeSubscriptionCount} ACTIVE SIM",
                pillColor = if (dualSimState.hasPhoneStatePermission) colors.neonCyan else colors.warningAmber,
                animationIntensity = settings.animationIntensity,
                onBack = onBack,
                onSelectModule = onSelectModule
            )
        }

        if (!dualSimState.hasPhoneStatePermission) {
            item {
                GlassTelemetryCard(
                    glowTone = GlowTone.WARNING,
                    animationIntensity = settings.animationIntensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .testTag("sim_permission_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitleBar(
                            title = "Phone State Permission Required",
                            subtitle = "Android requires READ_PHONE_STATE to read Dual SIM subscriptions & signal quality",
                            icon = Icons.Rounded.SimCard,
                            accentColor = colors.warningAmber
                        )
                        Button(
                            onClick = onRequestPhoneStatePermission,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_phone_state_button")
                        ) {
                            Text("Grant Phone State Permission")
                        }
                    }
                }
            }
        }

        if (dualSimState.signalDegradationAlert != null) {
            item {
                GlassTelemetryCard(
                    glowTone = GlowTone.ERROR,
                    animationIntensity = settings.animationIntensity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = "Signal degradation alert", tint = colors.errorRed)
                        Text(
                            text = dualSimState.signalDegradationAlert,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.errorRed
                        )
                    }
                }
            }
        }

        // SIM 1 vs SIM 2 Comparison Card
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("sim_comparison_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitleBar(
                        title = "SIM 1 vs SIM 2 Comparison",
                        subtitle = dualSimState.comparisonSummary,
                        icon = Icons.AutoMirrored.Rounded.CompareArrows,
                        accentColor = colors.neonCyan
                    )

                    if (dualSimState.simSlots.isEmpty()) {
                        Text(
                            text = "No active cellular SIM subscriptions found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val sim1 = dualSimState.simSlots.getOrNull(0)
                            val sim2 = dualSimState.simSlots.getOrNull(1)
                            SimSlotColumn(
                                slot = sim1,
                                slotLabel = "SIM 1",
                                modifier = Modifier.weight(1f)
                            )
                            SimSlotColumn(
                                slot = sim2,
                                slotLabel = "SIM 2",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("refresh_sim_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh SIM scan")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh SIMs")
                        }
                        Button(
                            onClick = onOpenSimSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_sim_settings_button")
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Open Android SIM settings")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SIM Settings")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenInternetPanel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_connectivity_panel_button")
                    ) {
                        Text("Open Quick Data Switch Panel (Android)")
                    }
                    Text(
                        text = "Android does not allow apps to silently switch active mobile data SIMs. Shortcuts open official system settings safely.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Individual Detailed Slot Cards
        items(dualSimState.simSlots, key = { "${it.slotIndex}_${it.subscriptionId}" }) { slot ->
            GlassTelemetryCard(
                glowTone = if (slot.isDefaultData) GlowTone.ORANGE_PRIORITY else GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("sim_slot_card_${slot.slotIndex}")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Slot ${slot.slotIndex + 1}: ${slot.displayName}",
                        subtitle = buildString {
                            append(slot.carrierName)
                            if (slot.isDefaultData) append(" • DEFAULT DATA")
                            if (slot.isDefaultVoice) append(" • VOICE")
                            if (slot.isRoaming) append(" • ROAMING")
                        },
                        icon = Icons.Rounded.SimCard,
                        accentColor = if (slot.isDefaultData) colors.premiumOrange else colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "OPERATOR",
                            value = slot.networkOperatorName,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "NETWORK TYPE",
                            value = slot.networkTypeLabel,
                            valueColor = colors.neonCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "SIGNAL STRENGTH",
                            value = slot.signalStrengthDbm?.let { "$it dBm" }
                                ?: if (slot.signalLevel >= 0) "Level ${slot.signalLevel}/4" else "Unavailable",
                            valueColor = when {
                                slot.signalLevel >= 3 -> colors.successGreen
                                slot.signalLevel == 2 -> colors.warningAmber
                                slot.signalLevel in 0..1 -> colors.errorRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "QUALITY",
                            value = slot.signalQualityLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    slot.unavailableReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Network Handover History
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.CYAN_BLUE,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("sim_handover_history_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitleBar(
                        title = "Network Handover History",
                        subtitle = "Recorded cellular transitions & degradation events",
                        icon = Icons.Rounded.History,
                        accentColor = colors.neonCyan
                    )
                    if (handovers.isEmpty()) {
                        Text(
                            text = "No cellular handovers or degradation transitions logged yet during this monitor window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        handovers.take(12).forEach { h ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.navyBackground.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "SIM ${h.slotIndex + 1} (${h.operatorName}): ${h.previousNetworkType ?: "?"} → ${h.newNetworkType}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (h.alertMessage != null) colors.warningAmber else colors.neonCyan
                                )
                                Text(
                                    text = "${TelemetryFormatters.formatTime(h.timestampMs)} • ${h.rssiDbm?.let { "$it dBm" } ?: "Level ${h.signalLevel}"}${h.alertMessage?.let { " • $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimSlotColumn(
    slot: SimSlotTelemetry?,
    slotLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.navyBackground.copy(alpha = 0.75f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (slot?.isDefaultData == true) "$slotLabel (DATA)" else slotLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (slot?.isDefaultData == true) colors.premiumOrange else colors.neonCyan
        )
        if (slot == null || !slot.isActiveSlot) {
            Text(
                text = slot?.unavailableReason ?: "Empty / Inactive Slot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = slot.carrierName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = slot.networkTypeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.neonCyan
            )
            val progress = when {
                slot.signalStrengthDbm != null -> ((slot.signalStrengthDbm + 125f) / 55f).coerceIn(0.05f, 1f)
                slot.signalLevel >= 0 -> (slot.signalLevel / 4f).coerceIn(0.05f, 1f)
                else -> 0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = when {
                    progress >= 0.65f -> colors.successGreen
                    progress >= 0.35f -> colors.warningAmber
                    else -> colors.errorRed
                }
            )
            Text(
                text = slot.signalStrengthDbm?.let { "$it dBm (${slot.signalQualityLabel})" }
                    ?: if (slot.signalLevel >= 0) "Bars: ${slot.signalLevel}/4" else "dBm Unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
