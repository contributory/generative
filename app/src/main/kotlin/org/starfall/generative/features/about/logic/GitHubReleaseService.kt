package org.starfall.generative.features.about.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

internal data class ApkAsset(val name: String, val sizeBytes: Long, val downloadUrl: String)
internal data class GitHubReleaseInfo(val tagName: String, val title: String, val body: String,
    val pageUrl: String, val apkAssets: List<ApkAsset>, val isPrerelease: Boolean = false)

/**
 * The newest stable release followed by the newest pre-release (flagged [GitHubReleaseInfo.isPrerelease]),
 * when each exists. `/releases/latest` is not used because GitHub never returns pre-releases from it.
 */
internal suspend fun fetchLatestGitHubReleases(): List<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
    val connection = (URL("https://api.github.com/repos/starfall-org/generative/releases?per_page=30").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "Generative-Android")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) error("GitHub returned HTTP $code")
        val releases = Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonArray
            .map { it.jsonObject }
            .filter { it["draft"]?.jsonPrimitive?.booleanOrNull != true }
            .map(::parseRelease)
        // The API lists releases newest first.
        listOfNotNull(releases.firstOrNull { !it.isPrerelease }, releases.firstOrNull { it.isPrerelease })
    } finally { connection.disconnect() }
}

private fun parseRelease(root: JsonObject): GitHubReleaseInfo {
    val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val title = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: tag
    val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val page = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val assets = root["assets"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject }
        .filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true }
        .map { asset -> ApkAsset(asset["name"]?.jsonPrimitive?.content.orEmpty(),
            asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            asset["browser_download_url"]?.jsonPrimitive?.content.orEmpty()) }
    return GitHubReleaseInfo(tag, title, body, page, assets, root["prerelease"]?.jsonPrimitive?.booleanOrNull == true)
}
