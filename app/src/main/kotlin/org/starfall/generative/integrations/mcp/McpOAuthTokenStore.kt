package org.starfall.generative.integrations.mcp

import android.content.Context
import org.starfall.generative.integrations.security.oauthRecordStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.starfall.generative.integrations.security.SecretCipher

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
    private val namespace = "generative.mcp.oauth"
    private val records by lazy { oauthRecordStorage(context.applicationContext) }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(serverId: String): McpOAuthTokenState? {
        val encrypted = records.get(namespace, serverId) ?: return null
        return runCatching { json.decodeFromString<McpOAuthTokenState>(SecretCipher.decrypt(encrypted)) }.getOrNull()
    }

    fun save(serverId: String, state: McpOAuthTokenState) {
        records.put(namespace, serverId, SecretCipher.encrypt(json.encodeToString(state)))
    }

    fun delete(serverId: String) { records.remove(namespace, serverId) }

    fun exportAll(): Map<String, JsonObject> = records.all(namespace).mapNotNull { (serverId, value) ->
        val encrypted = value
        runCatching {
            serverId to json.encodeToJsonElement<McpOAuthTokenState>(json.decodeFromString(SecretCipher.decrypt(encrypted))).jsonObject
        }.getOrNull()
    }.toMap()

    fun importAll(tokens: Map<String, JsonObject>) {
        val encoded = tokens.mapValues { (_, value) ->
            val token = json.decodeFromJsonElement<McpOAuthTokenState>(value)
            SecretCipher.encrypt(json.encodeToString(token))
        }
        records.replace(namespace, encoded)
    }
}
