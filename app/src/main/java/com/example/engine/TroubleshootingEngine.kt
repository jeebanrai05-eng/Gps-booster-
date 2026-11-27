package com.example.engine

import com.example.data.local.WorkSessionEntity
import com.example.domain.model.BatteryIntelligenceState
import com.example.domain.model.DeviceIntelligenceState
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.DualSimState
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.RiderZonesState
import com.example.domain.model.SystemCheckState
import com.example.domain.model.ThermalSeverity
import com.example.domain.model.TroubleSeverity
import com.example.domain.model.TroubleshootingItem

class TroubleshootingEngine {

    fun analyze(
        gps: GpsTelemetryState,
        network: NetworkDiagnosticsState,
        system: SystemCheckState,
        device: DeviceIntelligenceState,
        battery: BatteryIntelligenceState,
        dualSim: DualSimState,
        zones: RiderZonesState,
        recentSessions: List<WorkSessionEntity>
    ): List<TroubleshootingItem> {
        val issues = mutableListOf<TroubleshootingItem>()

        // 1. GPS Permission / Hardware / Fix issues
        if (!system.hasFineLocationPermission) {
            issues += TroubleshootingItem(
                id = "gps_perm",
                severity = TroubleSeverity.CRITICAL,
                category = "GPS",
                title = "Precise Location Permission Missing",
                observedEvidence = "ACCESS_FINE_LOCATION is currently not granted.",
                recommendation = "Grant Precise Location so Rider Mode and GNSS satellite diagnostics can acquire accurate fixes.",
                actionLabel = "Grant Location Permission",
                actionType = DiagnosticActionType.REQUEST_LOCATION_PERMISSION
            )
        } else if (!system.isLocationServicesEnabled || !system.isGpsProviderEnabled) {
            issues += TroubleshootingItem(
                id = "gps_disabled",
                severity = TroubleSeverity.CRITICAL,
                category = "GPS",
                title = "GPS Provider Disabled",
                observedEvidence = "Android Location Services or GNSS hardware provider is turned OFF.",
                recommendation = "Enable High-Accuracy Location in Android Location Settings.",
                actionLabel = "Open Location Settings",
                actionType = DiagnosticActionType.OPEN_LOCATION_SETTINGS
            )
        } else if (gps.fixStatus == GpsFixStatus.STALE_FIX || (gps.horizontalAccuracyMeters ?: 0f) > 35f) {
            issues += TroubleshootingItem(
                id = "gps_accuracy",
                severity = TroubleSeverity.WARNING,
                category = "GPS",
                title = "Degraded GNSS Accuracy / Stale Fix",
                observedEvidence = "Accuracy: ±${gps.horizontalAccuracyMeters?.toInt() ?: "?"}m • Age: ${gps.locationAgeSeconds ?: 0}s (${gps.satellitesUsedInFix}/${gps.satellitesTotal} satellites).",
                recommendation = "Move briefly out of dense tunnels/overpasses toward open sky and avoid metal magnetic cases covering the top antenna."
            )
        }

        // 2. Network & Dual SIM issues
        if (!network.isNetworkAvailable) {
            issues += TroubleshootingItem(
                id = "net_offline",
                severity = TroubleSeverity.CRITICAL,
                category = "Network",
                title = "No Active Internet Connection",
                observedEvidence = "ConnectivityManager reports no active transport.",
                recommendation = "Check mobile data toggle, SIM data plan, or airplane mode.",
                actionLabel = "Open Network Panel",
                actionType = DiagnosticActionType.OPEN_NETWORK_SETTINGS
            )
        } else if (!network.isNetworkValidated) {
            issues += TroubleshootingItem(
                id = "net_unvalidated",
                severity = TroubleSeverity.WARNING,
                category = "Network",
                title = "Internet Validation Failing",
                observedEvidence = "Connected via ${network.transportType.label}, but NET_CAPABILITY_VALIDATED is false.",
                recommendation = "Check captive portal Wi-Fi or toggle mobile data.",
                actionLabel = "Open Network Panel",
                actionType = DiagnosticActionType.OPEN_NETWORK_SETTINGS
            )
        }

        dualSim.signalDegradationAlert?.let { alert ->
            issues += TroubleshootingItem(
                id = "sim_degraded",
                severity = TroubleSeverity.WARNING,
                category = "Dual SIM",
                title = "Weak Cellular Data Signal",
                observedEvidence = alert,
                recommendation = if (dualSim.activeSubscriptionCount > 1) {
                    "Compare SIM 1 vs SIM 2 in Dual SIM Intelligence and consider switching default mobile data in Android SIM settings if the other SIM has stronger coverage."
                } else {
                    "Move out of shielded indoor zones or check mobile network mode in Android SIM settings."
                }
            )
        }

        // 3. Thermal & Battery Restrictions
        if (device.thermalSeverity == ThermalSeverity.SEVERE || device.thermalSeverity == ThermalSeverity.CRITICAL ||
            (battery.temperatureC != null && battery.temperatureC >= 43.5f)
        ) {
            issues += TroubleshootingItem(
                id = "thermal_hot",
                severity = TroubleSeverity.CRITICAL,
                category = "Thermal",
                title = "Device Overheating / Thermal Throttling",
                observedEvidence = "Thermal: ${device.thermalStatusLabel} • Battery: ${battery.temperatureC?.let { String.format("%.1f°C", it) } ?: "N/A"}",
                recommendation = "Shade the phone from direct sun, reduce screen brightness, and avoid fast-charging simultaneously during peak sun shifts."
            )
        } else if (device.thermalSeverity == ThermalSeverity.MODERATE) {
            issues += TroubleshootingItem(
                id = "thermal_warm",
                severity = TroubleSeverity.WARNING,
                category = "Thermal",
                title = "Moderate Device Heat",
                observedEvidence = "Thermal API reports ${device.thermalStatusLabel}.",
                recommendation = "Ensure airflow across your handlebar mount to prevent severe throttling."
            )
        }

        if (system.isPowerSaveMode || system.isBackgroundRestricted || !system.isBatteryOptimizationIgnored) {
            issues += TroubleshootingItem(
                id = "battery_throttle",
                severity = if (system.isPowerSaveMode || system.isBackgroundRestricted) TroubleSeverity.WARNING else TroubleSeverity.WARNING,
                category = "Battery & Background",
                title = "Background / Power Throttling Detected",
                observedEvidence = buildList {
                    if (system.isPowerSaveMode) add("Battery Saver ON")
                    if (system.isBackgroundRestricted) add("Background Restricted")
                    if (!system.isBatteryOptimizationIgnored) add("Battery Optimization Active")
                }.joinToString(" • "),
                recommendation = "Set battery usage to Unrestricted for Ultimate Rider Booster and Talabat Rider so Android does not delay GPS updates.",
                actionLabel = "Battery Settings",
                actionType = DiagnosticActionType.OPEN_BATTERY_SETTINGS
            )
        }

        if (!battery.isCharging && battery.levelPercent in 0..20) {
            issues += TroubleshootingItem(
                id = "battery_low",
                severity = if (battery.levelPercent <= 10) TroubleSeverity.CRITICAL else TroubleSeverity.WARNING,
                category = "Battery",
                title = "Low Battery Level (${battery.levelPercent}%)",
                observedEvidence = "${battery.levelPercent}% remaining (${battery.estimationLabel}).",
                recommendation = "Connect to an external power bank before long delivery shifts."
            )
        }

        // 4. Memory / Storage pressure
        if (device.isLowMemory || device.ramUsagePercent >= 88) {
            issues += TroubleshootingItem(
                id = "ram_pressure",
                severity = TroubleSeverity.WARNING,
                category = "Memory",
                title = "High RAM Pressure (${device.ramUsagePercent}% used)",
                observedEvidence = "${device.availableRamMb} MB available out of ${device.totalRamMb} MB.",
                recommendation = "Close unused heavy background apps (video/games) so Android keeps navigation and rider dispatch apps resident in memory."
            )
        }
        if (device.storageUsedPercent >= 92) {
            issues += TroubleshootingItem(
                id = "storage_full",
                severity = TroubleSeverity.WARNING,
                category = "Storage",
                title = "Low Internal Storage (${device.storageUsedPercent}% used)",
                observedEvidence = "${String.format("%.1f", device.storageAvailableGb)} GB free.",
                recommendation = "Free up storage space to prevent app cache crashes and sluggish map tile writes."
            )
        }

        // 5. Historical recurring zone/session patterns
        if (zones.repeatedWeakZoneWarnings.isNotEmpty()) {
            val worst = zones.repeatedWeakZoneWarnings.first()
            issues += TroubleshootingItem(
                id = "weak_zone_pattern",
                severity = TroubleSeverity.WARNING,
                category = "Rider Zones",
                title = "Recurring Weak Signal Area Observed",
                observedEvidence = "Zone ${worst.zoneKey} had ${worst.weakCount}/${worst.sampleCount} weak GPS/network samples.",
                recommendation = "Review Rider Zone Intelligence before parking between orders in this zone."
            )
        }

        val recentDrops = recentSessions.take(5).sumOf { it.networkLossCount }
        if (recentDrops >= 3) {
            issues += TroubleshootingItem(
                id = "session_net_drops",
                severity = TroubleSeverity.WARNING,
                category = "History",
                title = "Frequent Network Drops Across Recent Sessions",
                observedEvidence = "$recentDrops network interruptions recorded across your last ${recentSessions.take(5).size} sessions.",
                recommendation = "Inspect Dual SIM handover history to identify whether carrier tower switching is causing packet drops."
            )
        }

        if (issues.isEmpty()) {
            issues += TroubleshootingItem(
                id = "all_nominal",
                severity = TroubleSeverity.OPTIMAL,
                category = "System",
                title = "All Rider Systems Nominal",
                observedEvidence = "GNSS fix, validated network, RAM (${device.ramUsagePercent}%), and thermal status (${device.thermalStatusLabel}) look healthy.",
                recommendation = "No corrective actions required right now."
            )
        }

        return issues
    }
}
