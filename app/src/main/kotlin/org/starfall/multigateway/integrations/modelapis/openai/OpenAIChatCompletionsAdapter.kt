package org.starfall.multigateway.integrations.modelapis.openai

import kotlinx.coroutines.flow.Flow
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
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

internal class OpenAIChatCompletionsAdapter(private val sdk: OfficialProviderSdk) : ModelApiAdapter {
    override val modelApiType = ModelApiType.OPENAI


    override suspend fun testConnection(provider: ModelProvider): Result<String> = sdk.connectionResult(provider) {
        sdk.sdkCall { sdk.openAi(provider).useClient { it.models().list() } }
    }

    override fun stream(request: ModelApiRequest): Flow<GenerationEvent> = streamOpenAi(
        request.provider, request.modelName, request.messages, request.systemPrompt,
        request.temperature, request.topP, request.maxTokens, request.reasoningEffort, request.sendThinkingContent
    )

    private fun streamOpenAi(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String,
        temperature: Double?,
        topP: Double?,
        maxTokens: Int,
        reasoningEffort: String? = null,
        sendThinkingContent: Boolean = false
    ): Flow<GenerationEvent> = flow {
        sdk.openAi(provider).useClient { client ->
            val params = ChatCompletionCreateParams.builder().model(modelName).maxTokens(maxTokens.toLong())
            if (systemPrompt.isNotBlank()) params.addSystemMessage(systemPrompt)
            messages.forEach { message ->
                if (message.role == ChatRole.MODEL) {
                    val assistant = com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.builder()
                        .content(message.content)
                    message.reasoningContent
                        ?.takeIf { sendThinkingContent && it.isNotBlank() }
                        ?.let { reasoning ->
                            assistant.putAdditionalProperty(
                                "reasoning_content",
                                com.openai.core.JsonValue.from(reasoning)
                            )
                        }
                    params.addMessage(assistant.build())
                } else if (message.files.isNotEmpty()) {
                    params.addMessage(
                        ChatCompletionUserMessageParam.builder()
                            .contentOfArrayOfContentParts(sdk.openAiChatContent(message))
                            .build()
                    )
                } else {
                    params.addUserMessage(message.content)
                }
            }
            temperature?.let { params.temperature(it) }
            topP?.let { params.topP(it) }
            reasoningEffort?.trim()?.takeIf { it.isNotEmpty() }?.let { effort ->
                params.putAdditionalBodyProperty("reasoning_effort", com.openai.core.JsonValue.from(effort))
            }

            fun reasoningFrom(properties: Map<String, com.openai.core.JsonValue>): String? =
                runCatching { properties["reasoning_content"]?.convert(String::class.java) }
                    .getOrNull()?.takeIf { it.isNotBlank() }

            if (!provider.config.supportStream) {
                val response = sdk.sdkCall { client.chat().completions().create(params.build()) }
                response.choices().firstOrNull()?.message()?.let { message ->
                    reasoningFrom(message._additionalProperties())?.let { emit(GenerationEvent.Reasoning(it)) }
                    message.content().orElse(null)?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                }
                return@useClient
            }

            sdk.sdkCall { client.chat().completions().createStreaming(params.build()) }.use { response ->
                val iterator = response.stream().iterator()
                while (sdk.sdkCall { iterator.hasNext() }) {
                    val delta = iterator.next().choices().firstOrNull()?.delta() ?: continue
                    reasoningFrom(delta._additionalProperties())?.let { emit(GenerationEvent.Reasoning(it)) }
                    delta.content().orElse(null)?.takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
