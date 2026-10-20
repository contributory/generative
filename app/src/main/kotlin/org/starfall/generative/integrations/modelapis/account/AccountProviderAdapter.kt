package org.starfall.generative.integrations.modelapis.account

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import org.starfall.generative.shared.model.language.DiscoveredModel
import org.starfall.generative.shared.model.generation.GenerationEvent
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.StoredMessage

/**
 * Boundary for account-backed providers that do not use a normal API key.
 *
 * Keep provider-specific OAuth, token storage and wire behavior behind this
 * interface so removing an adapter only requires removing its registry entry
 * and ModelApiType dispatch.
 */
internal interface AccountProviderAdapter {
    val providerType: ModelApiType

    suspend fun authorize(provider: ModelProvider): Result<ModelProvider>
    suspend fun fetchModels(provider: ModelProvider): List<String>
    suspend fun fetchModelCatalog(provider: ModelProvider): List<DiscoveredModel> =
        fetchModels(provider).map { DiscoveredModel(it) }
    suspend fun testConnection(provider: ModelProvider): Result<String>
    suspend fun prepareAuthenticatedProvider(provider: ModelProvider): ModelProvider

    suspend fun prepareModelProvider(provider: ModelProvider, modelName: String): ModelProvider =
        prepareAuthenticatedProvider(provider)

    fun normalizeToolRequest(
        sourceProvider: ModelProvider,
        wireProvider: ModelProvider,
        body: JsonObject,
        systemPrompt: String
    ): JsonObject = body

    fun streamEvents(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String,
        maxOutputTokens: Int
    ): Flow<GenerationEvent>

    fun prepareRequestProvider(sourceProvider: ModelProvider, wireProvider: ModelProvider, body: JsonObject): ModelProvider = wireProvider

    fun requestUrl(sourceProvider: ModelProvider, wireProvider: ModelProvider, defaultUrl: String): String = defaultUrl

    fun unwrapResponse(response: JsonObject): JsonObject = response

    fun clearCredentials(providerId: String)
}
