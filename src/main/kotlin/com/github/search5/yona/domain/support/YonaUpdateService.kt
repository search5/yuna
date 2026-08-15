package com.github.search5.yona.domain.support

import org.eclipse.jgit.api.Git
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.logging.Logger

@Service
class YonaUpdateService(
    @Value("\${yuna.update.repository-url:https://github.com/yona-projects/yona.git}")
    private val repositoryUrl: String,
    @Value("\${yuna.update.current-version:1.15.0}")
    private val currentVersion: String
) {
    private val log = Logger.getLogger(YonaUpdateService::class.java.name)

    private var latestVersion: String? = null
    private var isUpdateRequired: Boolean = false
    var isWatched: Boolean = true

    fun getLatestVersion(): String? = latestVersion
    fun isUpdateRequired(): Boolean = isUpdateRequired
    fun getReleaseUrl(): String = "https://github.com/yona-projects/yona/releases/tag/v${latestVersion ?: ""}"

    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000, initialDelay = 10000)
    fun refreshVersionToUpdate() {
        try {
            log.info("Fetching the latest Yona version from remote: $repositoryUrl")
            val tags = Git.lsRemoteRepository()
                .setRemote(repositoryUrl)
                .setHeads(false)
                .setTags(true)
                .call()

            var highestVersion: List<Int>? = null
            var highestVersionStr: String? = null

            for (ref in tags) {
                val rawTag = ref.name.replaceFirst("^refs/tags/", "")
                val cleanTag = if (rawTag.startsWith("v")) rawTag.substring(1) else rawTag
                val versionParts = parseVersion(cleanTag) ?: continue

                if (highestVersion == null || compareVersions(versionParts, highestVersion) > 0) {
                    highestVersion = versionParts
                    highestVersionStr = cleanTag
                }
            }

            val currentParts = parseVersion(currentVersion) ?: listOf(1, 15, 0)
            if (highestVersion != null && compareVersions(highestVersion, currentParts) > 0) {
                latestVersion = highestVersionStr
                isUpdateRequired = true
                log.info("A new Yona version is available: $latestVersion (Current: $currentVersion)")
            } else {
                latestVersion = null
                isUpdateRequired = false
                log.info("Yona is up to date (Current: $currentVersion)")
            }
        } catch (e: Exception) {
            log.warning("Failed to check for Yona updates: ${e.message}")
        }
    }

    private fun parseVersion(versionStr: String): List<Int>? {
        // "1.15.0" -> [1, 15, 0]
        val clean = versionStr.split("-").firstOrNull() ?: return null
        val parts = clean.split(".")
        if (parts.size < 2) return null
        return try {
            parts.map { it.toInt() }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun compareVersions(v1: List<Int>, v2: List<Int>): Int {
        val maxSize = maxOf(v1.size, v2.size)
        for (i in 0 until maxSize) {
            val part1 = v1.getOrNull(i) ?: 0
            val part2 = v2.getOrNull(i) ?: 0
            if (part1 != part2) {
                return part1.compareTo(part2)
            }
        }
        return 0
    }
}
