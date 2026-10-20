package org.starfall.multigateway.integrations.modelapis.openaicodex

import android.content.Context
import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.starfall.multigateway.shared.auth.OAuthCallbackService
import org.starfall.multigateway.shared.auth.awaitLoopbackAuthorizationCode
import org.starfall.multigateway.integrations.modelapis.account.AccountProviderAdapter
import org.starfall.multigateway.integrations.modelapis.discoveredModel
import org.starfall.multigateway.integrations.modelapis.ModelApiRequest
import org.starfall.multigateway.integrations.modelapis.openairesponses.OpenAIResponsesAdapter
import org.starfall.multigateway.integrations.modelapis.sdk.OfficialProviderSdk
import org.starfall.multigateway.shared.model.auth.AuthMethod
import org.starfall.multigateway.shared.model.provider.oauthCredentialId
import org.starfall.multigateway.shared.model.language.DiscoveredModel
import org.starfall.multigateway.shared.auth.AppAuthTransactions
import org.starfall.multigateway.shared.auth.OAuthBrowser
import org.starfall.multigateway.shared.model.auth.Authorization
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.language.wireReasoningEffort
import org.starfall.multigateway.shared.files.AttachmentResolver

/**
 * Self-contained ChatGPT Codex account adapter.
 *
 * This module owns OAuth, refresh-token rotation, encrypted token persistence,
 * request normalization and Codex wire headers. Core code only dispatches
 * ModelApiType.OPENAI_CODEX here.
 */
internal class OpenAICodexAdapter(
    context: Context,
    attachments: AttachmentResolver
) : AccountProviderAdapter {
    override val providerType: ModelApiType = ModelApiType.OPENAI_CODEX

    private val appContext = context.applicationContext
    private val tokenStore = CodexTokenStore(appContext)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val sdk = OfficialProviderSdk(attachments)
    private val http = HttpClient(CIO) { install(org.starfall.multigateway.shared.logging.DebugLogKtorPlugin) }

    override suspend fun authorize(provider: ModelProvider): Result<ModelProvider> = runCatching {
        require(provider.type == ModelApiType.OPENAI_CODEX) { "Provider is not OpenAI Codex." }

        val request = AppAuthTransactions.authorization("$ISSUER/oauth/authorize", "$ISSUER/oauth/token", CLIENT_ID,
            REDIRECT_URI, AUTH_SCOPE, mapOf("prompt" to "login", "id_token_add_organizations" to "true", "codex_cli_simplified_flow" to "true"))
        OAuthCallbackService.keepAlive(appContext) {
            val code = awaitAuthorizationCode(request.toUri().toString(), request.state!!)
            val exchanged = exchangeAuthorizationCode(code, request.codeVerifier!!)
            withContext(Dispatchers.IO) { tokenStore.save(provider.oauthCredentialId, exchanged) }

            provider.copy(
                name = provider.name.ifBlank { ModelApiType.OPENAI_CODEX.defaultName },
                baseUrl = provider.baseUrl.ifBlank { ModelApiType.OPENAI_CODEX.defaultBaseUrl },
                auth = Authorization(
                    method = AuthMethod.OAUTH,
                    key = exchanged.email ?: exchanged.accountId,
                    value = AUTH_MARKER,
                    oauthAccountId = provider.auth.oauthAccountId
                )
            )
        }
    }

    override suspend fun fetchModels(provider: ModelProvider): List<String> {
        return fetchModelCatalog(provider).map { it.id }
    }

    override suspend fun fetchModelCatalog(provider: ModelProvider): List<DiscoveredModel> {
        val token = ensureToken(provider)
        val endpoint = "https://chatgpt.com/backend-api/codex/models?client_version=$CODEX_CLIENT_VERSION"
        val response = http.get(endpoint) {
            header("Authorization", "Bearer ${token.accessToken}")
            token.accountId?.takeIf { it.isNotBlank() }?.let { header("Chatgpt-Account-Id", it) }
            header("Originator", CODEX_ORIGINATOR)
            header("User-Agent", CODEX_USER_AGENT)
            header("Accept", "application/json")
        }
        check(response.status.isSuccess()) {
            "Codex model catalog returned HTTP ${response.status.value}"
        }
        return parseCodexModelCatalog(json.parseToJsonElement(response.bodyAsText()).jsonObject)
    }

    override suspend fun testConnection(provider: ModelProvider): Result<String> = runCatching {
        val token = ensureToken(provider)
        val identity = token.email ?: token.accountId
        if (identity.isNullOrBlank()) "OpenAI Codex authorization is ready."
        else "OpenAI Codex authorized as $identity."
    }

    override suspend fun prepareAuthenticatedProvider(provider: ModelProvider): ModelProvider {
        val token = ensureToken(provider)
        val sessionId = UUID.randomUUID().toString()
        val headers = linkedMapOf(
            "User-Agent" to CODEX_USER_AGENT,
            "Connection" to "Keep-Alive",
            "Originator" to "codex-tui",
            "Session_id" to sessionId,
            "Conversation_id" to sessionId
        )
        token.accountId?.takeIf { it.isNotBlank() }?.let {
            headers["Chatgpt-Account-Id"] = it
        }
        headers.putAll(provider.config.headers)

        return provider.copy(
            type = ModelApiType.OPENAI_RESPONSES,
            baseUrl = resolveEndpoint(provider.baseUrl),
            auth = Authorization(AuthMethod.BEARER_TOKEN, value = token.accessToken),
            config = provider.config.copy(headers = headers)
        )
    }

    override fun normalizeToolRequest(
        sourceProvider: ModelProvider,
        wireProvider: ModelProvider,
        body: JsonObject,
        systemPrompt: String
    ): JsonObject {
        val sessionId = wireProvider.config.headers["Session_id"] ?: UUID.randomUUID().toString()
        val values = body.toMutableMap()
        listOf(
            "user",
            "metadata",
            "prompt_cache_retention",
            "safety_identifier",
            "stream_options",
            "max_output_tokens",
            "max_completion_tokens",
            "temperature",
            "top_p",
            "frequency_penalty",
            "presence_penalty",
            "previous_response_id"
        ).forEach(values::remove)
        values["instructions"] = JsonPrimitive(
            systemPrompt.takeIf { it.isNotBlank() }
                ?: "You are a coding assistant. Follow the user's request accurately and concisely."
        )
        (values["input"] as? JsonArray)?.let { input ->
            values["input"] = JsonArray(input.filterNot { item ->
                (item as? JsonObject)?.get("role")?.jsonPrimitive?.contentOrNull == "system"
            })
        }
        values["store"] = JsonPrimitive(false)
        values["prompt_cache_key"] = JsonPrimitive(sessionId)

        val include = (values["include"] as? JsonArray).orEmpty().toMutableList()
        if (include.none { it.jsonPrimitive.contentOrNull == "reasoning.encrypted_content" }) {
            include += JsonPrimitive("reasoning.encrypted_content")
        }
        values["include"] = JsonArray(include)

        (values["reasoning"] as? JsonObject)?.let { reasoning ->
            if (reasoning["summary"] == null) {
                values["reasoning"] = JsonObject(reasoning + ("summary" to JsonPrimitive("auto")))
            }
        }
        return JsonObject(values)
    }

    override fun streamEvents(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String,
        maxOutputTokens: Int
    ): Flow<GenerationEvent> = flow {
        val wire = prepareAuthenticatedProvider(provider)
        val instructions = systemPrompt.takeIf(String::isNotBlank)
            ?: "You are a coding assistant. Follow the user's request accurately and concisely."
        emitAll(OpenAIResponsesAdapter(sdk).stream(ModelApiRequest(
            provider = wire,
            modelName = modelName,
            messages = messages,
            systemPrompt = instructions,
            temperature = null,
            topP = null,
            topK = null,
            maxTokens = maxOutputTokens,
            reasoningEffort = provider.config.modelConfigs[modelName]?.wireReasoningEffort,
            sendThinkingContent = false,
            enabledModelTools = emptyList()
        )))
    }

    override fun clearCredentials(providerId: String) {
        tokenStore.delete(providerId)
    }

    private suspend fun ensureToken(provider: ModelProvider): CodexTokenState {
        require(provider.auth.method == AuthMethod.OAUTH && provider.auth.value == AUTH_MARKER) {
            "OpenAI Codex is not authorized. Open OAuth in the browser from Provider settings."
        }
        val current = tokenStore.load(provider.oauthCredentialId)
            ?: error("OpenAI Codex credentials are missing. Sign in again.")

        val expiresAt = current.expiresAt
        if (expiresAt == null || expiresAt > System.currentTimeMillis() + REFRESH_BUFFER_MS) {
            return current
        }

        val refreshed = refresh(current)
        tokenStore.save(provider.oauthCredentialId, refreshed)
        return refreshed
    }

    private suspend fun exchangeAuthorizationCode(code: String, verifier: String): CodexTokenState {
        val request = AppAuthTransactions.token("$ISSUER/oauth/token", CLIENT_ID,
            mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT_URI, "code_verifier" to verifier))
        val payload = AppAuthTransactions.executeToken(request)
        return tokenStateFromResponse(payload.toString(), previousRefreshToken = null)
    }

    private suspend fun refresh(current: CodexTokenState): CodexTokenState {
        val request = AppAuthTransactions.token("$ISSUER/oauth/token", CLIENT_ID,
            mapOf("grant_type" to "refresh_token", "refresh_token" to current.refreshToken, "scope" to REFRESH_SCOPE))
        val payload = AppAuthTransactions.executeToken(request)
        val next = tokenStateFromResponse(payload.toString(), previousRefreshToken = current.refreshToken)
        return next.copy(accountId = next.accountId ?: current.accountId, email = next.email ?: current.email)
    }

    private fun tokenStateFromResponse(
        raw: String,
        previousRefreshToken: String?
    ): CodexTokenState = CodexTokenParser(json).parse(raw, previousRefreshToken)

    private suspend fun awaitAuthorizationCode(authorizationUrl: String, expectedState: String): String =
        awaitLoopbackAuthorizationCode(REDIRECT_URI, authorizationUrl, expectedState) { url ->
            withContext(Dispatchers.Main) {
                OAuthBrowser.open(appContext, url)
            }
        }

    private fun resolveEndpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        if (clean.isBlank()) return DEFAULT_ENDPOINT
        return when {
            clean.endsWith("/backend-api/codex/responses") -> clean
            clean.endsWith("/backend-api/codex") -> "$clean/responses"
            clean.endsWith("/responses") -> clean
            else -> DEFAULT_ENDPOINT
        }
    }

    companion object {
        const val AUTH_MARKER = "openai_codex_oauth"
        const val DEFAULT_ENDPOINT = "https://chatgpt.com/backend-api/codex/responses"

        private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private const val ISSUER = "https://auth.openai.com"
        private const val CALLBACK_PORT = 1455
        private const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        private const val CODEX_ORIGINATOR = "codex-tui"
        private const val CODEX_CLIENT_VERSION = "0.135.0"
        private const val AUTH_SCOPE = "openid email profile offline_access"
        private const val REFRESH_SCOPE = "openid profile email"
        private const val AUTH_TIMEOUT_MS = 5 * 60 * 1000L
        private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
        private const val CODEX_USER_AGENT =
            "codex-tui/$CODEX_CLIENT_VERSION (Android; arm64) MultiGateway"
    }
}


/** Parses the live Codex catalog without maintaining an application-side model allowlist. */
internal fun parseCodexModelCatalog(response: JsonObject): List<DiscoveredModel> {
    val models = response["models"] as? JsonArray
        ?: error("Codex returned an unsupported model catalog")
    return models.mapNotNull { value ->
        val item = value as? JsonObject ?: return@mapNotNull null
        val id = listOf("slug", "id", "model")
            .firstNotNullOfOrNull { key -> item[key]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
            ?: return@mapNotNull null
        discoveredModel(id, item)
    }.distinctBy { it.id }
}
