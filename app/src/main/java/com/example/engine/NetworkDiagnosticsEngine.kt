package com.example.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.domain.model.LatencyTestState
import com.example.domain.model.NetworkDiagnosticsState
import com.example.domain.model.NetworkTransportType
import com.example.domain.model.SpeedTestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class NetworkDiagnosticsEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(NetworkDiagnosticsState())
    val state: StateFlow<NetworkDiagnosticsState> = _state.asStateFlow()

    private var callbackRegistered = false
    private var lastKnownTransport: NetworkTransportType? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshNetworkSnapshot("Network interface available")
        }

        override fun onLost(network: Network) {
            _state.update { current ->
                current.copy(
                    connectionLossCount = current.connectionLossCount + 1,
                    lastEventDescription = "Connection lost",
                    lastEventTimestampMs = System.currentTimeMillis()
                )
            }
            refreshNetworkSnapshot("Connection lost")
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            applyCapabilities(capabilities, eventNote = null)
        }
    }

    fun startMonitoring() {
        refreshNetworkSnapshot("Initial network check")
        if (!callbackRegistered) {
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                callbackRegistered = true
            }
        }
    }

    fun stopMonitoring() {
        if (callbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            callbackRegistered = false
        }
    }

    fun refreshNetworkSnapshot(note: String? = null) {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        applyCapabilities(capabilities, note)
    }

    @SuppressLint("MissingPermission")
    private fun applyCapabilities(capabilities: NetworkCapabilities?, eventNote: String?) {
        val isAvailable = capabilities != null
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isEthernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val transport = when {
            capabilities == null -> NetworkTransportType.NONE
            isWifi -> NetworkTransportType.WIFI
            isCellular -> NetworkTransportType.CELLULAR
            isVpn -> NetworkTransportType.VPN
            isEthernet -> NetworkTransportType.ETHERNET
            else -> NetworkTransportType.OTHER
        }

        val previousTransport = lastKnownTransport
        val transitioned = previousTransport != null && previousTransport != transport
        lastKnownTransport = transport

        val operator = runCatching {
            val op = telephonyManager?.networkOperatorName
            if (!op.isNullOrBlank()) op else telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val cellularGen = readCellularTypeSafely()

        _state.update { current ->
            val transitionIncrement = if (transitioned) 1 else 0
            val description = when {
                transitioned -> "Switched ${previousTransport?.label} -> ${transport.label}"
                eventNote != null -> eventNote
                else -> current.lastEventDescription
            }
            current.copy(
                isNetworkAvailable = isAvailable,
                hasInternetCapability = hasInternet,
                isNetworkValidated = isValidated,
                transportType = transport,
                isWifiConnected = isWifi,
                isCellularConnected = isCellular,
                operatorName = operator,
                cellularNetworkTypeLabel = cellularGen,
                downstreamBandwidthKbps = capabilities?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
                upstreamBandwidthKbps = capabilities?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
                networkTransitionCount = current.networkTransitionCount + transitionIncrement,
                lastEventDescription = description,
                lastEventTimestampMs = System.currentTimeMillis()
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCellularTypeSafely(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.networkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
            when (type) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> null
            }
        }.getOrNull()
    }

    fun runLatencyTest() {
        refreshNetworkSnapshot()
        val snap = _state.value
        if (!snap.isNetworkAvailable || !snap.hasInternetCapability) {
            _state.update {
                it.copy(
                    latencyTestState = LatencyTestState.Error(
                        message = "Internet disconnected. Connect to Wi-Fi or mobile data to test latency.",
                        requiresInternet = true
                    )
                )
            }
            return
        }

        if (_state.value.latencyTestState is LatencyTestState.Running) return

        scope.launch {
            val totalProbes = 4
            val host = "1.1.1.1"
            val port = 443
            val label = "Cloudflare Edge ($host:$port)"
            val timings = mutableListOf<Long>()

            for (probe in 1..totalProbes) {
                _state.update {
                    it.copy(latencyTestState = LatencyTestState.Running(probe, totalProbes, label))
                }
                val rtt = measureTcpConnectMs(host, port, 2500)
                if (rtt != null) {
                    timings.add(rtt)
                }
            }

            if (timings.isEmpty()) {
                // Fallback to Google DNS TCP endpoint in case 1.1.1.1 is filtered
                val fallbackHost = "8.8.8.8"
                val fallbackLabel = "Google DNS Edge ($fallbackHost:443)"
                for (probe in 1..totalProbes) {
                    _state.update {
                        it.copy(latencyTestState = LatencyTestState.Running(probe, totalProbes, fallbackLabel))
                    }
                    measureTcpConnectMs(fallbackHost, 443, 2500)?.let { timings.add(it) }
                }
                if (timings.isEmpty()) {
                    _state.update {
                        it.copy(
                            latencyTestState = LatencyTestState.Error(
                                message = "Network timeout: Unable to reach latency test endpoints.",
                                requiresInternet = true
                            )
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            latencyTestState = LatencyTestState.Success(
                                minMs = timings.min(),
                                avgMs = timings.average().toLong(),
                                maxMs = timings.max(),
                                successfulProbes = timings.size,
                                totalProbes = totalProbes,
                                endpointLabel = fallbackLabel,
                                testedAtMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } else {
                _state.update {
                    it.copy(
                        latencyTestState = LatencyTestState.Success(
                            minMs = timings.min(),
                            avgMs = timings.average().toLong(),
                            maxMs = timings.max(),
                            successfulProbes = timings.size,
                            totalProbes = totalProbes,
                            endpointLabel = label,
                            testedAtMs = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private suspend fun measureTcpConnectMs(host: String, port: Int, timeoutMs: Int): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    val startNanos = SystemClock.elapsedRealtimeNanos()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    val endNanos = SystemClock.elapsedRealtimeNanos()
                    ((endNanos - startNanos) / 1_000_000L).coerceAtLeast(1L)
                }
            }.getOrNull()
        }

    fun runSpeedTest() {
        refreshNetworkSnapshot()
        val snap = _state.value
        if (!snap.isNetworkAvailable || !snap.hasInternetCapability) {
            _state.update {
                it.copy(
                    speedTestState = SpeedTestState.Error(
                        message = "Internet disconnected. Speed testing requires an active connection.",
                        requiresInternet = true
                    )
                )
            }
            return
        }

        if (_state.value.speedTestState is SpeedTestState.Running) return

        scope.launch {
            val sourceLabel = "Cloudflare Speed Endpoint (2 MB)"
            val testUrl = "https://speed.cloudflare.com/__down?bytes=2000000"
            _state.update {
                it.copy(speedTestState = SpeedTestState.Running(0.05f, 0.0, sourceLabel))
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(testUrl)
                        .header("Cache-Control", "no-cache")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code} from test endpoint")
                        }
                        val body = response.body ?: throw IllegalStateException("Empty response body")
                        val expectedBytes = body.contentLength().takeIf { it > 0 } ?: 2_000_000L
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        val startNanos = SystemClock.elapsedRealtimeNanos()
                        var lastEmitNanos = startNanos

                        body.byteStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                totalRead += read
                                val now = SystemClock.elapsedRealtimeNanos()
                                if (now - lastEmitNanos > 150_000_000L) {
                                    lastEmitNanos = now
                                    val elapsedSec = ((now - startNanos) / 1_000_000_000.0).coerceAtLeast(0.01)
                                    val mbps = (totalRead * 8.0) / (elapsedSec * 1_000_000.0)
                                    val progress = (totalRead.toFloat() / expectedBytes.toFloat()).coerceIn(0.05f, 0.98f)
                                    _state.update {
                                        it.copy(
                                            speedTestState = SpeedTestState.Running(
                                                progressFraction = progress,
                                                currentMbps = mbps,
                                                sourceLabel = sourceLabel
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        val durationMs = ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L)
                            .coerceAtLeast(1L)
                        val durationSec = durationMs / 1000.0
                        val finalMbps = (totalRead * 8.0) / (durationSec * 1_000_000.0)

                        SpeedTestState.Success(
                            downloadMbps = finalMbps,
                            bytesTransferred = totalRead,
                            durationMs = durationMs,
                            sourceLabel = sourceLabel,
                            testedAtMs = System.currentTimeMillis()
                        )
                    }
                }
            }

            result.onSuccess { success ->
                _state.update { it.copy(speedTestState = success) }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        speedTestState = SpeedTestState.Error(
                            message = err.localizedMessage ?: "Speed test failed due to network timeout.",
                            requiresInternet = true
                        )
                    )
                }
            }
        }
    }
}
