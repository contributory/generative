package org.starfall.multigateway.integrations.modelapis

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.starfall.multigateway.integrations.modelapis.account.AccountProviderAdapterRegistry
import org.starfall.multigateway.integrations.modelapis.sdk.OfficialProviderSdk
import org.starfall.multigateway.shared.auth.OAuthCallbackService
import org.starfall.multigateway.shared.files.AttachmentResolver
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.generation.hasToolCalls
import org.starfall.multigateway.shared.model.language.DiscoveredModel
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.language.sendsThinkingBack
import org.starfall.multigateway.shared.model.language.wireReasoningEffort
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.streamEnabledFor
import org.starfall.multigateway.shared.storage.preferences.ModelConfigurationMemory
import java.util.Base64

class AiService(context: Context) {
    internal val modelConfigurationMemory = ModelConfigurationMemory(context)
    private val appContext = context.applicationContext

    internal suspend fun <T> withOAuthSession(block: suspend () -> T): T =
        OAuthCallbackService.keepAlive(appContext, block)

    private val attachments = AttachmentResolver(context)
    private val sdk = OfficialProviderSdk(attachments)
    private val accountAdapters = AccountProviderAdapterRegistry(context, attachments)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val adapters = ModelApiAdapterRegistry(context, sdk, httpClient, attachments, json)

    internal suspend fun toolAttachments(
        message: StoredMessage,
        files: ToolFiles,
        imported: MutableMap<String, String>
    ): JsonArray = JsonArray(message.files.map { reference ->
        val meta = attachments.metadata(reference) ?: error("Attachment metadata is missing")
        val bytes = attachments.readBytes(reference) ?: error("Attachment data is missing")
        val uri = imported[reference] ?: ("tool-file:" + files.save(bytes.inputStream(), meta.mimeType)).also {
            imported[reference] = it
        }
        buildJsonObject {
            put("uri", uri)
            put("mimeType", meta.mimeType)
            put("data", Base64.getEncoder().encodeToString(bytes))
        }
    })

    /** Trusted user selections only; model-supplied references use ToolFiles instead. */
    internal suspend fun importToolAttachments(references: List<String>, files: ToolFiles, maxBytes: Long): List<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            references.map { reference ->
                val meta = attachments.metadata(reference) ?: error("Attachment metadata is missing")
                require(meta.sizeBytes <= maxBytes) { "Reference image is too large for the selected generation API." }
                val input = attachments.open(reference) ?: error("Attachment data is missing")
                "tool-file:" + files.save(input, meta.mimeType, maxBytes)
            }
        }

    suspend fun authorizeProvider(provider: ModelProvider): Result<ModelProvider> =
        accountAdapters.get(provider.type)?.authorize(provider)
            ?: Result.failure(IllegalArgumentException("${provider.type.displayName} does not use account authorization."))

    suspend fun prepareAccountProvider(provider: ModelProvider, modelName: String? = null): ModelProvider =
        accountAdapters.get(provider.type)?.let { adapter ->
            if (modelName == null) adapter.prepareAuthenticatedProvider(provider) else adapter.prepareModelProvider(provider, modelName)
        }
            ?: provider

    fun normalizeAccountToolRequest(
        sourceProvider: ModelProvider,
        wireProvider: ModelProvider,
        body: JsonObject,
        systemPrompt: String
    ): JsonObject =
        accountAdapters.get(sourceProvider.type)
            ?.normalizeToolRequest(sourceProvider, wireProvider, body, systemPrompt)
            ?: body

    fun prepareAccountRequest(source: ModelProvider, wire: ModelProvider, body: JsonObject): ModelProvider =
        accountAdapters.get(source.type)?.prepareRequestProvider(source, wire, body) ?: wire

    fun accountRequestUrl(source: ModelProvider, wire: ModelProvider, url: String): String =
        accountAdapters.get(source.type)?.requestUrl(source, wire, url) ?: url

    fun unwrapAccountResponse(source: ModelProvider, response: JsonObject): JsonObject =
        accountAdapters.get(source.type)?.unwrapResponse(response) ?: response

    fun clearAccountCredentials(type: ModelApiType, providerId: String) {
        accountAdapters.clearCredentials(type, providerId)
    }

    suspend fun testConnection(provider: ModelProvider): Result<String> {
        accountAdapters.get(provider.type)?.let { return it.testConnection(provider) }
        return try {
            adapters.testConnection(provider)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testModel(provider: ModelProvider, modelId: String): Result<String> {
        return try {
            require(modelId.isNotBlank()) { "Model ID is empty" }
            val modelConfig = provider.config.modelConfigs[modelId] ?: ModelConfiguration()
            require(modelConfig.modelType == ModelType.TEXT_GENERATION) {
                "${modelConfig.modelType.displayName} models cannot be tested with a text request"
            }
            val testProvider = provider.copy(config = provider.config.copy(
                supportStream = false,
                maxTokens = minOf(provider.config.maxTokens, 64),
                modelConfigs = provider.config.modelConfigs + (modelId to modelConfig.copy(supportStream = false))
            ))
            val message = StoredMessage(
                id = "connection-test",
                role = ChatRole.USER,
                versions = listOf(MessageVersion(content = "Reply with OK."))
            )
            val outputBuilder = StringBuilder()
            streamContent(testProvider, modelId, listOf(message)).collect { chunk ->
                if (outputBuilder.length < 500) {
                    outputBuilder.append(chunk.take(500 - outputBuilder.length))
                }
            }
            val output = outputBuilder.toString().trim()
            if (output.startsWith("[Error:") || output.startsWith(" [Error:")) {
                Result.failure(Exception(output.trim()))
            } else {
                Result.success(if (output.isBlank()) "Model request completed successfully" else output)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProviderModels(provider: ModelProvider): List<String> =
        fetchProviderModelCatalog(provider).map { it.id }

    suspend fun fetchProviderModelCatalog(provider: ModelProvider): List<DiscoveredModel> {
        accountAdapters.get(provider.type)?.let { return it.fetchModelCatalog(provider) }
        return adapters.modelCatalog.fetch(provider)
    }

    suspend fun fetchOllamaModels(baseUrl: String): List<String> =
        adapters.fetchOllamaModels(baseUrl)

    private fun filterAttachmentsForProvider(
        provider: ModelProvider,
        modelConfig: ModelConfiguration,
        messages: List<StoredMessage>
    ): List<StoredMessage> = messages.map { message ->
        if (message.files.isEmpty()) return@map message
        val allowedFiles = message.files.filter { reference ->
            val meta = attachments.metadata(reference) ?: return@filter false
            when {
                accountAdapters.get(provider.type) != null -> when {
                    meta.isImage -> modelConfig.supportsVision
                    else -> true
                }
                provider.type == ModelApiType.GOOGLE -> when {
                    meta.isImage -> modelConfig.supportsVision
                    meta.isVideo -> modelConfig.supportsVideoInput
                    else -> true
                }
                provider.type == ModelApiType.OPENAI || provider.type == ModelApiType.OPENAI_RESPONSES -> when {
                    meta.isImage -> modelConfig.supportsVision
                    else -> true
                }
                provider.type == ModelApiType.ANTHROPIC -> when {
                    meta.isImage -> modelConfig.supportsVision
                    meta.isPdf || meta.isText -> true
                    else -> false
                }
                provider.type == ModelApiType.OLLAMA -> meta.isImage && modelConfig.supportsVision
                else -> false
            }
        }
        if (allowedFiles == message.files) return@map message
        val versions = message.versions.toMutableList()
        if (versions.isNotEmpty()) {
            val index = message.activeVersionIndex.coerceIn(0, versions.lastIndex)
            versions[index] = versions[index].copy(files = allowedFiles)
        }
        message.copy(versions = versions)
    }

    fun streamEvents(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String = "",
        maxOutputTokens: Int? = null
    ): Flow<GenerationEvent> = flow {
        val modelConfig = provider.config.modelConfigs[modelName] ?: ModelConfiguration()
        val sendThinking = modelConfig.sendsThinkingBack(messages.hasToolCalls())
        val requestProvider = provider.copy(config = provider.config.copy(supportStream = provider.streamEnabledFor(modelName)))
        val requestMessages = filterAttachmentsForProvider(requestProvider, modelConfig, messages)
        val temperature = modelConfig.temperature
        val topP = modelConfig.topP
        val topK = modelConfig.topK
        val maxTokens = maxOutputTokens
            ?.coerceAtLeast(1)
            ?.let { minOf(it, provider.config.maxTokens) }
            ?: provider.config.maxTokens
        accountAdapters.get(provider.type)?.let { adapter ->
            emitAll(adapter.streamEvents(
                requestProvider, modelName, requestMessages, systemPrompt, maxTokens
            ))
            return@flow
        }
        val adapter = adapters.get(provider.type)
            ?: error("No request adapter is registered for ${provider.type.displayName}.")
        emitAll(adapter.stream(ModelApiRequest(
            provider = requestProvider,
            modelName = modelName,
            messages = requestMessages,
            systemPrompt = systemPrompt,
            temperature = temperature,
            topP = topP,
            topK = topK,
            maxTokens = maxTokens,
            reasoningEffort = modelConfig.wireReasoningEffort,
            sendThinkingContent = sendThinking,
            enabledModelTools = modelConfig.enabledModelTools.filter { it in modelConfig.supportedModelTools }.distinct()
        )))
    }

    suspend fun streamContent(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String = "",
        maxOutputTokens: Int? = null
    ): Flow<String> = streamEvents(provider, modelName, messages, systemPrompt, maxOutputTokens)
        .transform { event ->
            if (event is GenerationEvent.Text) emit(event.text)
        }
}
