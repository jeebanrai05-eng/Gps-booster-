package com.example.update

import com.example.domain.model.GitHubReleaseManifest
import org.json.JSONObject

object VersionManifestParser {

    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

    fun parseAndValidate(rawJson: String): Result<GitHubReleaseManifest> {
        return runCatching {
            val json = JSONObject(rawJson.trim())

            val versionCode = json.optInt("versionCode", -1)
            require(versionCode > 0) { "versionCode must be a positive integer." }

            val versionName = json.optString("versionName", "").trim()
            require(versionName.isNotEmpty()) { "versionName is required." }

            val releaseTitle = json.optString("releaseTitle", "").trim()
                .ifEmpty { "Ultimate Rider Booster v$versionName" }

            val releaseNotes = json.optString("releaseNotes", "").trim()
            require(releaseNotes.isNotEmpty()) { "releaseNotes is required." }

            val apkUrl = json.optString("apkUrl", "").trim()
            require(apkUrl.startsWith("https://", ignoreCase = true)) {
                "apkUrl must use a valid HTTPS URL."
            }

            val sha256 = json.optString("sha256", "").trim().lowercase()
            require(SHA256_REGEX.matches(sha256)) {
                "sha256 must be a valid 64-character hexadecimal SHA-256 checksum."
            }

            val mandatory = json.optBoolean("mandatory", false)
            val minSupportedVersionCode = json.optInt("minSupportedVersionCode", 1).coerceAtLeast(1)

            val apkSizeBytes = if (json.has("apkSizeBytes") && !json.isNull("apkSizeBytes")) {
                json.optLong("apkSizeBytes", -1L).takeIf { it > 0L }
            } else {
                null
            }

            val releaseDate = json.optString("releaseDate", "").trim().takeIf { it.isNotEmpty() }

            GitHubReleaseManifest(
                versionCode = versionCode,
                versionName = versionName,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkUrl = apkUrl,
                sha256 = sha256,
                mandatory = mandatory,
                minSupportedVersionCode = minSupportedVersionCode,
                apkSizeBytes = apkSizeBytes,
                releaseDate = releaseDate
            )
        }
    }
}
