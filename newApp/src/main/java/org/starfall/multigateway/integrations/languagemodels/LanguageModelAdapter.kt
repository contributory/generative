package org.starfall.multigateway.integrations.languagemodels

import kotlinx.coroutines.flow.Flow
import org.starfall.multigateway.shared.model.GenerationEvent
import org.starfall.multigateway.shared.model.ModelProvider
import org.starfall.multigateway.shared.model.ProviderType
import org.starfall.multigateway.shared.model.StoredMessage

internal data class LanguageModelRequest(
    val provider: ModelProvider,
    val modelName: String,
    val messages: List<StoredMessage>,
    val systemPrompt: String,
    val temperature: Double?,
    val topP: Double?,
    val topK: Int?,
    val maxTokens: Int,
    val reasoningEffort: String? = null,
    val sendThinkingContent: Boolean = false
)

internal interface LanguageModelAdapter {
    val providerType: ProviderType
    fun stream(request: LanguageModelRequest): Flow<GenerationEvent>
    suspend fun testConnection(provider: ModelProvider): Result<String>
}
