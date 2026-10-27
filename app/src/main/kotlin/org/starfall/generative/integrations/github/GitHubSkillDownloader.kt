package org.starfall.generative.integrations.github

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A GitHub URL is resolved to a single SKILL.md file. Never follow arbitrary redirects. */
internal data class GitHubSkillLocation(
    val owner: String,
    val repository: String,
    val ref: String?,
    val folder: List<String>
)

internal fun parseGitHubSkillUrl(text: String): GitHubSkillLocation? {
    val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.port != -1 || uri.userInfo != null ||
        uri.query != null || uri.fragment != null) return null
    val hostname = uri.host?.lowercase() ?: return null
    val parts = uri.path.split('/').filter { it.isNotBlank() }
    if (parts.size < 2 || !parts.all { it != "." && it != ".." &&
        it.matches(Regex("[A-Za-z0-9._-]+")) }) return null
    val owner = parts[0]
    val repo = parts[1].removeSuffix(".git")
    if (owner.isBlank() || repo.isBlank()) return null
    return when (hostname) {
        "github.com", "www.github.com" -> {
            when {
                parts.size == 2 -> GitHubSkillLocation(owner, repo, null, emptyList())
                parts.size >= 4 && parts[2] == "tree" ->
                    GitHubSkillLocation(owner, repo, parts[3], parts.drop(4))
                parts.size >= 5 && parts[2] == "blob" && parts.last() == "SKILL.md" ->
                    GitHubSkillLocation(owner, repo, parts[3], parts.drop(4).dropLast(1))
                else -> null
            }
        }
        "raw.githubusercontent.com" -> if (parts.size >= 4 && parts.last() == "SKILL.md") {
            GitHubSkillLocation(owner, repo, parts[2], parts.drop(3).dropLast(1))
        } else null
        else -> null
    }
}

/** Bounded GitHub-only download. A repository URL resolves its default branch via the GitHub API. */
internal suspend fun downloadGitHubSkill(url: String): String = withContext(Dispatchers.IO) {
    val location = requireNotNull(parseGitHubSkillUrl(url)) { "Invalid GitHub SKILL.md URL" }
    val branch = location.ref ?: run {
        val metadata = fetchGitHubText("https://api.github.com/repos/${location.owner}/${location.repository}")
        JSONObject(metadata).getString("default_branch")
    }
    require(branch.matches(Regex("[A-Za-z0-9._/-]+")) &&
        branch.split('/').all { it != "." && it != ".." }) { "Invalid GitHub branch" }
    val target = (listOf(location.owner, location.repository, branch) + location.folder + "SKILL.md")
        .joinToString("/")
    fetchGitHubText("https://raw.githubusercontent.com/$target")
}

private fun fetchGitHubText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "text/plain, application/vnd.github+json")
        setRequestProperty("User-Agent", "Generative-AgentSkills")
    }
    try {
        require(connection.responseCode == HttpURLConnection.HTTP_OK) {
            "GitHub returned HTTP ${connection.responseCode}"
        }
        val bytes = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val block = ByteArray(8192)
            while (true) {
                val count = input.read(block)
                if (count == -1) break
                require(bytes.size() + count <= MAX_SKILL_FILE_BYTES) { "SKILL.md exceeds 256 KB" }
                bytes.write(block, 0, count)
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    } finally {
        connection.disconnect()
    }
}

const val MAX_SKILL_FILE_BYTES = 256 * 1024
