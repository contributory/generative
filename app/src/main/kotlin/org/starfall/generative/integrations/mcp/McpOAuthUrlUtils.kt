package org.starfall.generative.integrations.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl



/** Pure URL, metadata-discovery and bearer-challenge helpers shared by MCP OAuth orchestration. */
internal fun protectedResourceMetadataUrl(endpoint: HttpUrl, includePath: Boolean): String {
    val suffix = if (includePath && endpoint.encodedPath != "/") endpoint.encodedPath else ""
    return endpoint.newBuilder()
        .encodedPath("/.well-known/oauth-protected-resource$suffix")
        .query(null)
        .fragment(null)
        .build()
        .toString()
}

internal fun authorizationMetadataUrls(issuer: HttpUrl): List<String> {
    val path = issuer.encodedPath.takeUnless { it == "/" }.orEmpty()
    val root = issuer.newBuilder().encodedPath("/").query(null).fragment(null)
    return buildList {
        add(root.build().newBuilder()
            .encodedPath("/.well-known/oauth-authorization-server$path").build().toString())
        add(root.build().newBuilder()
            .encodedPath("/.well-known/openid-configuration$path").build().toString())
        if (path.isNotEmpty()) {
            add(issuer.newBuilder()
                .encodedPath(path.trimEnd('/') + "/.well-known/openid-configuration")
                .query(null).fragment(null).build().toString())
        }
    }.distinct()
}

internal fun canonicalResource(value: String): String {
    val url = value.toHttpUrl()
    require(url.fragment == null) { "MCP resource URL must not contain a fragment." }
    val path = if (url.encodedPath == "/") "" else url.encodedPath
    return url.newBuilder()
        .encodedPath(path.ifEmpty { "/" })
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .let { if (path.isEmpty()) it.removeSuffix("/") else it }
}

internal fun requireSecureOAuthUrl(url: HttpUrl, label: String) {
    val loopback = url.host == "127.0.0.1" || url.host == "localhost" || url.host == "::1"
    require(url.isHttps || (url.scheme == "http" && loopback)) {
        "OAuth $label must use HTTPS (HTTP is allowed only for loopback addresses)."
    }
}

internal fun redirectUri(configured: String?): String {
    val value = configured?.trim().takeUnless { it.isNullOrEmpty() } ?: McpOAuthService.REDIRECT_URI
    val uri = runCatching { value.toHttpUrl() }
        .getOrElse { error("OAuth redirect URI is invalid.") }
    require(uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "::1")) {
        "OAuth redirect URI must use http://localhost or http://127.0.0.1."
    }
    require(uri.port in 1..65535 && uri.query == null && uri.fragment == null) {
        "OAuth redirect URI must include a port and must not contain a query or fragment."
    }
    require(uri.encodedPath.isNotEmpty() && uri.encodedPath.startsWith("/")) {
        "OAuth redirect URI must include a callback path."
    }
    return value
}

internal fun bearerParameter(header: String, name: String): String? {
    val escaped = Regex.escape(name)
    val quoted = Regex("(?:^|[,\\s])$escaped\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        .find(header)?.groupValues?.getOrNull(1)
    if (quoted != null) return quoted
    return Regex("(?:^|[,\\s])$escaped\\s*=\\s*([^,\\s]+)", RegexOption.IGNORE_CASE)
        .find(header)?.groupValues?.getOrNull(1)
}

internal fun JsonObject.arrayStrings(name: String): List<String> =
    (this[name] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

internal fun sameUri(a: String, b: String): Boolean =
    runCatching { a.toHttpUrl() == b.toHttpUrl() }.getOrDefault(a == b)

internal fun resourceCoversEndpoint(resource: String, endpoint: String): Boolean = runCatching {
    val advertised = resource.toHttpUrl()
    val target = endpoint.toHttpUrl()
    if (advertised.scheme != target.scheme || advertised.host != target.host || advertised.port != target.port) {
        return@runCatching false
    }
    val resourcePath = advertised.encodedPath.trimEnd('/')
    val endpointPath = target.encodedPath.trimEnd('/')
    resourcePath.isEmpty() || resourcePath == endpointPath || endpointPath.startsWith("$resourcePath/")
}.getOrDefault(false)

