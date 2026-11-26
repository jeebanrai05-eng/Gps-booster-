package com.example.engine

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.domain.model.SystemCheckState
import com.example.domain.model.TalabatInstallationStatus
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemOptimizerEngine(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _state = MutableStateFlow(SystemCheckState())
    val state: StateFlow<SystemCheckState> = _state.asStateFlow()

    private val talabatCandidatePackages = listOf(
        "com.talabat.rider",
        "com.deliveryhero.talabat.rider",
        "com.talabat.courier",
        "com.roadrunner.talabat",
        "com.talabat.pandora.rider",
        "com.talabat.logistics.rider",
        "com.deliveryhero.logistics.rider",
        "com.talabat.fleet"
    )

    fun refreshSystemChecks() {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            hasFine || hasCoarse
        }

        val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        val providers = runCatching { locationManager.allProviders }.getOrDefault(emptyList())

        val playServicesAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        val ignoringBatteryOpt = runCatching {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
        val powerSave = powerManager.isPowerSaveMode
        val bgRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activityManager.isBackgroundRestricted
        } else {
            false
        }

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Power"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charger"
            else -> if (isCharging) "Charging" else "On Battery"
        }

        val talabatStatus = detectTalabatRider()

        _state.value = SystemCheckState(
            isLocationServicesEnabled = locationEnabled,
            isGpsProviderEnabled = gpsEnabled,
            isNetworkProviderEnabled = networkEnabled,
            isGooglePlayServicesAvailable = playServicesAvailable,
            hasFineLocationPermission = hasFine,
            hasCoarseLocationPermission = hasCoarse,
            hasBackgroundLocationPermission = hasBg,
            isBatteryOptimizationIgnored = ignoringBatteryOpt,
            isPowerSaveMode = powerSave,
            isBackgroundRestricted = bgRestricted,
            batteryLevelPercent = batteryPct,
            isCharging = isCharging,
            chargingTypeLabel = chargingType,
            availableLocationProviders = providers,
            talabatStatus = talabatStatus
        )
    }

    private fun detectTalabatRider(): TalabatInstallationStatus {
        val pm = context.packageManager
        for (pkg in talabatCandidatePackages) {
            val info = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.getOrNull()
            if (info != null && info.applicationInfo?.enabled == true) {
                return TalabatInstallationStatus.Installed(
                    packageName = pkg,
                    versionName = info.versionName
                )
            }
        }
        return TalabatInstallationStatus.NotInstalled
    }

    fun openLocationSettings() {
        launchActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (!launchActivitySafely(intent)) {
            launchActivitySafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
    }

    fun openAppPermissions(targetPackage: String = context.packageName) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", targetPackage, null)
        }
        launchActivitySafely(intent)
    }

    fun openNetworkSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        if (!launchActivitySafely(intent)) {
            launchActivitySafely(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openTalabatRider(): Boolean {
        val status = detectTalabatRider()
        if (status is TalabatInstallationStatus.Installed) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(status.packageName)
            if (launchIntent != null) {
                return launchActivitySafely(launchIntent)
            }
        }
        return false
    }

    fun openCoordinateInMaps(latitude: Double, longitude: Double): Boolean {
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(Rider+Position)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return launchActivitySafely(intent)
    }

    private fun launchActivitySafely(intent: Intent): Boolean {
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
