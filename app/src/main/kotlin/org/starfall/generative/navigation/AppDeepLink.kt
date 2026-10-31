package org.starfall.generative.navigation

import android.net.Uri
import java.util.Locale

/** External actions are parsed once by the Activity, then delivered to the navigation host. */
sealed interface AppDeepLink {
    data object Providers : AppDeepLink
    data class Mcp(val name: String? = null, val url: String? = null) : AppDeepLink
    data class Chat(val text: String, val temporary: Boolean) : AppDeepLink
}

data class AppDeepLinkRequest(val id: Long, val action: AppDeepLink)

/** Returns null for unknown hosts; malformed links for known hosts are rejected. */
internal fun parseAppDeepLink(uri: Uri): AppDeepLink? {
    if (!uri.isHierarchical || !uri.scheme.equals("generative", ignoreCase = true)) return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    if (host !in setOf("providers", "mcp", "chat")) return null
    require(uri.path.isNullOrEmpty() || uri.path == "/") { "Invalid app link path" }
    return when (host) {
        "providers" -> AppDeepLink.Providers
        "mcp" -> {
            val name = uri.getQueryParameter("name")?.trim()
            val url = uri.getQueryParameter("url")?.trim()
            require((name == null && url == null) ||
                (name != null && name.isNotEmpty() && name.length <= 256 &&
                    url != null && url.isNotEmpty() && url.length <= 8192)) { "Invalid MCP link" }
            AppDeepLink.Mcp(name, url)
        }
        else -> {
            val text = uri.getQueryParameter("text")
            require(text != null && text.isNotBlank() && text.length <= 100_000) { "Invalid chat text" }
            val temporary = when (uri.getQueryParameter("temp")?.lowercase(Locale.ROOT)) {
                null, "false" -> false
                "true" -> true
                else -> throw IllegalArgumentException("Invalid temporary chat option")
            }
            AppDeepLink.Chat(text, temporary)
        }
    }
}
