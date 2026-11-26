package com.example.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.domain.model.GpsFixStatus
import com.example.domain.model.GpsTelemetryState
import com.example.domain.model.LocationSourceType
import com.example.domain.model.SatelliteInfo
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GpsMonitorEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GpsTelemetryState())
    val state: StateFlow<GpsTelemetryState> = _state.asStateFlow()

    private var currentIntervalMs: Long = 2000L
    private var ageTickerJob: Job? = null
    private val recentFixes = ArrayDeque<Location>(6)
    private var totalSpeedSumMps = 0.0
    private var speedSamples = 0
    private var maxSpeedRecordedMps = 0f

    private var isLocationListenerRegistered = false
    private var isGnssCallbackRegistered = false
    private var isFusedRegistered = false

    private val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            refreshStaticStatus()
        }

        override fun onProviderEnabled(provider: String) {
            refreshStaticStatus()
        }

        override fun onProviderDisabled(provider: String) {
            refreshStaticStatus()
        }
    }

    private val fusedLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                // Prefer direct GPS provider when available; accept fused when GPS hasn't updated recently
                val current = _state.value
                val currentAgeMs = current.lastUpdateElapsedNanos?.let {
                    (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000L
                } ?: Long.MAX_VALUE
                if (loc.provider == LocationManager.GPS_PROVIDER || current.latitude == null || currentAgeMs > currentIntervalMs * 2) {
                    handleNewLocation(loc, isFromFusedClient = true)
                }
            }
        }
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onStarted() {
            _state.update { it.copy(gnssStatusSupported = true) }
        }

        override fun onStopped() {
            // Keep last known counts cleared only if monitoring stopped
        }

        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val total = status.satelliteCount
            var usedCount = 0
            var cn0Sum = 0f
            var cn0Count = 0
            var maxCn0: Float? = null
            val constellations = mutableMapOf<String, Int>()
            val details = ArrayList<SatelliteInfo>(total)

            for (i in 0 until total) {
                val used = status.usedInFix(i)
                if (used) usedCount++
                val cn0 = status.getCn0DbHz(i)
                if (cn0 > 0f) {
                    cn0Sum += cn0
                    cn0Count++
                    maxCn0 = maxOf(maxCn0 ?: 0f, cn0)
                }
                val constellationName = mapConstellation(status.getConstellationType(i))
                constellations[constellationName] = (constellations[constellationName] ?: 0) + 1
                details.add(
                    SatelliteInfo(
                        svid = status.getSvid(i),
                        constellation = constellationName,
                        cn0DbHz = cn0,
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        usedInFix = used
                    )
                )
            }

            val avgCn0 = if (cn0Count > 0) cn0Sum / cn0Count else null
            val quality = classifySignalQuality(
                usedInFix = usedCount,
                total = total,
                avgCn0 = avgCn0,
                accuracyMeters = _state.value.horizontalAccuracyMeters
            )

            _state.update {
                it.copy(
                    gnssStatusSupported = true,
                    satellitesTotal = total,
                    satellitesUsedInFix = usedCount,
                    averageCn0DbHz = avgCn0,
                    maxCn0DbHz = maxCn0,
                    constellationCounts = constellations,
                    satelliteDetails = details.sortedWith(
                        compareByDescending<SatelliteInfo> { s -> s.usedInFix }
                            .thenByDescending { s -> s.cn0DbHz }
                    ),
                    signalQualityLabel = quality
                )
            }
        }
    }

    fun refreshStaticStatus() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasGnssHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        val fusedAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        val fixStatus = resolveFixStatus(
            hasPermission = hasFine || hasCoarse,
            hasGnssHardware = hasGnssHardware,
            locationEnabled = locationEnabled,
            gpsEnabled = gpsEnabled,
            lastElapsedNanos = _state.value.lastUpdateElapsedNanos
        )

        _state.update { current ->
            current.copy(
                hasFineLocationPermission = hasFine,
                hasCoarseLocationPermission = hasCoarse,
                hasGnssHardware = hasGnssHardware,
                isLocationServicesEnabled = locationEnabled,
                isGpsProviderEnabled = gpsEnabled,
                isNetworkProviderEnabled = networkEnabled,
                isFusedLocationAvailable = fusedAvailable,
                fixStatus = fixStatus
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(intervalMs: Long = currentIntervalMs) {
        currentIntervalMs = intervalMs.coerceAtLeast(1000L)
        refreshStaticStatus()
        val current = _state.value

        if (!current.hasFineLocationPermission && !current.hasCoarseLocationPermission) {
            _state.update {
                it.copy(
                    isMonitoringActive = false,
                    fixStatus = GpsFixStatus.PERMISSION_DENIED,
                    signalQualityLabel = "Location Permission Needed"
                )
            }
            return
        }

        if (!current.isLocationServicesEnabled) {
            _state.update {
                it.copy(
                    isMonitoringActive = false,
                    fixStatus = GpsFixStatus.GPS_DISABLED,
                    signalQualityLabel = "Location Services Off"
                )
            }
            return
        }

        // Seed with real lastKnownLocation if we don't have one yet
        if (_state.value.latitude == null) {
            val lastGps = runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            val lastNet = runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            val bestLast = when {
                lastGps != null && lastNet != null ->
                    if (lastGps.elapsedRealtimeNanos >= lastNet.elapsedRealtimeNanos) lastGps else lastNet
                else -> lastGps ?: lastNet
            }
            bestLast?.let { handleNewLocation(it, isInitialLastKnown = true) }
        }

        stopListenersOnly()

        if (current.hasGnssHardware && current.isGpsProviderEnabled && current.hasFineLocationPermission) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    currentIntervalMs,
                    0f,
                    gpsLocationListener,
                    Looper.getMainLooper()
                )
                isLocationListenerRegistered = true
            }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.registerGnssStatusCallback(
                        ContextCompat.getMainExecutor(context),
                        gnssStatusCallback
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
                }
                isGnssCallbackRegistered = true
            }.onFailure {
                _state.update { s -> s.copy(gnssStatusSupported = false) }
            }
        } else if (current.isNetworkProviderEnabled) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    currentIntervalMs,
                    0f,
                    gpsLocationListener,
                    Looper.getMainLooper()
                )
                isLocationListenerRegistered = true
            }
        }

        if (current.isFusedLocationAvailable) {
            runCatching {
                val priority = if (current.hasFineLocationPermission) {
                    Priority.PRIORITY_HIGH_ACCURACY
                } else {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                }
                val request = LocationRequest.Builder(priority, currentIntervalMs)
                    .setMinUpdateIntervalMillis(currentIntervalMs)
                    .build()
                fusedClient.requestLocationUpdates(
                    request,
                    fusedLocationCallback,
                    Looper.getMainLooper()
                )
                isFusedRegistered = true
            }
        }

        _state.update { it.copy(isMonitoringActive = true) }
        startAgeTicker()
    }

    fun updateInterval(intervalMs: Long) {
        if (currentIntervalMs == intervalMs) return
        currentIntervalMs = intervalMs
        if (_state.value.isMonitoringActive) {
            startMonitoring(intervalMs)
        }
    }

    fun stopMonitoring() {
        stopListenersOnly()
        ageTickerJob?.cancel()
        ageTickerJob = null
        _state.update { it.copy(isMonitoringActive = false) }
    }

    fun resetSpeedStats() {
        totalSpeedSumMps = 0.0
        speedSamples = 0
        maxSpeedRecordedMps = 0f
        _state.update {
            it.copy(
                maxSpeedMps = 0f,
                avgSpeedMps = 0f,
                speedSampleCount = 0
            )
        }
    }

    /**
     * Requests a single fresh fix cleanly using LocationManagerCompat.getCurrentLocation
     * and/or FusedLocationProviderClient when user triggers Smart GPS Recovery or Refresh.
     * Never invents coordinates or leaves runaway high-frequency polling behind.
     */
    @SuppressLint("MissingPermission")
    suspend fun requestFreshLocationFix(timeoutMs: Long = 2800L): Boolean {
        refreshStaticStatus()
        val current = _state.value
        val hasPerm = current.hasFineLocationPermission || current.hasCoarseLocationPermission
        if (!hasPerm || !current.isLocationServicesEnabled) {
            return false
        }

        // Ensure active monitoring listeners are attached if GPS provider is enabled
        if (!current.isMonitoringActive) {
            startMonitoring(currentIntervalMs)
        }

        val initialNanos = _state.value.lastUpdateElapsedNanos

        // 1. Try direct GNSS provider single fix when fine permission and GPS provider are available
        if (current.isGpsProviderEnabled && current.hasFineLocationPermission) {
            val directLocation = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(
                            locationManager,
                            LocationManager.GPS_PROVIDER,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (directLocation != null) {
                handleNewLocation(directLocation, isInitialLastKnown = false, isFromFusedClient = false)
                return true
            }
        }

        // 2. Fallback to high-accuracy Fused single fix if Google Play Services is available
        if (current.isFusedLocationAvailable) {
            val fusedLoc = withTimeoutOrNull(timeoutMs.coerceAtMost(2000L)) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cts = CancellationTokenSource()
                    cont.invokeOnCancellation { cts.cancel() }
                    val priority = if (current.hasFineLocationPermission) {
                        Priority.PRIORITY_HIGH_ACCURACY
                    } else {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    }
                    runCatching {
                        fusedClient.getCurrentLocation(priority, cts.token)
                            .addOnSuccessListener { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fusedLoc != null) {
                handleNewLocation(fusedLoc, isInitialLastKnown = false, isFromFusedClient = true)
                return true
            }
        }

        val latestNanos = _state.value.lastUpdateElapsedNanos
        return latestNanos != null && latestNanos != initialNanos
    }

    private fun stopListenersOnly() {
        if (isLocationListenerRegistered) {
            runCatching { locationManager.removeUpdates(gpsLocationListener) }
            isLocationListenerRegistered = false
        }
        if (isGnssCallbackRegistered) {
            runCatching { locationManager.unregisterGnssStatusCallback(gnssStatusCallback) }
            isGnssCallbackRegistered = false
        }
        if (isFusedRegistered) {
            runCatching { fusedClient.removeLocationUpdates(fusedLocationCallback) }
            isFusedRegistered = false
        }
    }

    private fun startAgeTicker() {
        ageTickerJob?.cancel()
        ageTickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val current = _state.value
                val lastNanos = current.lastUpdateElapsedNanos
                if (lastNanos != null) {
                    val ageSec = ((SystemClock.elapsedRealtimeNanos() - lastNanos) / 1_000_000_000L)
                        .coerceAtLeast(0L)
                    val delayThresholdSec = ((currentIntervalMs * 3L) / 1000L).coerceAtLeast(8L)
                    val delayed = ageSec > delayThresholdSec
                    val newStatus = resolveFixStatus(
                        hasPermission = current.hasFineLocationPermission || current.hasCoarseLocationPermission,
                        hasGnssHardware = current.hasGnssHardware,
                        locationEnabled = current.isLocationServicesEnabled,
                        gpsEnabled = current.isGpsProviderEnabled,
                        lastElapsedNanos = lastNanos
                    )
                    _state.update {
                        it.copy(
                            locationAgeSeconds = ageSec,
                            updateDelayDetected = delayed,
                            fixStatus = newStatus
                        )
                    }
                }
            }
        }
    }

    private fun handleNewLocation(
        location: Location,
        isInitialLastKnown: Boolean = false,
        isFromFusedClient: Boolean = false
    ) {
        val prevNanos = _state.value.lastUpdateElapsedNanos
        val newNanos = location.elapsedRealtimeNanos
        val intervalMs = if (!isInitialLastKnown && prevNanos != null && newNanos > prevNanos) {
            (newNanos - prevNanos) / 1_000_000L
        } else {
            _state.value.updateIntervalMs
        }

        val ageSec = ((SystemClock.elapsedRealtimeNanos() - newNanos) / 1_000_000_000L)
            .coerceAtLeast(0L)

        val sourceType = when {
            location.provider == LocationManager.GPS_PROVIDER -> LocationSourceType.GNSS
            location.provider == LocationManager.NETWORK_PROVIDER -> LocationSourceType.NETWORK
            isFromFusedClient || location.provider == "fused" -> LocationSourceType.FUSED
            else -> LocationSourceType.NETWORK
        }

        if (!isInitialLastKnown) {
            if (recentFixes.size >= 6) recentFixes.removeFirst()
            recentFixes.addLast(location)
        }

        val stability = computeStabilityLabel(recentFixes)

        val speed = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else null
        if (!isInitialLastKnown && speed != null) {
            speedSamples++
            totalSpeedSumMps += speed
            if (speed > maxSpeedRecordedMps) {
                maxSpeedRecordedMps = speed
            }
        }

        val avgSpeed = if (speedSamples > 0) (totalSpeedSumMps / speedSamples).toFloat() else 0f

        val verticalAcc = if (location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters
        } else null

        val delayDetected = intervalMs != null && intervalMs > (currentIntervalMs * 2.5).toLong().coerceAtLeast(6000L)

        _state.update { current ->
            val quality = classifySignalQuality(
                usedInFix = current.satellitesUsedInFix,
                total = current.satellitesTotal,
                avgCn0 = current.averageCn0DbHz,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
            )
            val updatedGnssWallTime = if (sourceType == LocationSourceType.GNSS) {
                location.time
            } else {
                current.lastValidGnssFixWallTimeMs
            }
            val updatedGnssElapsedNanos = if (sourceType == LocationSourceType.GNSS) {
                newNanos
            } else {
                current.lastValidGnssFixElapsedNanos
            }
            current.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                verticalAccuracyMeters = verticalAcc,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                speedMps = speed,
                maxSpeedMps = maxSpeedRecordedMps,
                avgSpeedMps = avgSpeed,
                speedSampleCount = speedSamples,
                bearingDegrees = if (location.hasBearing()) location.bearing else null,
                lastUpdateWallTimeMs = location.time,
                lastUpdateElapsedNanos = newNanos,
                lastValidGnssFixWallTimeMs = updatedGnssWallTime,
                lastValidGnssFixElapsedNanos = updatedGnssElapsedNanos,
                locationAgeSeconds = ageSec,
                updateIntervalMs = intervalMs,
                updateDelayDetected = delayDetected,
                locationStabilityLabel = stability,
                providerName = location.provider ?: "unknown",
                locationSourceType = sourceType,
                isLocationAvailable = true,
                signalQualityLabel = quality,
                fixStatus = resolveFixStatus(
                    hasPermission = current.hasFineLocationPermission || current.hasCoarseLocationPermission,
                    hasGnssHardware = current.hasGnssHardware,
                    locationEnabled = current.isLocationServicesEnabled,
                    gpsEnabled = current.isGpsProviderEnabled,
                    lastElapsedNanos = newNanos
                )
            )
        }
    }

    private fun resolveFixStatus(
        hasPermission: Boolean,
        hasGnssHardware: Boolean,
        locationEnabled: Boolean,
        gpsEnabled: Boolean,
        lastElapsedNanos: Long?
    ): GpsFixStatus {
        if (!hasPermission) return GpsFixStatus.PERMISSION_DENIED
        if (!locationEnabled || !gpsEnabled) return GpsFixStatus.GPS_DISABLED
        if (!hasGnssHardware) return GpsFixStatus.HARDWARE_UNAVAILABLE
        if (lastElapsedNanos == null) return GpsFixStatus.SEARCHING
        val ageSec = (SystemClock.elapsedRealtimeNanos() - lastElapsedNanos) / 1_000_000_000L
        return if (ageSec > 20L) GpsFixStatus.STALE_FIX else GpsFixStatus.FIX_ACQUIRED
    }

    private fun computeStabilityLabel(fixes: ArrayDeque<Location>): String {
        if (fixes.size < 3) return "Calibrating (${fixes.size}/3 fixes)"
        val accValues = fixes.filter { it.hasAccuracy() }.map { it.accuracy }
        val avgAcc = if (accValues.isNotEmpty()) accValues.average() else 99.0
        var maxJumpMeters = 0f
        val list = fixes.toList()
        for (i in 1 until list.size) {
            val prev = list[i - 1]
            val curr = list[i]
            val dist = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            // Ignore genuine rider movement when speed > 2 m/s
            val speed = curr.speed
            if (speed < 1.5f && dist > maxJumpMeters) {
                maxJumpMeters = dist
            }
        }
        return when {
            avgAcc <= 10.0 && maxJumpMeters < 12f -> "High Stability"
            avgAcc <= 25.0 && maxJumpMeters < 30f -> "Moderate Stability"
            else -> "Low Stability / Drift"
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return abs((r * c).toFloat())
    }

    private fun classifySignalQuality(
        usedInFix: Int,
        total: Int,
        avgCn0: Float?,
        accuracyMeters: Float?
    ): String {
        val acc = accuracyMeters ?: 999f
        val cn0 = avgCn0 ?: 0f
        return when {
            usedInFix >= 6 && (cn0 >= 28f || acc <= 10f) -> "Excellent GNSS Fix"
            usedInFix >= 4 && acc <= 25f -> "Good Rider Fix"
            total > 0 && usedInFix < 4 -> "Weak / Acquiring Fix"
            acc <= 30f -> "Active Location Fix"
            total == 0 && accuracyMeters == null -> "Searching Satellites"
            else -> "Fair / Urban Multipath"
        }
    }

    private fun mapConstellation(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        else -> "Other"
    }
}
