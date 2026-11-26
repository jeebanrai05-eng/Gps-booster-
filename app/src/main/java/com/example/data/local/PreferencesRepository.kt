package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.AppSettings
import com.example.domain.model.GitHubReleaseManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.WeakHashMap

private val dataStoreCache = WeakHashMap<Context, DataStore<Preferences>>()

private fun getOrCreateDataStore(context: Context): DataStore<Preferences> {
    val appContext = context.applicationContext ?: context
    return synchronized(dataStoreCache) {
        dataStoreCache.getOrPut(appContext) {
            PreferenceDataStoreFactory.create(
                produceFile = { appContext.preferencesDataStoreFile("rider_booster_settings") }
            )
        }
    }
}

data class CachedUpdateSnapshot(
    val autoUpdateCheckEnabled: Boolean = true,
    val lastUpdateCheckMs: Long? = null,
    val cachedManifest: GitHubReleaseManifest? = null
)

class PreferencesRepository(private val context: Context) {
    private val dataStore: DataStore<Preferences> by lazy { getOrCreateDataStore(context) }
    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ANIMATION_INTENSITY = stringPreferencesKey("animation_intensity")
        val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
        val GPS_INTERVAL_MS = longPreferencesKey("gps_interval_ms")
        val RIDER_MODE_ACTIVE = booleanPreferencesKey("rider_mode_active")
        val SELECTED_RIDER_PACKAGE = stringPreferencesKey("selected_rider_package")

        // Update cache & preferences
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
        val CACHED_VERSION_CODE = intPreferencesKey("cached_version_code")
        val CACHED_VERSION_NAME = stringPreferencesKey("cached_version_name")
        val CACHED_RELEASE_TITLE = stringPreferencesKey("cached_release_title")
        val CACHED_RELEASE_NOTES = stringPreferencesKey("cached_release_notes")
        val CACHED_APK_URL = stringPreferencesKey("cached_apk_url")
        val CACHED_SHA256 = stringPreferencesKey("cached_sha256")
        val CACHED_MANDATORY = booleanPreferencesKey("cached_mandatory")
        val CACHED_MIN_SUPPORTED = intPreferencesKey("cached_min_supported")
        val CACHED_APK_SIZE_BYTES = longPreferencesKey("cached_apk_size_bytes")
        val CACHED_RELEASE_DATE = stringPreferencesKey("cached_release_date")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val intensityName = prefs[Keys.ANIMATION_INTENSITY] ?: AnimationIntensity.FULL.name
        val intensity = runCatching { AnimationIntensity.valueOf(intensityName) }
            .getOrDefault(AnimationIntensity.FULL)
        AppSettings(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            animationIntensity = intensity,
            useMetricUnits = prefs[Keys.USE_METRIC_UNITS] ?: true,
            gpsMonitoringIntervalMs = prefs[Keys.GPS_INTERVAL_MS] ?: 2000L
        )
    }

    val riderModeActiveFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.RIDER_MODE_ACTIVE] ?: false
    }

    val selectedRiderPackageFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_RIDER_PACKAGE]
    }

    val updateCacheFlow: Flow<CachedUpdateSnapshot> = dataStore.data.map { prefs ->
        val versionCode = prefs[Keys.CACHED_VERSION_CODE]
        val versionName = prefs[Keys.CACHED_VERSION_NAME]
        val apkUrl = prefs[Keys.CACHED_APK_URL]
        val sha256 = prefs[Keys.CACHED_SHA256]
        val manifest = if (versionCode != null && !versionName.isNullOrBlank() && !apkUrl.isNullOrBlank() && !sha256.isNullOrBlank()) {
            GitHubReleaseManifest(
                versionCode = versionCode,
                versionName = versionName,
                releaseTitle = prefs[Keys.CACHED_RELEASE_TITLE] ?: "Ultimate Rider Booster Update",
                releaseNotes = prefs[Keys.CACHED_RELEASE_NOTES] ?: "",
                apkUrl = apkUrl,
                sha256 = sha256,
                mandatory = prefs[Keys.CACHED_MANDATORY] ?: false,
                minSupportedVersionCode = prefs[Keys.CACHED_MIN_SUPPORTED] ?: 1,
                apkSizeBytes = prefs[Keys.CACHED_APK_SIZE_BYTES],
                releaseDate = prefs[Keys.CACHED_RELEASE_DATE]
            )
        } else {
            null
        }
        CachedUpdateSnapshot(
            autoUpdateCheckEnabled = prefs[Keys.AUTO_UPDATE_CHECK] ?: true,
            lastUpdateCheckMs = prefs[Keys.LAST_UPDATE_CHECK_MS],
            cachedManifest = manifest
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setAnimationIntensity(intensity: AnimationIntensity) {
        dataStore.edit { it[Keys.ANIMATION_INTENSITY] = intensity.name }
    }

    suspend fun setUseMetricUnits(useMetric: Boolean) {
        dataStore.edit { it[Keys.USE_METRIC_UNITS] = useMetric }
    }

    suspend fun setGpsMonitoringIntervalMs(intervalMs: Long) {
        dataStore.edit { it[Keys.GPS_INTERVAL_MS] = intervalMs }
    }

    suspend fun setRiderModeActive(active: Boolean) {
        dataStore.edit { it[Keys.RIDER_MODE_ACTIVE] = active }
    }

    suspend fun setSelectedRiderPackage(packageName: String?) {
        dataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) {
                prefs.remove(Keys.SELECTED_RIDER_PACKAGE)
            } else {
                prefs[Keys.SELECTED_RIDER_PACKAGE] = packageName
            }
        }
    }

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_UPDATE_CHECK] = enabled }
    }

    suspend fun saveUpdateCheckResult(checkedAtMs: Long, manifest: GitHubReleaseManifest?) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK_MS] = checkedAtMs
            if (manifest != null) {
                prefs[Keys.CACHED_VERSION_CODE] = manifest.versionCode
                prefs[Keys.CACHED_VERSION_NAME] = manifest.versionName
                prefs[Keys.CACHED_RELEASE_TITLE] = manifest.releaseTitle
                prefs[Keys.CACHED_RELEASE_NOTES] = manifest.releaseNotes
                prefs[Keys.CACHED_APK_URL] = manifest.apkUrl
                prefs[Keys.CACHED_SHA256] = manifest.sha256
                prefs[Keys.CACHED_MANDATORY] = manifest.mandatory
                prefs[Keys.CACHED_MIN_SUPPORTED] = manifest.minSupportedVersionCode
                if (manifest.apkSizeBytes != null) {
                    prefs[Keys.CACHED_APK_SIZE_BYTES] = manifest.apkSizeBytes
                } else {
                    prefs.remove(Keys.CACHED_APK_SIZE_BYTES)
                }
                if (!manifest.releaseDate.isNullOrBlank()) {
                    prefs[Keys.CACHED_RELEASE_DATE] = manifest.releaseDate
                } else {
                    prefs.remove(Keys.CACHED_RELEASE_DATE)
                }
            }
        }
    }
}
