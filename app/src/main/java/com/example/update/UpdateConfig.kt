package com.example.update

/**
 * Centralized GitHub Self-Update configuration.
 *
 * Replace [GITHUB_OWNER] and [GITHUB_REPOSITORY] with your actual GitHub repository
 * details before publishing releases. No code changes anywhere else are needed.
 */
object UpdateConfig {
    const val GITHUB_OWNER = "jeebanrai05-eng"
    const val GITHUB_REPOSITORY = "Gps-booster-"
    const val GITHUB_BRANCH = "main"

    const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPOSITORY/$GITHUB_BRANCH/version.json"

    const val FALLBACK_VERSION_JSON_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPOSITORY/releases/latest/download/version.json"

    const val RELEASES_PAGE_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPOSITORY/releases"

    const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 hours

    fun isConfigured(url: String = VERSION_JSON_URL): Boolean {
        return url.startsWith("https://", ignoreCase = true) &&
            !url.contains("YOUR_GITHUB_USERNAME", ignoreCase = true) &&
            !url.contains("YOUR_REPOSITORY_NAME", ignoreCase = true)
    }
}
