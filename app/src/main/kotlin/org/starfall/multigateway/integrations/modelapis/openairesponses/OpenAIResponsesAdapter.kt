package org.starfall.multigateway.integrations.modelapis.openairesponses

import kotlinx.coroutines.flow.Flow
import com.openai.models.responses.EasyInputMessage
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.shared.model.api.*
import org.starfall.multigateway.shared.model.auth.*
import org.starfall.multigateway.shared.model.generation.*
import org.starfall.multigateway.shared.model.language.*
import org.starfall.multigateway.shared.model.provider.*
import org.starfall.multigateway.integrations.modelapis.sdk.useClient
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.integrations.modelapis.ModelApiAdapter
import org.starfall.multigateway.integrations.modelapis.ModelApiRequest
import org.starfall.multigateway.integrations.modelapis.sdk.OfficialProviderSdk

internal class OpenAIResponsesAdapter(private val sdk: OfficialProviderSdk) : ModelApiAdapter {
    override val modelApiType = ModelApiType.OPENAI_RESPONSES


    override suspend fun testConnection(provider: ModelProvider): Result<String> = sdk.connectionResult(provider) {
        sdk.sdkCall { sdk.openAi(provider).useClient { it.models().list() } }
    }

    override fun stream(request: ModelApiRequest): Flow<GenerationEvent> = streamResponses(
        request.provider, request.modelName, request.messages, request.systemPrompt,
        request.temperature, request.topP, request.maxTokens, request.reasoningEffort, request.sendThinkingContent, request.enabledModelTools
    )

    private fun streamResponses(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int,
        reasoningEffort: String? = null,
        sendThinkingContent: Boolean = false,
        enabledModelTools: List<String> = emptyList()
    ): Flow<GenerationEvent> = flow {
        sdk.openAi(provider).useClient { client ->
            val inputItems = buildList {
                messages.forEach { message ->
                    if (message.role == ChatRole.MODEL && sendThinkingContent) {
                        message.reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
                            val content = com.openai.models.responses.ResponseReasoningItem.Content.builder()
                                .text(reasoning)
                                .build()
                            val reasoningItem = com.openai.models.responses.ResponseReasoningItem.builder()
                                .id("rs_${message.id}")
                                .summary(emptyList())
                                .content(listOf(content))
                                .build()
                            add(ResponseInputItem.ofReasoning(reasoningItem))
                        }
                    }
                    val easy = EasyInputMessage.builder()
                        .role(if (message.role == ChatRole.MODEL) EasyInputMessage.Role.ASSISTANT else EasyInputMessage.Role.USER)
                    if (message.role == ChatRole.USER && message.files.isNotEmpty()) {
                        easy.contentOfResponseInputMessageContentList(sdk.openAiResponseContent(message))
                    } else {
                        easy.content(message.content)
                    }
                    add(ResponseInputItem.ofEasyInputMessage(easy.build()))
                }
            }
            val params = ResponseCreateParams.builder().model(modelName).maxOutputTokens(maxTokens.toLong()).store(false)
                .inputOfResponse(inputItems)
            val nativeTools = requestToolsForOpenAIResponses(enabledModelTools)
            if (nativeTools.isNotEmpty()) params.putAdditionalBodyProperty("tools", com.openai.core.JsonValue.from(nativeTools))
            if (systemPrompt.isNotBlank()) params.instructions(systemPrompt)
            temperature?.let { params.temperature(it) }
            topP?.let { params.topP(it) }
            reasoningEffort?.trim()?.takeIf { it.isNotEmpty() }?.let { effort ->
                params.putAdditionalBodyProperty(
                    "reasoning",
                    com.openai.core.JsonValue.from(mapOf("effort" to effort))
                )
            }
            if (!provider.config.supportStream) {
                val response = sdk.sdkCall { client.responses().create(params.build()) }
                check(!response.error().isPresent) { "OpenAI Responses request failed" }
                response.output().forEach { item ->
                    item.reasoning().orElse(null)?.content()?.orElse(emptyList())?.forEach { part ->
                        part.text().takeIf { it.isNotBlank() }?.let { emit(GenerationEvent.Reasoning(it)) }
                    }
                    item.message().orElse(null)?.content()?.forEach { content ->
                        content.outputText().orElse(null)?.text()?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                    }
                }
                response._usage().asKnown().orElse(null)?.tokenUsage()?.let { emit(GenerationEvent.Usage(it)) }
                return@useClient
            }
            sdk.sdkCall { client.responses().createStreaming(params.build()) }.use { response ->
                val iterator = response.stream().iterator()
                var finished = false
                var usage: TokenUsage? = null
                while (sdk.sdkCall { iterator.hasNext() }) {
                    val event = iterator.next()
                    check(!event.error().isPresent && !event.failed().isPresent) { "OpenAI Responses stream failed" }
                    event.reasoningTextDelta().orElse(null)?.delta()?.takeIf { it.isNotEmpty() }?.let {
                        emit(GenerationEvent.Reasoning(it))
                    }
                    event.outputTextDelta().orElse(null)?.delta()?.takeIf { it.isNotEmpty() }?.let {
                        emit(GenerationEvent.Text(it))
                    }
                    if (event.completed().isPresent || event.incomplete().isPresent) {
                        finished = true
                        val finalResponse = event.completed().orElse(null)?._response()?.asKnown()?.orElse(null)
                            ?: event.incomplete().orElse(null)?._response()?.asKnown()?.orElse(null)
                        finalResponse?._usage()?.asKnown()?.orElse(null)?.tokenUsage()?.let { usage = it }
                    }
                }
                check(finished) { "OpenAI Responses stream ended without a final response" }
                usage?.let { emit(GenerationEvent.Usage(it)) }
            }
        }
    }.flowOn(Dispatchers.IO)
}

private fun ResponseUsage.tokenUsage(): TokenUsage? {
    val input = _inputTokens().asKnown().orElse(null) ?: return null
    val outputField = _outputTokens()
    val output = outputField.asKnown().orElse(null)
    if (output == null && !outputField.isMissing() && !outputField.isNull()) return null
    return TokenUsage(input, output).takeIf { it.isValid }
}


private fun requestToolsForOpenAIResponses(enabled: List<String>): List<Map<String, Any>> =
    enabled.distinct().mapNotNull { tool ->
        when (tool) {
            "web_search" -> mapOf("type" to "web_search")
            "code_interpreter" -> mapOf("type" to "code_interpreter", "container" to mapOf("type" to "auto"))
            "image_generation" -> mapOf("type" to "image_generation")
            else -> null
        }
    }
