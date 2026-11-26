package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.UpdateCheckStatus
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.screens.GpsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.RiderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.LocalRiderColors
import com.example.ui.theme.RiderBoosterTheme
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.TelemetryViewModel

sealed class RiderDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Home : RiderDestination("home", "HOME", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home")
    data object Gps : RiderDestination("gps", "GPS", Icons.Rounded.GpsFixed, Icons.Outlined.GpsFixed, "nav_gps")
    data object Network : RiderDestination("network", "NETWORK", Icons.Rounded.NetworkCheck, Icons.Outlined.NetworkCheck, "nav_network")
    data object Rider : RiderDestination("rider", "RIDER", Icons.Rounded.DeliveryDining, Icons.Outlined.DeliveryDining, "nav_rider")
    data object Tools : RiderDestination("tools", "TOOLS", Icons.Rounded.Build, Icons.Outlined.Build, "nav_tools")
}

private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as RiderBoosterApplication).container

        setContent {
            val telemetryViewModel: TelemetryViewModel = viewModel(
                factory = TelemetryViewModel.factory(
                    gpsEngine = container.gpsEngine,
                    networkEngine = container.networkEngine,
                    systemEngine = container.systemEngine,
                    compassEngine = container.compassEngine,
                    riderModeController = container.riderModeController,
                    diagnosticRunner = container.diagnosticRunner,
                    smartGpsRecoveryEngine = container.smartGpsRecoveryEngine,
                    talabatDetectorEngine = container.talabatDetectorEngine,
                    gitHubUpdateManager = container.gitHubUpdateManager,
                    historyRepository = container.historyRepository,
                    preferencesRepository = container.preferencesRepository
                )
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    preferencesRepository = container.preferencesRepository,
                    historyRepository = container.historyRepository,
                    gitHubUpdateManager = container.gitHubUpdateManager
                )
            )

            val settings by telemetryViewModel.settings.collectAsStateWithLifecycle()

            RiderBoosterTheme(darkTheme = settings.darkTheme) {
                RiderBoosterAppRoot(
                    telemetryViewModel = telemetryViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun RiderBoosterAppRoot(
    telemetryViewModel: TelemetryViewModel,
    settingsViewModel: SettingsViewModel
) {
    val gpsState by telemetryViewModel.gpsState.collectAsStateWithLifecycle()
    val networkState by telemetryViewModel.networkState.collectAsStateWithLifecycle()
    val systemState by telemetryViewModel.systemState.collectAsStateWithLifecycle()
    val compassState by telemetryViewModel.compassState.collectAsStateWithLifecycle()
    val riderModeState by telemetryViewModel.riderModeState.collectAsStateWithLifecycle()
    val diagnosticState by telemetryViewModel.diagnosticState.collectAsStateWithLifecycle()
    val smartRecoveryState by telemetryViewModel.smartRecoveryState.collectAsStateWithLifecycle()
    val talabatDetection by telemetryViewModel.talabatDetectionState.collectAsStateWithLifecycle()
    val updateState by telemetryViewModel.updateState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val history by settingsViewModel.diagnosticHistory.collectAsStateWithLifecycle()

    val colors = LocalRiderColors.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var pendingStartRiderModeAfterPermission by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        telemetryViewModel.onPermissionResultUpdated()
        if (pendingStartRiderModeAfterPermission) {
            pendingStartRiderModeAfterPermission = false
            telemetryViewModel.startRiderMode()
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        telemetryViewModel.onPermissionResultUpdated()
        if (!fineGranted && !coarseGranted) {
            pendingStartRiderModeAfterPermission = false
            showPermissionRationale = true
        } else if (pendingStartRiderModeAfterPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !riderModeState.hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                pendingStartRiderModeAfterPermission = false
                telemetryViewModel.startRiderMode()
            }
        }
    }

    val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val startRiderModeWithPermissions: () -> Unit = {
        telemetryViewModel.refreshSystemChecks()
        val currentRiderState = telemetryViewModel.riderModeState.value
        when {
            !currentRiderState.hasForegroundLocationPermission -> {
                pendingStartRiderModeAfterPermission = true
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !currentRiderState.hasNotificationPermission -> {
                pendingStartRiderModeAfterPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                telemetryViewModel.startRiderMode()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> telemetryViewModel.onForegroundResume()
                Lifecycle.Event.ON_PAUSE -> telemetryViewModel.onBackgroundPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            telemetryViewModel.onBackgroundPause()
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Precise Location Recommended") },
            text = {
                Text(
                    "Ultimate Rider Booster uses Precise Location only while you are viewing diagnostics to inspect real GNSS satellites, accuracy, and speed. You can also inspect network and system settings without granting location."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        telemetryViewModel.openAppPermissions()
                    }
                ) {
                    Text("Open App Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    val availableManifest = (updateState.checkStatus as? UpdateCheckStatus.UpdateAvailable)?.manifest
    if (updateState.showUpdateDialog && availableManifest != null) {
        UpdateAvailableDialog(
            updateState = updateState,
            manifest = availableManifest,
            onUpdateNow = { telemetryViewModel.startUpdateDownload(it) },
            onLater = { telemetryViewModel.dismissUpdateDialog() },
            onOpenInstallUnknownSettings = { telemetryViewModel.openInstallUnknownAppsSettings() },
            onRetryInstallVerifiedApk = { telemetryViewModel.retryInstallVerifiedApk() }
        )
    }

    val navItems = listOf(
        RiderDestination.Home,
        RiderDestination.Gps,
        RiderDestination.Network,
        RiderDestination.Rider,
        RiderDestination.Tools
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isSettingsRoute = currentDestination?.route == ROUTE_SETTINGS

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.deepBackground,
                        colors.navyBackground,
                        colors.deepBackground
                    )
                )
            )
    ) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isSettingsRoute) {
                    NavigationBar(
                        containerColor = colors.navyBackground.copy(alpha = 0.96f),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.neonCyan,
                                    selectedTextColor = colors.neonCyan,
                                    indicatorColor = colors.electricBlue.copy(alpha = 0.22f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !isSettingsRoute) {
                    NavigationRail(
                        containerColor = colors.navyBackground.copy(alpha = 0.92f),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.neonCyan,
                                    selectedTextColor = colors.neonCyan,
                                    indicatorColor = colors.electricBlue.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = RiderDestination.Home.route,
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(180)) }
                    ) {
                        composable(RiderDestination.Home.route) {
                            HomeDashboardScreen(
                                gpsState = gpsState,
                                networkState = networkState,
                                systemState = systemState,
                                riderModeState = riderModeState,
                                diagnosticState = diagnosticState,
                                smartRecoveryState = smartRecoveryState,
                                talabatDetection = talabatDetection,
                                updateState = updateState,
                                settings = settings,
                                snackbarHostState = snackbarHostState,
                                onStartRiderMode = startRiderModeWithPermissions,
                                onStopRiderMode = { telemetryViewModel.stopRiderMode() },
                                onBoostNow = { telemetryViewModel.runBoostDiagnostics() },
                                onDismissReport = { telemetryViewModel.dismissDiagnosticReport() },
                                onExecuteAction = { actionType ->
                                    telemetryViewModel.executeRecommendedAction(
                                        actionType = actionType,
                                        onRequestLocationPermission = requestLocationPermission
                                    )
                                },
                                onRequestLocationPermission = requestLocationPermission,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onOpenAppPermissions = { pkg -> telemetryViewModel.openAppPermissions(pkg) },
                                onOpenLocationSettings = { telemetryViewModel.openLocationSettings() },
                                onOpenBatterySettings = { telemetryViewModel.openBatterySettings() },
                                onOpenNetworkSettings = { telemetryViewModel.openNetworkSettings() },
                                onRunSmartGpsRecovery = { telemetryViewModel.runSmartGpsRecovery() },
                                onRefreshLocationRequest = { telemetryViewModel.refreshLocationRequest() },
                                onStartGpsMonitoring = { telemetryViewModel.startGpsMonitoring() },
                                onStopGpsMonitoring = { telemetryViewModel.stopGpsMonitoring() },
                                onLaunchTalabatRider = { telemetryViewModel.launchTalabatRider() },
                                onRetryTalabatDetection = { telemetryViewModel.refreshTalabatDetection() },
                                onSelectManualRiderApp = { telemetryViewModel.selectManualRiderApp(it) },
                                onCheckForUpdates = { telemetryViewModel.checkForUpdates() },
                                onOpenUpdateDialog = { telemetryViewModel.showUpdateDialogIfAvailable() },
                                onDownloadAndInstallUpdate = { telemetryViewModel.startUpdateDownload(it) },
                                onOpenInstallUnknownSettings = { telemetryViewModel.openInstallUnknownAppsSettings() },
                                onRetryInstallVerifiedApk = { telemetryViewModel.retryInstallVerifiedApk() },
                                onOpenReleasesPage = { telemetryViewModel.openGitHubReleasesFallback() },
                                onOpenSettingsScreen = {
                                    navController.navigate(ROUTE_SETTINGS) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(RiderDestination.Gps.route) {
                            GpsScreen(
                                gpsState = gpsState,
                                systemState = systemState,
                                smartRecoveryState = smartRecoveryState,
                                recentHistory = history,
                                settings = settings,
                                onToggleMonitoring = { telemetryViewModel.toggleGpsMonitoring() },
                                onStartGpsMonitoring = { telemetryViewModel.startGpsMonitoring() },
                                onStopGpsMonitoring = { telemetryViewModel.stopGpsMonitoring() },
                                onRunSmartGpsRecovery = { telemetryViewModel.runSmartGpsRecovery() },
                                onRefreshLocationRequest = { telemetryViewModel.refreshLocationRequest() },
                                onRequestLocationPermission = requestLocationPermission,
                                onOpenLocationSettings = { telemetryViewModel.openLocationSettings() },
                                onOpenBatterySettings = { telemetryViewModel.openBatterySettings() },
                                onOpenNetworkSettings = { telemetryViewModel.openNetworkSettings() },
                                onOpenAppPermissions = { telemetryViewModel.openAppPermissions() }
                            )
                        }

                        composable(RiderDestination.Network.route) {
                            NetworkScreen(
                                networkState = networkState,
                                settings = settings,
                                onRefreshNetwork = { telemetryViewModel.refreshNetwork() },
                                onRunLatencyTest = { telemetryViewModel.runLatencyTest() },
                                onRunSpeedTest = { telemetryViewModel.runSpeedTest() },
                                onOpenNetworkSettings = { telemetryViewModel.openNetworkSettings() }
                            )
                        }

                        composable(RiderDestination.Rider.route) {
                            RiderScreen(
                                gpsState = gpsState,
                                networkState = networkState,
                                systemState = systemState,
                                riderModeState = riderModeState,
                                talabatDetection = talabatDetection,
                                settings = settings,
                                snackbarHostState = snackbarHostState,
                                onStartRiderMode = startRiderModeWithPermissions,
                                onStopRiderMode = { telemetryViewModel.stopRiderMode() },
                                onRequestLocationPermission = requestLocationPermission,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onLaunchTalabatRider = { telemetryViewModel.launchTalabatRider() },
                                onRetryTalabatDetection = { telemetryViewModel.refreshTalabatDetection() },
                                onSelectManualRiderApp = { telemetryViewModel.selectManualRiderApp(it) },
                                onOpenAppPermissions = { pkg -> telemetryViewModel.openAppPermissions(pkg) },
                                onOpenLocationSettings = { telemetryViewModel.openLocationSettings() },
                                onOpenBatterySettings = { telemetryViewModel.openBatterySettings() },
                                onRunGpsDiagnostics = {
                                    navController.navigate(RiderDestination.Home.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                    telemetryViewModel.runBoostDiagnostics()
                                }
                            )
                        }

                        composable(RiderDestination.Tools.route) {
                            ToolsScreen(
                                gpsState = gpsState,
                                networkState = networkState,
                                systemState = systemState,
                                compassState = compassState,
                                settings = settings,
                                snackbarHostState = snackbarHostState,
                                onStartCompass = { telemetryViewModel.startCompass() },
                                onStopCompass = { telemetryViewModel.stopCompass() },
                                onResetSpeedStats = { telemetryViewModel.resetSpeedStats() },
                                onOpenCoordinateInMaps = { lat, lon ->
                                    telemetryViewModel.openCoordinateInMaps(lat, lon)
                                },
                                onRefreshSystemChecks = { telemetryViewModel.refreshSystemChecks() }
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                updateState = updateState,
                                history = history,
                                onBack = { navController.popBackStack() },
                                onSetDarkTheme = { settingsViewModel.setDarkTheme(it) },
                                onSetAnimationIntensity = { settingsViewModel.setAnimationIntensity(it) },
                                onSetUseMetricUnits = { settingsViewModel.setUseMetricUnits(it) },
                                onSetGpsIntervalMs = { settingsViewModel.setGpsInterval(it) },
                                onSetAutoUpdateCheck = { settingsViewModel.setAutoUpdateCheckEnabled(it) },
                                onCheckForUpdates = { settingsViewModel.checkForUpdates() },
                                onDownloadAndInstallUpdate = { settingsViewModel.startUpdateDownload(it) },
                                onOpenInstallUnknownSettings = { settingsViewModel.openInstallUnknownAppsSettings() },
                                onRetryInstallVerifiedApk = { settingsViewModel.retryInstallVerifiedApk() },
                                onOpenReleasesPage = { settingsViewModel.openGitHubReleasesFallback() },
                                onClearHistory = { settingsViewModel.clearHistory() },
                                onOpenAppPermissions = { telemetryViewModel.openAppPermissions() }
                            )
                        }
                    }
                }
            }
        }
    }
}
