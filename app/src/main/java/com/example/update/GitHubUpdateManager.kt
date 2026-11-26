package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.local.PreferencesRepository
import com.example.domain.model.ApkDownloadStatus
import com.example.domain.model.AppUpdateUiState
import com.example.domain.model.GitHubReleaseManifest
import com.example.domain.model.UpdateCheckStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class GitHubUpdateManager(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope,
    private val versionJsonUrl: String = UpdateConfig.VERSION_JSON_URL,
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val currentVersionCodeProvider: () -> Int = { BuildConfig.VERSION_CODE },
    private val currentVersionNameProvider: () -> String = { BuildConfig.VERSION_NAME }
) {
    private val _state = MutableStateFlow(
        AppUpdateUiState(
            currentVersionCode = currentVersionCodeProvider(),
            currentVersionName = currentVersionNameProvider(),
            isRepositoryConfigured = UpdateConfig.isConfigured(versionJsonUrl)
        )
    )
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val downloadMutex = Mutex()
    private var verifiedApkFile: File? = null
    private var verifiedManifest: GitHubReleaseManifest? = null
    private var autoCheckTriggeredThisSession = false

    init {
        scope.launch {
            preferencesRepository.updateCacheFlow.collect { snapshot ->
                val currentCode = currentVersionCodeProvider()
                _state.update { current ->
                    val restoredCheckStatus = if (current.checkStatus == UpdateCheckStatus.Idle && snapshot.cachedManifest != null) {
                        if (snapshot.cachedManifest.versionCode > currentCode) {
                            UpdateCheckStatus.UpdateAvailable(snapshot.cachedManifest)
                        } else {
                            UpdateCheckStatus.UpToDate(snapshot.cachedManifest)
                        }
                    } else {
                        current.checkStatus
                    }
                    current.copy(
                        autoUpdateCheckEnabled = snapshot.autoUpdateCheckEnabled,
                        lastCheckTimestampMs = snapshot.lastUpdateCheckMs,
                        cachedManifest = snapshot.cachedManifest,
                        checkStatus = restoredCheckStatus
                    )
                }
            }
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAutoUpdateCheckEnabled(enabled)
        }
        _state.update { it.copy(autoUpdateCheckEnabled = enabled) }
    }

    fun dismissUpdateDialog() {
        _state.update { it.copy(showUpdateDialog = false) }
    }

    fun showUpdateDialogIfAvailable() {
        val status = _state.value.checkStatus
        if (status is UpdateCheckStatus.UpdateAvailable) {
            _state.update { it.copy(showUpdateDialog = true) }
        }
    }

    fun autoCheckUpdatesIfNeeded() {
        if (autoCheckTriggeredThisSession) return
        autoCheckTriggeredThisSession = true
        scope.launch {
            val snapshot = preferencesRepository.updateCacheFlow.first()
            if (!snapshot.autoUpdateCheckEnabled) return@launch
            if (!UpdateConfig.isConfigured(versionJsonUrl)) return@launch

            val lastCheck = snapshot.lastUpdateCheckMs ?: 0L
            val now = System.currentTimeMillis()
            if ((now - lastCheck) >= UpdateConfig.AUTO_CHECK_INTERVAL_MS) {
                checkForUpdates(isManual = false)
            } else {
                val cached = snapshot.cachedManifest
                if (cached != null && cached.versionCode > currentVersionCodeProvider()) {
                    _state.update {
                        it.copy(
                            checkStatus = UpdateCheckStatus.UpdateAvailable(cached),
                            showUpdateDialog = true
                        )
                    }
                }
            }
        }
    }

    suspend fun checkForUpdates(isManual: Boolean = true) {
        if (!UpdateConfig.isConfigured(versionJsonUrl)) {
            if (isManual) {
                _state.update {
                    it.copy(
                        checkStatus = UpdateCheckStatus.Error(
                            message = "GitHub repository not configured yet. Update UpdateConfig.kt with your GitHub owner and repository.",
                            isRateLimited = false,
                            isOffline = false
                        )
                    )
                }
            }
            return
        }

        if (!versionJsonUrl.startsWith("https://", ignoreCase = true)) {
            _state.update {
                it.copy(checkStatus = UpdateCheckStatus.Error("Insecure update endpoint blocked. HTTPS is required."))
            }
            return
        }

        if (!isNetworkAvailable()) {
            _state.update {
                it.copy(
                    checkStatus = UpdateCheckStatus.Error(
                        message = "Offline: Internet connection is required to check GitHub releases.",
                        isOffline = true
                    )
                )
            }
            return
        }

        _state.update { it.copy(checkStatus = UpdateCheckStatus.Checking) }

        val fetchResult = withContext(Dispatchers.IO) {
            runCatching {
                fun fetchManifestString(targetUrl: String): String {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "UltimateRiderBooster/${currentVersionNameProvider()}")
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val rateRemaining = response.header("X-RateLimit-Remaining")
                        if (response.code == 403 && rateRemaining == "0" || response.code == 429) {
                            throw RateLimitException("GitHub rate limit reached. Try again later.")
                        }
                        if (response.code == 404) {
                            throw java.io.FileNotFoundException("HTTP 404 for $targetUrl")
                        }
                        if (!response.isSuccessful) {
                            throw IllegalStateException("GitHub returned HTTP ${response.code}. Verify repository & version.json exist.")
                        }
                        return response.body?.string() ?: throw IllegalStateException("Empty version.json response.")
                    }
                }

                val body = try {
                    fetchManifestString(versionJsonUrl)
                } catch (e: java.io.FileNotFoundException) {
                    val fallback = UpdateConfig.FALLBACK_VERSION_JSON_URL
                    if (fallback != versionJsonUrl && UpdateConfig.isConfigured(fallback)) {
                        try {
                            fetchManifestString(fallback)
                        } catch (e2: Exception) {
                            throw IllegalStateException(
                                "GitHub returned HTTP 404. Ensure repository '${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPOSITORY}' is Public in GitHub Settings and release v1.1.1 is published."
                            )
                        }
                    } else {
                        throw IllegalStateException(
                            "GitHub returned HTTP 404. Ensure repository '${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPOSITORY}' is Public in GitHub Settings and release v1.1.1 is published."
                        )
                    }
                }
                VersionManifestParser.parseAndValidate(body).getOrThrow()
            }
        }

        val now = System.currentTimeMillis()
        fetchResult.fold(
            onSuccess = { manifest ->
                preferencesRepository.saveUpdateCheckResult(now, manifest)
                val currentCode = currentVersionCodeProvider()
                val newStatus = if (manifest.versionCode > currentCode) {
                    UpdateCheckStatus.UpdateAvailable(manifest)
                } else {
                    UpdateCheckStatus.UpToDate(manifest)
                }
                _state.update {
                    it.copy(
                        lastCheckTimestampMs = now,
                        cachedManifest = manifest,
                        checkStatus = newStatus,
                        showUpdateDialog = manifest.versionCode > currentCode
                    )
                }
            },
            onFailure = { err ->
                val isRateLimit = err is RateLimitException
                _state.update {
                    it.copy(
                        checkStatus = UpdateCheckStatus.Error(
                            message = err.message ?: "Unable to check for updates.",
                            isRateLimited = isRateLimit,
                            isOffline = false
                        )
                    )
                }
            }
        )
    }

    fun startUpdateDownload(manifest: GitHubReleaseManifest) {
        scope.launch {
            downloadAndVerifyUpdate(manifest)
        }
    }

    suspend fun downloadAndVerifyUpdate(manifest: GitHubReleaseManifest): Boolean {
        if (!manifest.apkUrl.startsWith("https://", ignoreCase = true)) {
            _state.update { it.copy(downloadStatus = ApkDownloadStatus.Failed("Rejected non-HTTPS APK URL.")) }
            return false
        }

        val currentCode = currentVersionCodeProvider()
        if (manifest.versionCode <= currentCode) {
            _state.update {
                it.copy(downloadStatus = ApkDownloadStatus.Failed("Rejected version downgrade (${manifest.versionCode} <= $currentCode)."))
            }
            return false
        }

        if (!downloadMutex.tryLock()) {
            return false
        }

        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            cleanupUpdatesDir(updatesDir)
            verifiedApkFile = null
            verifiedManifest = null

            val tempFile = File(updatesDir, "rider-booster-download.apk.tmp")
            val finalFile = File(updatesDir, "ultimate-rider-booster-v${sanitizeFileName(manifest.versionName)}.apk")

            _state.update {
                it.copy(downloadStatus = ApkDownloadStatus.Downloading(0f, 0L, manifest.apkSizeBytes ?: 0L))
            }

            val downloadResult = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(manifest.apkUrl)
                        .header("User-Agent", "UltimateRiderBooster/${currentVersionNameProvider()}")
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("APK download failed with HTTP ${response.code}.")
                        }
                        val body = response.body ?: throw IllegalStateException("Empty APK download stream.")
                        val totalBytes = body.contentLength().takeIf { it > 0L } ?: (manifest.apkSizeBytes ?: 0L)
                        var downloaded = 0L
                        var lastProgressEmit = 0L

                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloaded += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressEmit >= 120L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                        lastProgressEmit = now
                                        val fraction = if (totalBytes > 0L) {
                                            (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                        _state.update {
                                            it.copy(
                                                downloadStatus = ApkDownloadStatus.Downloading(
                                                    progressFraction = fraction,
                                                    downloadedBytes = downloaded,
                                                    totalBytes = totalBytes
                                                )
                                            )
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }
                    }

                    // Step 1: SHA-256 verification
                    _state.update { it.copy(downloadStatus = ApkDownloadStatus.Verifying("Verifying SHA-256 checksum...")) }
                    val actualSha = computeFileSha256(tempFile)
                    if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                        throw SecurityException("SHA-256 checksum mismatch. Downloaded APK deleted.")
                    }

                    // Step 2: APK identity, version downgrade, and signing certificate verification
                    _state.update { it.copy(downloadStatus = ApkDownloadStatus.Verifying("Verifying package identity & signature...")) }
                    verifyApkArchiveOrThrow(tempFile, manifest, currentCode)

                    if (finalFile.exists()) finalFile.delete()
                    check(tempFile.renameTo(finalFile)) { "Failed to finalize verified APK file." }
                    finalFile
                }
            }

            return downloadResult.fold(
                onSuccess = { verifiedFile ->
                    verifiedApkFile = verifiedFile
                    verifiedManifest = manifest
                    val canInstall = canRequestPackageInstallsCompat()
                    if (canInstall) {
                        _state.update {
                            it.copy(
                                downloadStatus = ApkDownloadStatus.ReadyToInstall(
                                    manifest = manifest,
                                    fileName = verifiedFile.name,
                                    sizeBytes = verifiedFile.length()
                                )
                            )
                        }
                        launchPackageInstaller(verifiedFile)
                    } else {
                        _state.update {
                            it.copy(
                                downloadStatus = ApkDownloadStatus.NeedsInstallPermission(
                                    manifest = manifest,
                                    fileName = verifiedFile.name
                                )
                            )
                        }
                    }
                    true
                },
                onFailure = { err ->
                    tempFile.delete()
                    finalFile.delete()
                    verifiedApkFile = null
                    verifiedManifest = null
                    _state.update {
                        it.copy(
                            downloadStatus = ApkDownloadStatus.Failed(
                                err.message ?: "Update verification failed. File discarded."
                            )
                        )
                    }
                    false
                }
            )
        } finally {
            downloadMutex.unlock()
        }
    }

    fun retryInstallVerifiedApk(): Boolean {
        val file = verifiedApkFile ?: return false
        val manifest = verifiedManifest ?: return false
        if (!file.exists()) {
            _state.update { it.copy(downloadStatus = ApkDownloadStatus.Failed("Verified APK file missing. Please re-download.")) }
            return false
        }
        return if (canRequestPackageInstallsCompat()) {
            _state.update {
                it.copy(
                    downloadStatus = ApkDownloadStatus.ReadyToInstall(
                        manifest = manifest,
                        fileName = file.name,
                        sizeBytes = file.length()
                    )
                )
            }
            launchPackageInstaller(file)
        } else {
            _state.update {
                it.copy(
                    downloadStatus = ApkDownloadStatus.NeedsInstallPermission(
                        manifest = manifest,
                        fileName = file.name
                    )
                )
            }
            false
        }
    }

    fun openInstallUnknownAppsSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openGitHubReleasesFallback() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateConfig.RELEASES_PAGE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun canRequestPackageInstallsCompat(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    private fun launchPackageInstaller(apkFile: File): Boolean {
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        return runCatching {
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse { err ->
            _state.update {
                it.copy(downloadStatus = ApkDownloadStatus.Failed("Could not open Android installer: ${err.message}"))
            }
            false
        }
    }

    internal fun verifyApkArchiveOrThrow(
        apkFile: File,
        manifest: GitHubReleaseManifest,
        currentVersionCode: Int
    ) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: throw SecurityException("Invalid or unreadable APK archive.")

        if (archiveInfo.packageName != context.packageName) {
            throw SecurityException("APK package mismatch (${archiveInfo.packageName} != ${context.packageName}).")
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (archiveVersionCode <= currentVersionCode.toLong()) {
            throw SecurityException("APK version downgrade rejected ($archiveVersionCode <= $currentVersionCode).")
        }
        if (archiveVersionCode < manifest.versionCode.toLong()) {
            throw SecurityException("APK version ($archiveVersionCode) does not match manifest (${manifest.versionCode}).")
        }

        val installedSigners = getPackageCertSha256Set(pm, context.packageName)
        val archiveSigners = extractCertSha256Set(archiveInfo)
        if (archiveSigners.isEmpty()) {
            throw SecurityException("Downloaded APK contains no valid signing certificate.")
        }
        if (installedSigners.isNotEmpty() && installedSigners.intersect(archiveSigners).isEmpty()) {
            throw SecurityException("APK signing certificate does not match the installed app signature.")
        }
    }

    private fun getPackageCertSha256Set(pm: PackageManager, packageName: String): Set<String> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        }.getOrNull() ?: return emptySet()

        return extractCertSha256Set(info)
    }

    private fun extractCertSha256Set(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        } ?: return emptySet()

        return signatures.map { sig ->
            sha256Hex(sig.toByteArray())
        }.toSet()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun cleanupUpdatesDir(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".tmp") || file.name.endsWith(".apk"))) {
                file.delete()
            }
        }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    class RateLimitException(message: String) : Exception(message)

    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

        fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    digest.update(buf, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
