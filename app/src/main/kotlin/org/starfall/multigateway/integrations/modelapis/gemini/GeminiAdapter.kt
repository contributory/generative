package org.starfall.multigateway.integrations.modelapis.gemini

import kotlinx.coroutines.flow.Flow
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.shared.model.api.*
import org.starfall.multigateway.shared.model.auth.*
import org.starfall.multigateway.shared.model.generation.*
import org.starfall.multigateway.shared.model.language.*
import org.starfall.multigateway.shared.model.provider.*
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.integrations.modelapis.ModelApiAdapter
import org.starfall.multigateway.integrations.modelapis.ModelApiRequest
import org.starfall.multigateway.integrations.modelapis.sdk.OfficialProviderSdk

internal class GeminiAdapter(private val sdk: OfficialProviderSdk) : ModelApiAdapter {
    override val modelApiType = ModelApiType.GOOGLE


    override suspend fun testConnection(provider: ModelProvider): Result<String> = sdk.connectionResult(provider) {
        sdk.sdkCall { sdk.google(provider).use { it.models.list(null).iterator().hasNext() } }
    }

    override fun stream(request: ModelApiRequest): Flow<GenerationEvent> = streamGoogle(
        request.provider, request.modelName, request.messages, request.systemPrompt,
        request.temperature, request.topP, request.maxTokens, request.topK, request.sendThinkingContent, request.enabledModelTools
    )

    private fun streamGoogle(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int,
        topK: Int? = null,
        sendThinkingContent: Boolean = false,
        enabledModelTools: List<String> = emptyList()
    ): Flow<GenerationEvent> = flow {
        sdk.google(provider).use { client ->
            val contents = mutableListOf<Content>()
            for (message in messages) {
                contents += Content.builder()
                    .role(if (message.role == ChatRole.MODEL) "model" else "user")
                    .parts(sdk.googleParts(client, message))
                    .build()
            }
            val config = GenerateContentConfig.builder().maxOutputTokens(maxTokens)
            val nativeTools = requestToolsForGemini(enabledModelTools)
            if (nativeTools.isNotEmpty()) config.tools(nativeTools)
            provider.config.modelConfigs[modelName]?.let { modelConfig ->
                if (modelConfig.reasoningEffortDisabled) {
                    config.thinkingConfig(com.google.genai.types.ThinkingConfig.builder().thinkingBudget(0).includeThoughts(false).build())
                } else modelConfig.requestReasoningEffort?.let {
                    val thinking = com.google.genai.types.ThinkingConfig.builder().includeThoughts(true)
                    modelConfig.wireReasoningEffort?.let { level -> thinking.thinkingLevel(level) }
                    config.thinkingConfig(thinking.build())
                }
            }
            if (systemPrompt.isNotBlank()) config.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            temperature?.let { config.temperature(it.toFloat()) }
            topP?.let { config.topP(it.toFloat()) }
            topK?.let { config.topK(it.toFloat()) }
            if (!provider.config.supportStream) {
                sdk.sdkCall { client.models.generateContent(modelName.removePrefix("models/"), contents, config.build()) }
                    .text()?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                return@use
            }
            sdk.sdkCall { client.models.generateContentStream(modelName.removePrefix("models/"), contents, config.build()) }.use { response ->
                val iterator = response.iterator()
                while (sdk.sdkCall { iterator.hasNext() }) {
                    iterator.next().text()?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}


private fun requestToolsForGemini(enabled: List<String>): List<com.google.genai.types.Tool> =
    enabled.distinct().mapNotNull { tool ->
        when (tool) {
            "google_search" -> com.google.genai.types.Tool.builder()
                .googleSearch(com.google.genai.types.GoogleSearch.builder().build()).build()
            "google_maps" -> com.google.genai.types.Tool.builder()
                .googleMaps(com.google.genai.types.GoogleMaps.builder().build()).build()
            "url_context" -> com.google.genai.types.Tool.builder()
                .urlContext(com.google.genai.types.UrlContext.builder().build()).build()
            "code_execution" -> com.google.genai.types.Tool.builder()
                .codeExecution(com.google.genai.types.ToolCodeExecution.builder().build()).build()
            else -> null
        }
    }
