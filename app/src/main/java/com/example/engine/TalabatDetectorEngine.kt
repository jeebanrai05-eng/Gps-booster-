package com.example.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.example.data.local.PreferencesRepository
import com.example.domain.model.InstalledAppOption
import com.example.domain.model.TalabatDetectionResult
import com.example.domain.model.TalabatLaunchOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TalabatDetectorEngine(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(TalabatDetectionResult())
    val state: StateFlow<TalabatDetectionResult> = _state.asStateFlow()

    @Volatile
    private var cachedManualPackage: String? = null

    init {
        scope.launch {
            preferencesRepository.selectedRiderPackageFlow.collect { pkg ->
                cachedManualPackage = pkg
                refreshDetection(savedManualPkgOverride = pkg)
            }
        }
    }

    suspend fun refreshDetectionNow(): TalabatDetectionResult {
        val savedPkg = runCatching { preferencesRepository.selectedRiderPackageFlow.first() }.getOrNull()
        cachedManualPackage = savedPkg
        return refreshDetection(savedManualPkgOverride = savedPkg)
    }

    fun refreshDetection(savedManualPkgOverride: String? = cachedManualPackage): TalabatDetectionResult {
        val pm = context.packageManager
        val savedManualPkg = savedManualPkgOverride?.takeIf { it.isNotBlank() }

        val launchables = queryLaunchableApps(pm)
        val matchingApps = launchables
            .filter { option ->
                option.packageName in VERIFIED_TALABAT_RIDER_PACKAGES ||
                    matchesTalabatRiderLabel(option.appName, option.packageName) ||
                    option.packageName.equals("com.talabat", ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledAppOption> { it.isPriorityMatch }
                    .thenBy { it.appName.lowercase() }
            )

        // Method 1: User-selected manual override (persisted in DataStore)
        if (savedManualPkg != null) {
            val inspected = inspectSpecificPackage(pm, savedManualPkg, launchables, method = "Manual Selection")
            if (inspected != null) {
                val updated = inspected.copy(
                    savedManualPackage = savedManualPkg,
                    matchingApps = matchingApps,
                    allLaunchableApps = launchables
                )
                _state.value = updated
                return updated
            }
        }

        // Method 2: Check verified Talabat Rider package IDs (never auto-pick customer app com.talabat)
        for (pkg in VERIFIED_TALABAT_RIDER_PACKAGES) {
            val inspected = inspectSpecificPackage(pm, pkg, launchables, method = "Verified Package ID")
            if (inspected != null && inspected.isInstalled) {
                val updated = inspected.copy(
                    savedManualPackage = savedManualPkg,
                    matchingApps = matchingApps,
                    allLaunchableApps = launchables
                )
                _state.value = updated
                return updated
            }
        }

        // Method 3: Search launchable app labels & package IDs matching Talabat Rider / Courier keywords
        val labelMatch = launchables.firstOrNull { matchesTalabatRiderLabel(it.appName, it.packageName) }
        if (labelMatch != null) {
            val inspected = inspectSpecificPackage(
                pm,
                labelMatch.packageName,
                launchables,
                method = "Launcher Label Match"
            )
            if (inspected != null) {
                val updated = inspected.copy(
                    savedManualPackage = savedManualPkg,
                    matchingApps = matchingApps,
                    allLaunchableApps = launchables
                )
                _state.value = updated
                return updated
            }
        }

        val customerInstalled = matchingApps.any { it.packageName.equals("com.talabat", ignoreCase = true) }
        val notFoundNote = when {
            savedManualPkg != null -> "Saved app ($savedManualPkg) is no longer installed. Select a Rider app below."
            customerInstalled -> "Only the regular Talabat customer app was found. Select or install Talabat Rider if needed."
            else -> "Talabat Rider not auto-detected. Scan installed apps to select it manually."
        }

        val result = TalabatDetectionResult(
            isInstalled = false,
            detectedAppName = null,
            detectedPackageName = null,
            launchActivityAvailable = false,
            launchActivityName = null,
            appVersion = null,
            detectionMethod = "Not Detected",
            isAppDisabled = false,
            savedManualPackage = savedManualPkg,
            matchingApps = matchingApps,
            allLaunchableApps = launchables,
            errorNote = notFoundNote
        )
        _state.value = result
        return result
    }

    fun selectManualRiderApp(packageName: String?) {
        cachedManualPackage = packageName
        scope.launch {
            preferencesRepository.setSelectedRiderPackage(packageName)
        }
        refreshDetection(savedManualPkgOverride = packageName)
    }

    fun launchTalabatRider(): TalabatLaunchOutcome {
        val current = refreshDetection()
        val pkg = current.detectedPackageName
        if (!current.isInstalled || pkg.isNullOrBlank()) {
            return TalabatLaunchOutcome.NotInstalled
        }
        if (current.isAppDisabled) {
            return TalabatLaunchOutcome.AppDisabled(pkg)
        }

        val pm = context.packageManager
        val launchIntent = buildExactLaunchIntent(pm, pkg, current.launchActivityName)
            ?: return TalabatLaunchOutcome.LauncherUnavailable(pkg)

        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            TalabatLaunchOutcome.Launched(
                appName = current.detectedAppName ?: pkg,
                packageName = pkg
            )
        }.getOrElse { err ->
            TalabatLaunchOutcome.Failed(err.localizedMessage ?: "Android prevented launching $pkg.")
        }
    }

    private fun buildExactLaunchIntent(
        pm: PackageManager,
        packageName: String,
        knownActivityName: String?
    ): Intent? {
        pm.getLaunchIntentForPackage(packageName)?.let { return it }

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val resolved = queryIntentActivitiesCompat(pm, mainIntent).firstOrNull()
        val targetActivityName = resolved?.activityInfo?.name ?: knownActivityName
        return if (!targetActivityName.isNullOrBlank()) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, targetActivityName)
            }
        } else {
            null
        }
    }

    private fun inspectSpecificPackage(
        pm: PackageManager,
        packageName: String,
        launchables: List<InstalledAppOption>,
        method: String
    ): TalabatDetectionResult? {
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrNull() ?: return null

        val appInfo = pkgInfo.applicationInfo
        val isEnabled = appInfo?.enabled ?: true
        val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName

        val launchableMatch = launchables.firstOrNull { it.packageName == packageName }
        val fallbackIntent = pm.getLaunchIntentForPackage(packageName)
        val activityName = launchableMatch?.launcherActivityName ?: fallbackIntent?.component?.className
        val launchAvailable = isEnabled && (launchableMatch != null || fallbackIntent != null)

        val note = when {
            !isEnabled -> "$appName is installed but currently disabled in Android App Info."
            !launchAvailable -> "$appName has no launchable main activity."
            else -> null
        }

        return TalabatDetectionResult(
            isInstalled = true,
            detectedAppName = appName,
            detectedPackageName = packageName,
            launchActivityAvailable = launchAvailable,
            launchActivityName = activityName,
            appVersion = pkgInfo.versionName,
            detectionMethod = method,
            isAppDisabled = !isEnabled,
            errorNote = note
        )
    }

    private fun queryLaunchableApps(pm: PackageManager): List<InstalledAppOption> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = queryIntentActivitiesCompat(pm, intent)
        val seen = LinkedHashMap<String, InstalledAppOption>()

        for (info in resolveList) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == context.packageName) continue
            if (seen.containsKey(pkg)) continue

            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val appInfo = activityInfo.applicationInfo
            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.getOrNull()

            val isPriority = pkg in VERIFIED_TALABAT_RIDER_PACKAGES ||
                matchesTalabatRiderLabel(label, pkg) ||
                pkg.equals("com.talabat", ignoreCase = true)

            seen[pkg] = InstalledAppOption(
                appName = label,
                packageName = pkg,
                versionName = pkgInfo?.versionName,
                launcherActivityName = activityInfo.name,
                isEnabled = appInfo?.enabled ?: true,
                isPriorityMatch = isPriority
            )
        }

        return seen.values.sortedWith(
            compareByDescending<InstalledAppOption> { it.isPriorityMatch }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun queryIntentActivitiesCompat(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        /**
         * Verified Rider/Courier package candidates. Never includes the consumer food-ordering
         * package "com.talabat" so Rider Launch never opens the wrong app automatically.
         */
        val VERIFIED_TALABAT_RIDER_PACKAGES = listOf(
            "com.talabat.rider",
            "com.deliveryhero.talabat.rider",
            "com.talabat.courier",
            "com.roadrunner.talabat",
            "com.talabat.pandora.rider",
            "com.talabat.logistics.rider",
            "com.deliveryhero.logistics.rider",
            "com.talabat.fleet"
        )

        fun matchesTalabatRiderLabel(appLabel: String, packageName: String): Boolean {
            val l = appLabel.lowercase()
            val p = packageName.lowercase()
            if (p == "com.talabat") return false
            val hasTalabat = l.contains("talabat") || p.contains("talabat")
            val hasRiderKeyword = l.contains("rider") ||
                l.contains("courier") ||
                l.contains("driver") ||
                l.contains("roadrunner") ||
                p.contains("rider") ||
                p.contains("courier") ||
                p.contains("roadrunner")
            return hasTalabat && hasRiderKeyword
        }
    }
}
