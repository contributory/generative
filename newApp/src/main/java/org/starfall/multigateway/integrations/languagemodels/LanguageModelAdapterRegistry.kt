package org.starfall.multigateway.integrations.languagemodels

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.starfall.multigateway.shared.model.ProviderType
import org.starfall.multigateway.shared.files.AttachmentResolver
import org.starfall.multigateway.integrations.languagemodels.anthropic.AnthropicMessagesAdapter
import org.starfall.multigateway.integrations.languagemodels.gemini.GeminiAdapter
import org.starfall.multigateway.integrations.languagemodels.ollama.OllamaAdapter
import org.starfall.multigateway.integrations.languagemodels.openai.OpenAIChatCompletionsAdapter
import org.starfall.multigateway.integrations.languagemodels.openairesponses.OpenAIResponsesAdapter
import org.starfall.multigateway.integrations.languagemodels.sdk.OfficialProviderSdk

internal class LanguageModelAdapterRegistry(
    context: Context,
    sdk: OfficialProviderSdk,
    httpClient: HttpClient,
    attachments: AttachmentResolver,
    json: Json
) {
    val modelCatalog = ModelCatalogAdapter(httpClient, json)

    private val adapters: Map<ProviderType, LanguageModelAdapter> = listOf(
        OpenAIChatCompletionsAdapter(sdk),
        OpenAIResponsesAdapter(sdk),
        AnthropicMessagesAdapter(sdk),
        GeminiAdapter(sdk),
        OllamaAdapter(context, httpClient, attachments, json)
    ).associateBy(LanguageModelAdapter::providerType)

    fun get(type: ProviderType): LanguageModelAdapter? = adapters[type]

    suspend fun testConnection(provider: org.starfall.multigateway.shared.model.ModelProvider): Result<String> =
        get(provider.type)?.testConnection(provider)
            ?: Result.failure(IllegalArgumentException("No connection adapter is registered for ${provider.type.displayName}."))

    suspend fun cachedOllamaModels(baseUrl: String): List<String>? =
        (adapters[ProviderType.OLLAMA] as OllamaAdapter).cachedModels(baseUrl)

    suspend fun fetchOllamaModels(baseUrl: String): List<String> =
        (adapters[ProviderType.OLLAMA] as OllamaAdapter).fetchModels(baseUrl)
}
