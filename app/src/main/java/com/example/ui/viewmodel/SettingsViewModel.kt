package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DiagnosticRecordEntity
import com.example.data.local.DiagnosticsHistoryRepository
import com.example.data.local.PreferencesRepository
import com.example.domain.model.AnimationIntensity
import com.example.domain.model.AppSettings
import com.example.domain.model.AppUpdateUiState
import com.example.domain.model.GitHubReleaseManifest
import com.example.update.GitHubUpdateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: DiagnosticsHistoryRepository,
    private val gitHubUpdateManager: GitHubUpdateManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = preferencesRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val diagnosticHistory: StateFlow<List<DiagnosticRecordEntity>> =
        historyRepository.recentRecords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val updateState: StateFlow<AppUpdateUiState> = gitHubUpdateManager.state

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkTheme(enabled) }
    }

    fun setAnimationIntensity(intensity: AnimationIntensity) {
        viewModelScope.launch { preferencesRepository.setAnimationIntensity(intensity) }
    }

    fun setUseMetricUnits(useMetric: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseMetricUnits(useMetric) }
    }

    fun setGpsInterval(intervalMs: Long) {
        viewModelScope.launch { preferencesRepository.setGpsMonitoringIntervalMs(intervalMs) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearAll() }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        gitHubUpdateManager.setAutoUpdateCheckEnabled(enabled)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            gitHubUpdateManager.checkForUpdates(isManual = true)
        }
    }

    fun startUpdateDownload(manifest: GitHubReleaseManifest) {
        gitHubUpdateManager.startUpdateDownload(manifest)
    }

    fun retryInstallVerifiedApk(): Boolean = gitHubUpdateManager.retryInstallVerifiedApk()
    fun openInstallUnknownAppsSettings() = gitHubUpdateManager.openInstallUnknownAppsSettings()
    fun openGitHubReleasesFallback() = gitHubUpdateManager.openGitHubReleasesFallback()
    fun showUpdateDialogIfAvailable() = gitHubUpdateManager.showUpdateDialogIfAvailable()

    companion object {
        fun factory(
            preferencesRepository: PreferencesRepository,
            historyRepository: DiagnosticsHistoryRepository,
            gitHubUpdateManager: GitHubUpdateManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(preferencesRepository, historyRepository, gitHubUpdateManager) as T
            }
        }
    }
}
