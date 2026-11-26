package com.example.domain.model

data class RiderModeState(
    val isActive: Boolean = false,
    val isStarting: Boolean = false,
    val hasForegroundLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val statusLabel: String = "Monitoring Stopped",
    val activeAlertSummary: String? = null,
    val restrictionNote: String? = null,
    val monitoringIntervalMs: Long = 5000L,
    val startedAtMs: Long? = null,
    val lastMonitorCheckMs: Long? = null
)
