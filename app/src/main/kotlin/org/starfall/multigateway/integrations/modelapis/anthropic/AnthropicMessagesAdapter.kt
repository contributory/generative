package org.starfall.multigateway.integrations.modelapis.anthropic

import kotlinx.coroutines.flow.Flow
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.shared.model.api.*
import org.starfall.multigateway.shared.model.auth.*
import org.starfall.multigateway.shared.model.generation.*
import org.starfall.multigateway.shared.model.language.*
import org.starfall.multigateway.shared.model.provider.*
import org.starfall.multigateway.shared.model.claudeThinkingParameters
import org.starfall.multigateway.shared.model.claudeAllowsSampling
import org.starfall.multigateway.integrations.modelapis.sdk.useClient
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.integrations.modelapis.ModelApiAdapter
import org.starfall.multigateway.integrations.modelapis.ModelApiRequest
import org.starfall.multigateway.integrations.modelapis.sdk.OfficialProviderSdk

internal class AnthropicMessagesAdapter(private val sdk: OfficialProviderSdk) : ModelApiAdapter {
    override val modelApiType = ModelApiType.ANTHROPIC


    override suspend fun testConnection(provider: ModelProvider): Result<String> = sdk.connectionResult(provider) {
        sdk.sdkCall { sdk.anthropic(provider).useClient { it.models().list() } }
    }

    override fun stream(request: ModelApiRequest): Flow<GenerationEvent> = streamAnthropic(
        request.provider, request.modelName, request.messages, request.systemPrompt,
        request.temperature, request.topP, request.maxTokens, request.topK, request.sendThinkingContent, request.enabledModelTools
    )

    private fun streamAnthropic(
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
        sdk.anthropic(provider).useClient { client ->
            val params = MessageCreateParams.builder().model(modelName).maxTokens(maxTokens.toLong())
            val nativeTools = requestToolsForAnthropic(enabledModelTools)
            if (nativeTools.isNotEmpty()) params.putAdditionalBodyProperty("tools", com.anthropic.core.JsonValue.from(nativeTools))
            val modelConfig = provider.config.modelConfigs[modelName]
            modelConfig?.claudeThinkingParameters(modelName, maxTokens)?.forEach { (key, value) ->
                params.putAdditionalBodyProperty(key, com.anthropic.core.JsonValue.from(
                    value.jsonObject.mapValues { (_, field) ->
                        val primitive = field.jsonPrimitive
                        if (primitive.isString) primitive.content else primitive.intOrNull ?: primitive.content
                    }))
            }
            if (systemPrompt.isNotBlank()) params.system(systemPrompt)
            messages.forEach { message ->
                if (message.role == ChatRole.MODEL && sendThinkingContent && !message.reasoningContent.isNullOrBlank()) {
                    val blocks = mutableListOf<com.anthropic.models.messages.ContentBlockParam>()
                    val thinking = com.anthropic.models.messages.ThinkingBlockParam.builder()
                        .thinking(message.reasoningContent!!)
                        .signature(message.reasoningSignature.orEmpty())
                        .build()
                    blocks += com.anthropic.models.messages.ContentBlockParam.ofThinking(thinking)
                    if (message.content.isNotEmpty()) {
                        blocks += com.anthropic.models.messages.ContentBlockParam.ofText(message.content)
                    }
                    params.addMessage(
                        com.anthropic.models.messages.MessageParam.builder()
                            .role(com.anthropic.models.messages.MessageParam.Role.ASSISTANT)
                            .contentOfBlockParams(blocks)
                            .build()
                    )
                } else if (message.role == ChatRole.MODEL) {
                    params.addAssistantMessage(message.content)
                } else if (message.files.isNotEmpty()) {
                    params.addMessage(
                        com.anthropic.models.messages.MessageParam.builder()
                            .role(com.anthropic.models.messages.MessageParam.Role.USER)
                            .contentOfBlockParams(sdk.anthropicContent(message))
                            .build()
                    )
                } else {
                    params.addUserMessage(message.content)
                }
            }
            @Suppress("DEPRECATION")
            run {
                if ((modelConfig ?: ModelConfiguration()).claudeAllowsSampling(modelName, maxTokens)) {
                    temperature?.let { params.temperature(it) }
                    topP?.let { params.topP(it) }
                    topK?.let { params.topK(it.toLong()) }
                }
            }

            if (!provider.config.supportStream) {
                sdk.sdkCall { client.messages().create(params.build()) }.content().forEach { block ->
                    block.thinking().orElse(null)?.let { thinking ->
                        emit(GenerationEvent.Reasoning(thinking.thinking(), thinking.signature()))
                    }
                    block.text().orElse(null)?.text()?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                }
                return@useClient
            }

            sdk.sdkCall { client.messages().createStreaming(params.build()) }.use { response ->
                val iterator = response.stream().iterator()
                while (sdk.sdkCall { iterator.hasNext() }) {
                    val delta = iterator.next().contentBlockDelta().orElse(null)?.delta() ?: continue
                    delta.thinking().orElse(null)?.thinking()?.takeIf { it.isNotEmpty() }?.let {
                        emit(GenerationEvent.Reasoning(it))
                    }
                    delta.signature().orElse(null)?.signature()?.takeIf { it.isNotEmpty() }?.let {
                        emit(GenerationEvent.Reasoning("", it))
                    }
                    delta.text().orElse(null)?.text()?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}


private fun requestToolsForAnthropic(enabled: List<String>): List<Map<String, Any>> =
    enabled.distinct().mapNotNull { tool ->
        when (tool) {
            "web_search" -> mapOf("type" to "web_search_20250305", "name" to "web_search")
            "web_fetch" -> mapOf("type" to "web_fetch_20250910", "name" to "web_fetch")
            "code_execution" -> mapOf("type" to "code_execution_20250825", "name" to "code_execution")
            else -> null
        }
    }
