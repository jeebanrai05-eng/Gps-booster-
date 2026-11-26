package com.example.domain.model

enum class NetworkTransportType(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Mobile Data"),
    ETHERNET("Ethernet"),
    VPN("VPN Tunnel"),
    OTHER("Other"),
    NONE("Disconnected")
}

sealed interface LatencyTestState {
    data object Idle : LatencyTestState
    data class Running(val probeIndex: Int, val totalProbes: Int, val endpointLabel: String) : LatencyTestState
    data class Success(
        val minMs: Long,
        val avgMs: Long,
        val maxMs: Long,
        val successfulProbes: Int,
        val totalProbes: Int,
        val endpointLabel: String,
        val testedAtMs: Long
    ) : LatencyTestState
    data class Error(val message: String, val requiresInternet: Boolean = true) : LatencyTestState
}

sealed interface SpeedTestState {
    data object Idle : SpeedTestState
    data class Running(val progressFraction: Float, val currentMbps: Double, val sourceLabel: String) : SpeedTestState
    data class Success(
        val downloadMbps: Double,
        val bytesTransferred: Long,
        val durationMs: Long,
        val sourceLabel: String,
        val testedAtMs: Long
    ) : SpeedTestState
    data class Error(val message: String, val requiresInternet: Boolean = true) : SpeedTestState
}

data class NetworkDiagnosticsState(
    val isNetworkAvailable: Boolean = false,
    val hasInternetCapability: Boolean = false,
    val isNetworkValidated: Boolean = false,
    val transportType: NetworkTransportType = NetworkTransportType.NONE,
    val isWifiConnected: Boolean = false,
    val isCellularConnected: Boolean = false,
    val operatorName: String? = null,
    val cellularNetworkTypeLabel: String? = null,
    val downstreamBandwidthKbps: Int? = null,
    val upstreamBandwidthKbps: Int? = null,
    val connectionLossCount: Int = 0,
    val networkTransitionCount: Int = 0,
    val lastEventDescription: String = "Monitoring initialized",
    val lastEventTimestampMs: Long = System.currentTimeMillis(),
    val latencyTestState: LatencyTestState = LatencyTestState.Idle,
    val speedTestState: SpeedTestState = SpeedTestState.Idle
)
