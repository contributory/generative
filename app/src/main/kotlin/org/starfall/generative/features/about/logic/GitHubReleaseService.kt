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
 * The newest stable release, plus the newest pre-release (flagged [GitHubReleaseInfo.isPrerelease]) when it
 * is newer than that stable release or no stable release exists. `/releases/latest` is not used because
 * GitHub never returns pre-releases from it.
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
        val stable = releases.firstOrNull { !it.isPrerelease }
        val preRelease = releases.filter { it.isPrerelease }
            .maxWithOrNull { a, b -> compareReleaseVersions(a.tagName, b.tagName) }
            ?.takeIf { stable == null || compareReleaseVersions(it.tagName, stable.tagName) > 0 }
        listOfNotNull(stable, preRelease)
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

// Versions look like "1.0.2-123" (main) or "1.0.2-123dev" (other branches): base version, then the
// commit count. The branch suffix is ignored; the commit count orders builds of the same base version.
internal fun compareReleaseVersions(current: String, latest: String): Int {
    val pattern = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-(\d+))?""")
    fun parts(value: String): List<Long> {
        val match = pattern.find(value.trim())
        return (1..4).map { match?.groupValues?.getOrNull(it)?.toLongOrNull() ?: 0L }
    }
    val a = parts(current); val b = parts(latest)
    for (index in a.indices) {
        val comparison = a[index].compareTo(b[index])
        if (comparison != 0) return comparison
    }
    return 0
}
