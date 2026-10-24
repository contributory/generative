package org.starfall.generative.integrations.modelapis

import android.content.Context
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.starfall.generative.integrations.modelapis.account.AccountProviderAdapterRegistry
import org.starfall.generative.integrations.modelapis.sdk.OfficialProviderSdk
import org.starfall.generative.shared.auth.OAuthCallbackService
import org.starfall.generative.shared.files.AttachmentResolver
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.logging.DebugLog
import org.starfall.generative.shared.logging.DebugLogKtorPlugin
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.withoutEmptyModelMessages
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.auth.CredentialLoadState
import org.starfall.generative.shared.model.generation.GenerationEvent
import org.starfall.generative.shared.model.generation.hasToolCalls
import org.starfall.generative.shared.model.language.DiscoveredModel
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.language.sendsThinkingBack
import org.starfall.generative.shared.model.language.wireReasoningEffort
import org.starfall.generative.shared.model.logging.DebugLogCategory
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.streamEnabledFor
import org.starfall.generative.shared.networking.requireNetworkCredentials
import org.starfall.generative.shared.storage.preferences.ModelConfigurationMemory
import java.util.Base64

class AiService(
    context: Context,
    var providerLoader: suspend (ModelProvider) -> ModelProvider = { it }
) {
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
        install(DebugLogKtorPlugin)
    }

    private val adapters = ModelApiAdapterRegistry(context, sdk, httpClient, attachments, json)

    /**
     * Load only NOT_REQUESTED records; the loader must retain the caller's public configuration.
     * Already-ready records bypass the loader, and incomplete results fail before any adapter runs.
     */
    suspend fun resolveProvider(provider: ModelProvider): ModelProvider {
        val resolved = if (provider.credentialState == CredentialLoadState.NOT_REQUESTED) providerLoader(provider) else provider
        check(resolved.id == provider.id) { "Credential loader returned a different provider." }
        return resolved.requireNetworkCredentials()
    }

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
                require(meta.sizeBytes <= maxBytes) { "Reference file is too large for the selected generation API." }
                val input = attachments.open(reference) ?: error("Attachment data is missing")
                "tool-file:" + files.save(input, meta.mimeType, maxBytes)
            }
        }

    suspend fun authorizeProvider(provider: ModelProvider): Result<ModelProvider> = try {
        val resolved = resolveProvider(provider)
        accountAdapters.get(resolved.type)?.authorize(resolved)
            ?: Result.failure(IllegalArgumentException("${resolved.type.displayName} does not use account authorization."))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun prepareAccountProvider(provider: ModelProvider, modelName: String? = null): ModelProvider {
        val resolved = resolveProvider(provider)
        return accountAdapters.get(resolved.type)?.let { adapter ->
            if (modelName == null) adapter.prepareAuthenticatedProvider(resolved) else adapter.prepareModelProvider(resolved, modelName)
        }
            ?: resolved
    }

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
        return try {
            val resolved = resolveProvider(provider)
            accountAdapters.get(resolved.type)?.let { return it.testConnection(resolved) }
            adapters.testConnection(resolved)
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
                // Keep a blank reply blank: the editor reports an empty response as a warning instead of claiming success.
                Result.success(output)
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
        val resolved = resolveProvider(provider)
        accountAdapters.get(resolved.type)?.let { return it.fetchModelCatalog(resolved) }
        return adapters.modelCatalog.fetch(resolved)
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
        val resolved = resolveProvider(provider)
        val modelConfig = resolved.config.modelConfigs[modelName] ?: ModelConfiguration()
        val sendThinking = modelConfig.sendsThinkingBack(messages.hasToolCalls())
        val requestProvider = resolved.copy(config = resolved.config.copy(supportStream = resolved.streamEnabledFor(modelName)))
        val requestMessages = filterAttachmentsForProvider(requestProvider, modelConfig, messages.withoutEmptyModelMessages())
        val temperature = modelConfig.temperature
        val topP = modelConfig.topP
        val topK = modelConfig.topK
        val maxTokens = maxOutputTokens
            ?.coerceAtLeast(1)
            ?: resolved.config.maxTokens
        logModelRequest(requestProvider, modelName, requestMessages, systemPrompt, maxTokens, temperature, topP, topK)
        accountAdapters.get(resolved.type)?.let { adapter ->
            emitAll(adapter.streamEvents(
                requestProvider, modelName, requestMessages, systemPrompt, maxTokens
            ))
            return@flow
        }
        val adapter = adapters.get(resolved.type)
            ?: error("No request adapter is registered for ${resolved.type.displayName}.")
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
    }.catch { error ->
        DebugLog.error(DebugLogCategory.ERRORS, "Model", "Request to ${provider.name} / $modelName failed", error)
        throw error
    }

    /** Official SDK transports cannot be intercepted, so the model request is logged here for every provider. */
    private fun logModelRequest(
        provider: ModelProvider,
        modelName: String,
        messages: List<StoredMessage>,
        systemPrompt: String,
        maxTokens: Int,
        temperature: Double?,
        topP: Double?,
        topK: Int?
    ) {
        DebugLog.log(DebugLogCategory.NETWORK, "Model") {
            "→ ${provider.type.displayName} · ${provider.name} · $modelName " +
                "(stream=${provider.config.supportStream}, ${messages.size} messages)"
        }
        DebugLog.log(DebugLogCategory.PAYLOAD, "Model") {
            buildString {
                append("model=").append(modelName).append(" maxTokens=").append(maxTokens)
                append(" temperature=").append(temperature).append(" topP=").append(topP).append(" topK=").append(topK).append('\n')
                if (systemPrompt.isNotBlank()) append("[system] ").append(systemPrompt.take(600)).append('\n')
                messages.forEach { message ->
                    append('[').append(message.role.name.lowercase()).append("] ").append(message.content.take(600))
                    if (message.files.isNotEmpty()) append(" (+").append(message.files.size).append(" files)")
                    append('\n')
                }
            }
        }
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
