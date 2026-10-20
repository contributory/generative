package org.starfall.generative.integrations.modelapis

import kotlinx.coroutines.flow.Flow
import org.starfall.generative.shared.model.generation.GenerationEvent
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.StoredMessage

internal data class ModelApiRequest(
    val provider: ModelProvider,
    val modelName: String,
    val messages: List<StoredMessage>,
    val systemPrompt: String,
    val temperature: Double?,
    val topP: Double?,
    val topK: Int?,
    val maxTokens: Int,
    val reasoningEffort: String? = null,
    val sendThinkingContent: Boolean = false,
    val enabledModelTools: List<String> = emptyList()
)

internal interface ModelApiAdapter {
    val modelApiType: ModelApiType
    fun stream(request: ModelApiRequest): Flow<GenerationEvent>
    suspend fun testConnection(provider: ModelProvider): Result<String>
}
