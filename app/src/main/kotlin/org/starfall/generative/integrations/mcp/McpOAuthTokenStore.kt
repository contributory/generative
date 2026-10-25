package org.starfall.generative.integrations.mcp

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.starfall.generative.shared.storage.database.SecretCipher

@Serializable
internal data class McpOAuthTokenState(
    val accessToken: String,
    val refreshToken: String = "",
    val tokenType: String = "Bearer",
    val expiresAt: Long? = null,
    val clientId: String,
    val clientSecret: String? = null,
    val tokenEndpointAuthMethod: String = "none",
    val authorizationServer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val resource: String,
    val mcpEndpoint: String? = null,
    val scope: String? = null,
    val redirectUri: String = McpOAuthService.REDIRECT_URI
)

internal class McpOAuthTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("generative.mcp.oauth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(serverId: String): McpOAuthTokenState? {
        val encrypted = preferences.getString(serverId, null) ?: return null
        return runCatching { json.decodeFromString<McpOAuthTokenState>(SecretCipher.decrypt(encrypted)) }.getOrNull()
    }

    fun save(serverId: String, state: McpOAuthTokenState) {
        check(preferences.edit().putString(serverId, SecretCipher.encrypt(json.encodeToString(state))).commit()) {
            "Could not save MCP OAuth credentials."
        }
    }

    fun delete(serverId: String) { preferences.edit().remove(serverId).apply() }

    fun exportAll(): Map<String, JsonObject> = preferences.all.mapNotNull { (serverId, value) ->
        val encrypted = value as? String ?: return@mapNotNull null
        runCatching {
            serverId to json.encodeToJsonElement<McpOAuthTokenState>(json.decodeFromString(SecretCipher.decrypt(encrypted))).jsonObject
        }.getOrNull()
    }.toMap()

    fun importAll(tokens: Map<String, JsonObject>) {
        val editor = preferences.edit()
        tokens.forEach { (serverId, value) ->
            val token = json.decodeFromJsonElement<McpOAuthTokenState>(value)
            editor.putString(serverId, SecretCipher.encrypt(json.encodeToString(token)))
        }
        check(editor.commit()) { "Could not restore MCP OAuth credentials." }
    }
}
