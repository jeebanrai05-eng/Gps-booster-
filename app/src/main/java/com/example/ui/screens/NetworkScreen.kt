package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppSettings
import com.example.domain.model.LatencyTestState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.SpeedTestState
import com.example.ui.components.GlassTelemetryCard
import com.example.ui.components.GlowTone
import com.example.ui.components.LiveStatusPill
import com.example.ui.components.SectionTitleBar
import com.example.ui.components.TelemetryStatTile
import com.example.ui.theme.LocalRiderColors
import com.example.util.TelemetryFormatters
import java.util.Locale

@Composable
fun NetworkScreen(
    networkState: NetworkDiagnosticsState,
    settings: AppSettings,
    onRefreshNetwork: () -> Unit,
    onRunLatencyTest: () -> Unit,
    onRunSpeedTest: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalRiderColors.current

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NETWORK DIAGNOSTICS",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real ConnectivityManager & Reachable Endpoint Probing",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.neonCyan
                    )
                }
                LiveStatusPill(
                    label = if (networkState.isNetworkValidated) "VALIDATED"
                    else if (networkState.isNetworkAvailable) "UNVERIFIED"
                    else "OFFLINE",
                    toneColor = when {
                        networkState.isNetworkValidated -> colors.successGreen
                        networkState.isNetworkAvailable -> colors.warningAmber
                        else -> colors.errorRed
                    },
                    pulse = networkState.isNetworkValidated,
                    animationIntensity = settings.animationIntensity
                )
            }
        }

        // Offline Local Interface & Transport Status
        item {
            GlassTelemetryCard(
                glowTone = if (networkState.isNetworkAvailable) GlowTone.CYAN_BLUE else GlowTone.ERROR,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Offline Link & Transport Monitor",
                        subtitle = "Uses Android ConnectivityManager & NetworkCapabilities (works offline)",
                        icon = if (networkState.isWifiConnected) Icons.Rounded.Wifi
                        else if (networkState.isCellularConnected) Icons.Rounded.CellTower
                        else Icons.Rounded.CloudOff,
                        accentColor = colors.neonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "TRANSPORT",
                            value = networkState.transportType.label,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "INTERNET CAP",
                            value = if (networkState.hasInternetCapability) "Available" else "No",
                            valueColor = if (networkState.hasInternetCapability) colors.successGreen else colors.errorRed,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "VALIDATED",
                            value = if (networkState.isNetworkValidated) "Verified" else "Unverified",
                            valueColor = if (networkState.isNetworkValidated) colors.successGreen else colors.warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "WI-FI",
                            value = if (networkState.isWifiConnected) "Connected" else "Inactive",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "MOBILE DATA",
                            value = if (networkState.isCellularConnected) {
                                networkState.cellularNetworkTypeLabel ?: "Connected"
                            } else "Inactive",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "CARRIER",
                            value = networkState.operatorName ?: "Unavailable",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryStatTile(
                            label = "DROPS DETECTED",
                            value = "${networkState.connectionLossCount}",
                            valueColor = if (networkState.connectionLossCount > 0) colors.warningAmber else colors.successGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "TRANSITIONS",
                            value = "${networkState.networkTransitionCount}",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryStatTile(
                            label = "CAP ESTIMATE",
                            value = networkState.downstreamBandwidthKbps?.let { "${it / 1000} Mbps" } ?: "N/A",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Last event: ${networkState.lastEventDescription} (${TelemetryFormatters.formatTime(networkState.lastEventTimestampMs)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshNetwork,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("refresh_network_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                        OutlinedButton(
                            onClick = onOpenNetworkSettings,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_network_settings_button")
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Net Settings")
                        }
                    }
                }
            }
        }

        // Internet-Dependent Latency Test
        item {
            GlassTelemetryCard(
                glowTone = GlowTone.ORANGE_PRIORITY,
                animationIntensity = settings.animationIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitleBar(
                        title = "Latency Probe (Internet-Dependent)",
                        subtitle = "Measures real TCP handshake RTT across 4 probes to a reachable edge node",
                        icon = Icons.Rounded.NetworkPing,
                        accentColor = colors.premiumOrange
                    )

                    when (val state = networkState.latencyTestState) {
                        is LatencyTestState.Idle -> {
                            Text(
                                text = "Tap Run Latency Test to measure real round-trip latency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is LatencyTestState.Running -> {
                            Text(
                                text = "Probing ${state.endpointLabel} (${state.probeIndex}/${state.totalProbes})...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.neonCyan
                            )
                            LinearProgressIndicator(
                                progress = { state.probeIndex.toFloat() / state.totalProbes.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = colors.neonCyan,
                                trackColor = colors.navyBackground
                            )
                        }
                        is LatencyTestState.Success -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelemetryStatTile("MIN RTT", "${state.minMs} ms", colors.successGreen, Modifier.weight(1f))
                                TelemetryStatTile("AVG RTT", "${state.avgMs} ms", colors.neonCyan, Modifier.weight(1f))
                                TelemetryStatTile("MAX RTT", "${state.maxMs} ms", colors.warningAmber, Modifier.weight(1f))
                            }
                            Text(
                                text = "Endpoint: ${state.endpointLabel} • ${state.successfulProbes}/${state.totalProbes} probes succeeded",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is LatencyTestState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.errorRed
                            )
                        }
                    }

                    Button(
                        onClick = onRunLatencyTest,
                        enabled = networkState.latencyTestState !is LatencyTestState.Running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_latency_test_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.electricBlue)
                    ) {
                        Text("Run Latency Test")
                    }
                }
            }
        }

        // Internet-Dependent Download Speed Test
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
                        title = "Throughput Speed Test (Internet-Dependent)",
                        subtitle = "Streams real 2 MB HTTP test payload; reports exact measured Mbps",
                        icon = Icons.Rounded.Speed,
                        accentColor = colors.neonCyan
                    )

                    when (val speed = networkState.speedTestState) {
                        is SpeedTestState.Idle -> {
                            Text(
                                text = "Requires active internet connection. Never estimates or simulates bandwidth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is SpeedTestState.Running -> {
                            Text(
                                text = String.format(Locale.US, "Measuring: %.2f Mbps (%s)", speed.currentMbps, speed.sourceLabel),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.neonCyan
                            )
                            LinearProgressIndicator(
                                progress = { speed.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = colors.neonCyan,
                                trackColor = colors.navyBackground
                            )
                        }
                        is SpeedTestState.Success -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelemetryStatTile(
                                    label = "DOWNLOAD",
                                    value = String.format(Locale.US, "%.2f Mbps", speed.downloadMbps),
                                    valueColor = colors.successGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                TelemetryStatTile(
                                    label = "TRANSFERRED",
                                    value = "${speed.bytesTransferred / 1024} KB",
                                    modifier = Modifier.weight(1f)
                                )
                                TelemetryStatTile(
                                    label = "DURATION",
                                    value = "${speed.durationMs} ms",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "Source: ${speed.sourceLabel} at ${TelemetryFormatters.formatTime(speed.testedAtMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is SpeedTestState.Error -> {
                            Text(
                                text = speed.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.errorRed
                            )
                        }
                    }

                    Button(
                        onClick = onRunSpeedTest,
                        enabled = networkState.speedTestState !is SpeedTestState.Running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_speed_test_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.premiumOrange)
                    ) {
                        Text("Run Download Speed Test", color = MaterialTheme.colorScheme.onTertiary)
                    }

                    Text(
                        text = "Disclaimer: Diagnostics measure real connection quality and do not increase carrier bandwidth or bypass network limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
