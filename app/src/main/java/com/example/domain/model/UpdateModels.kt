package com.example.domain.model

data class GitHubReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256: String,
    val mandatory: Boolean,
    val minSupportedVersionCode: Int,
    val apkSizeBytes: Long? = null,
    val releaseDate: String? = null
)

sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data class UpdateAvailable(val manifest: GitHubReleaseManifest) : UpdateCheckStatus
    data class UpToDate(val latestManifest: GitHubReleaseManifest) : UpdateCheckStatus
    data class Error(val message: String, val isRateLimited: Boolean = false, val isOffline: Boolean = false) : UpdateCheckStatus
}

sealed interface ApkDownloadStatus {
    data object Idle : ApkDownloadStatus
    data class Downloading(val progressFraction: Float, val downloadedBytes: Long, val totalBytes: Long) : ApkDownloadStatus
    data class Verifying(val stage: String) : ApkDownloadStatus
    data class ReadyToInstall(val manifest: GitHubReleaseManifest, val fileName: String, val sizeBytes: Long) : ApkDownloadStatus
    data class NeedsInstallPermission(val manifest: GitHubReleaseManifest, val fileName: String) : ApkDownloadStatus
    data class Failed(val reason: String) : ApkDownloadStatus
}

data class AppUpdateUiState(
    val currentVersionCode: Int = 1,
    val currentVersionName: String = "1.0",
    val autoUpdateCheckEnabled: Boolean = true,
    val lastCheckTimestampMs: Long? = null,
    val cachedManifest: GitHubReleaseManifest? = null,
    val checkStatus: UpdateCheckStatus = UpdateCheckStatus.Idle,
    val downloadStatus: ApkDownloadStatus = ApkDownloadStatus.Idle,
    val showUpdateDialog: Boolean = false,
    val isRepositoryConfigured: Boolean = false
)
