package org.starfall.generative.features.about.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

internal data class ApkAsset(val name: String, val sizeBytes: Long, val downloadUrl: String)
internal data class GitHubReleaseInfo(val tagName: String, val title: String, val body: String,
    val pageUrl: String, val apkAssets: List<ApkAsset>)

/**
 * The latest stable release published on GitHub. The `/releases/latest` endpoint excludes drafts and
 * pre-releases, so pre-release builds are never offered here.
 */
internal suspend fun fetchLatestGitHubRelease(): GitHubReleaseInfo? = withContext(Dispatchers.IO) {
    val connection = (URL("https://api.github.com/repos/starfall-org/generative/releases/latest").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        setRequestProperty("User-Agent", "Generative-Android")
    }
    try {
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
        if (code !in 200..299) error("GitHub returned HTTP $code")
        parseRelease(Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject)
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
    return GitHubReleaseInfo(tag, title, body, page, assets)
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
