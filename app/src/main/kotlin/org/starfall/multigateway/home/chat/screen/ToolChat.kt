package org.starfall.multigateway.home.chat.screen

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import org.starfall.multigateway.features.defaultmodelsandtools.shared.MAX_MEDIA_INPUT_BYTES
import org.starfall.multigateway.features.defaultmodelsandtools.shared.SystemMediaTools
import org.starfall.multigateway.features.defaultmodelsandtools.shared.isAgnesProvider
import org.starfall.multigateway.features.defaultmodelsandtools.shared.mediaInputImageArgument
import org.starfall.multigateway.features.defaultmodelsandtools.shared.mediaInputImageUrlArgument
import org.starfall.multigateway.features.defaultmodelsandtools.shared.mediaInputImagesArgument
import org.starfall.multigateway.features.defaultmodelsandtools.shared.mediaToolSchema
import org.starfall.multigateway.features.defaultmodelsandtools.shared.resolveContentApiMedia
import org.starfall.multigateway.features.defaultmodelsandtools.shared.toolAttachmentReferences
import org.starfall.multigateway.features.mcp.shared.McpService
import org.starfall.multigateway.features.mcp.shared.McpSession
import org.starfall.multigateway.features.remotemachines.shared.RemoteMachineService
import org.starfall.multigateway.features.remotemachines.shared.endpoint
import org.starfall.multigateway.integrations.modelapis.AiService
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.REMOTE_TOOL_DISCONNECT
import org.starfall.multigateway.shared.model.REMOTE_TOOL_EXECUTE
import org.starfall.multigateway.shared.model.REMOTE_TOOL_START_MACHINE
import org.starfall.multigateway.shared.model.REMOTE_TOOL_UPDATE_IP
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.RemoteMachineType
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.ToolActivity
import org.starfall.multigateway.shared.model.ToolDefinition
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.claudeAllowsSampling
import org.starfall.multigateway.shared.model.claudeThinkingParameters
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.generation.hasToolCalls
import org.starfall.multigateway.shared.model.globalMcpToolEnabled
import org.starfall.multigateway.shared.model.isContentApiPreset
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.language.googleThinkingConfig
import org.starfall.multigateway.shared.model.language.sendsThinkingBack
import org.starfall.multigateway.shared.model.language.wireReasoningEffort
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.streamEnabledFor
import org.starfall.multigateway.shared.model.remoteMachineToolEnabled
import org.starfall.multigateway.shared.model.withUniqueWireNames
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.networking.geminiFunctionParameters
import org.starfall.multigateway.shared.networking.modelResponse
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.providerBase
import org.starfall.multigateway.shared.networking.providerTurn
import org.starfall.multigateway.shared.networking.requireArray
import org.starfall.multigateway.shared.networking.requireObject
import org.starfall.multigateway.shared.networking.str
import org.starfall.multigateway.shared.networking.text
import org.starfall.multigateway.shared.networking.toolArguments

private fun sentFileNames(result: JsonObject): List<String> {
    val listed = (result["files"] as? JsonArray).orEmpty().mapNotNull { value ->
        when (value) {
            is JsonObject -> value.text("uri")
            is JsonPrimitive -> value.content
            else -> null
        }.orEmpty().removePrefix("tool-file:").takeIf { it.isNotBlank() }
    }
    val legacy = result.text("uri").removePrefix("tool-file:").takeIf { it.isNotBlank() }
    return (listed + legacy.orEmpty()).distinct()
}

/** Native structured tool calls, bounded rounds, no executable text extracted from answers. */
class ToolChat(
    private val http: ToolHttp,
    private val mcp: McpService,
    private val aiService: AiService,
    private val remoteMachines: RemoteMachineService? = null
) {
    /** Closes persistent Remote Machine SSH connections; called when the user switches chat. */
    fun disconnectRemoteMachines() {
        remoteMachines?.disconnectAllWhenIdle()
    }

    fun generateMedia(
        provider: ModelProvider,
        model: String,
        kind: ModelType,
        prompt: String,
        imageOptions: JsonObject = obj(),
        attachments: List<String> = emptyList(),
        videoOptions: JsonObject = obj()
    ): Flow<GenerationEvent> = flow {
        require(kind == ModelType.IMAGE_GENERATION || kind == ModelType.VIDEO_GENERATION)
        val name = if (kind == ModelType.IMAGE_GENERATION) "generate_image" else "generate_video"
        var activity = ToolActivity(UUID.randomUUID().toString(), name,
            arguments = obj("prompt" to str(prompt)).toString())
        emit(GenerationEvent.Tool(activity))
        try {
            require(kind != ModelType.VIDEO_GENERATION || attachments.size <= 1) {
                "Video generation accepts one reference image. Remove extra attachments."
            }
            require(attachments.size <= 16) { "Image generation accepts at most 16 reference images." }
            val maxBytes = if (kind == ModelType.IMAGE_GENERATION && provider.type.isOpenAi && !isAgnesProvider(provider)) 50L * 1024 * 1024 - 1 else MAX_MEDIA_INPUT_BYTES
            val inputs = aiService.importToolAttachments(attachments, http.requireFiles(), maxBytes)
            if (inputs.isNotEmpty()) {
                activity = activity.copy(arguments = buildJsonObject {
                    put("prompt", prompt)
                    if (kind == ModelType.VIDEO_GENERATION) put("input_image", inputs.single())
                    else put("input_images", JsonArray(inputs.map(::str)))
                }.toString())
                emit(GenerationEvent.Tool(activity))
            }
            val result = SystemMediaTools(http).generate(name, provider, model, prompt, imageOptions,
                inputImage = if (kind == ModelType.VIDEO_GENERATION) inputs.singleOrNull() else null,
                inputImages = if (kind == ModelType.IMAGE_GENERATION) inputs else emptyList(),
                videoOptions = videoOptions)
            val summary = summarizeToolResult(result, http.requireFiles())
            emit(GenerationEvent.Tool(activity.copy(status = "success", summary = summary.preview,
                files = summary.files, response = summary.content.toString())))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = obj(
                "isError" to JsonPrimitive(true),
                "status" to str("error"),
                "error" to str(e.message.orEmpty().take(500))
            )
            emit(GenerationEvent.Tool(activity.copy(
                status = "error",
                summary = e.message.orEmpty().take(500),
                response = error.toString()
            )))
            throw e
        }
    }

    suspend fun completeText(
        provider: ModelProvider,
        model: String,
        messages: List<StoredMessage>,
        prompt: String,
        maxOutputTokens: Int? = null
    ): String {
        val output = StringBuilder()
        aiService.streamContent(provider, model, messages, prompt, maxOutputTokens).collect { output.append(it) }
        return output.toString()
    }

    fun generate(
        provider: ModelProvider,
        model: String,
        messages: List<StoredMessage>,
        prompt: String,
        servers: List<McpInfo>,
        providers: List<ModelProvider>,
        settings: () -> ToolSettings,
        maxToolRounds: () -> Int? = { null },
        takePendingMessage: () -> StoredMessage? = { null }
    ): Flow<GenerationEvent> = channelFlow {
        val sessions = mutableMapOf<String, McpSession>()
        val tools = mutableListOf<ToolDefinition>()
        // Keep one catalog for a generation. The service still validates the selected machine at
        // execution time, so deleting a machine cannot redirect a call to another machine.
        val configuredMachines = remoteMachines?.state?.value?.machines.orEmpty()
        try {
            servers
                .filter { settings().quickMcp[it.id] != false }
                .forEach { server ->
                    val activity = ToolActivity(UUID.randomUUID().toString(), "${server.name}: connect")
                    send(GenerationEvent.Tool(activity))
                    try {
                        val session = mcp.session(server)
                        sessions[server.id] = session
                        tools += session.tools().filter {
                            globalMcpToolEnabled(settings(), server.id, it.originalName)
                        }
                        send(GenerationEvent.Tool(activity.copy(status = "success", summary = "Tools ready")))
                    } catch (e: CancellationException) {
                        send(GenerationEvent.Tool(activity.copy(status = "cancelled")))
                        throw e
                    } catch (e: Exception) {
                        send(GenerationEvent.Tool(activity.copy(status = "error", summary = e.message.orEmpty().take(500))))
                }
            }

            if (remoteMachines != null) {
                configuredMachines.forEach { machine ->
                    machine.supportedToolNames()
                        .filter { toolName -> remoteMachineToolEnabled(settings(), machine, toolName) }
                        .forEach { toolName ->
                        tools += ToolDefinition(
                            name = remoteToolName(machine, toolName),
                            description = when (toolName) {
                                REMOTE_TOOL_START_MACHINE ->
                                    "Start the Google Cloud Shell environment of the remote machine '${machine.name}' before using it."
                                REMOTE_TOOL_DISCONNECT ->
                                    "Close the SSH connection to the remote machine '${machine.name}'. " +
                                        "The connection opens automatically on the next execute call and closes after 5 minutes without activity, so calling this is optional."
                                REMOTE_TOOL_UPDATE_IP ->
                                    "Refresh the SSH endpoint of the Google Cloud Shell remote machine '${machine.name}'. Use only after connecting to this machine reports an error."
                                else ->
                                    "Execute a shell command on the remote machine named '${machine.name}' (${machine.type.label()}). " +
                                        "The user knows this machine by that exact name. Connects automatically and keeps the connection open for 5 minutes of inactivity. " +
                                        "Return concise commands and inspect the result."
                            },
                            schema = remoteToolSchema(toolName),
                            originalName = toolName,
                            remoteMachineId = machine.id
                        )
                    }
                }
            }

            listOf("generate_image", "generate_video").forEach { name ->
                val config = settings().system[name]
                if (config?.enabled == true) {
                    tools += ToolDefinition(
                        name = name,
                        description = if (name == "generate_image") {
                            "Generate or edit an image from a detailed prompt and optional attached/reference images. The app displays the saved image."
                        } else {
                            "Generate a video from a detailed prompt. The app displays the saved video."
                        },
                        schema = mediaToolSchema(name)
                    )
                }
            }

            if (tools.isEmpty()) {
                aiService.streamEvents(provider, model, messages, prompt).collect { send(it) }
                return@channelFlow
            }

            val uniquelyNamedTools = tools.withUniqueWireNames()
            tools.clear()
            tools += uniquelyNamedTools

            http.requireFiles()
            val history = mutableListOf<JsonObject>()
            if (provider.type != ModelApiType.GOOGLE && prompt.isNotBlank()) {
                history += obj("role" to str("system"), "content" to str(prompt))
            }
            val passbackConfig = provider.config.modelConfigs[model] ?: ModelConfiguration()
            // Auto passes earlier reasoning back only when this conversation already used tools. It also
            // switches on as soon as the current turn makes a tool call (see below).
            var sendThinkingContent = passbackConfig.sendsThinkingBack(messages.hasToolCalls())
            val importedAttachments = mutableMapOf<String, String>()
            messages.forEach { message ->
                val toolSummary = message.activeVersion.toolActivity
                    // Connection handshakes are transport state, not part of the assistant's
                    // conversational content. Including them here makes providers echo lines
                    // such as "Tool Context7: connect: success" into the answer.
                    .filter { it.status != "running" && !it.name.endsWith(": connect") }
                    .joinToString("\n", prefix = "\n") {
                        val references = it.files.filter { name -> http.requireFiles().resolve(name) != null }
                            .joinToString(", ") { name -> "tool-file:$name" }
                        "Tool ${it.name}: ${it.status}. ${it.summary.take(2000)}" +
                            if (references.isEmpty()) "" else " Reusable files: $references"
                    }
                val attachments = if (message.files.isEmpty()) JsonArray(emptyList()) else
                    aiService.toolAttachments(message, http.requireFiles(), importedAttachments)
                val baseMessage = obj(
                    "role" to str(if (message.role == ChatRole.MODEL) "assistant" else "user"),
                    "content" to str(message.content + toolSummary + toolAttachmentReferences(attachments))
                )
                var wireMessage = if (message.files.isEmpty()) baseMessage else
                    JsonObject(baseMessage + ("_attachments" to attachments))
                val reasoning = message.reasoningContent?.takeIf {
                    sendThinkingContent && message.role == ChatRole.MODEL && it.isNotBlank()
                }
                if (reasoning != null) {
                    wireMessage = JsonObject(wireMessage + ("reasoning_content" to str(reasoning)))
                    message.reasoningSignature?.takeIf { it.isNotBlank() }?.let { signature ->
                        wireMessage = JsonObject(wireMessage + ("reasoning_signature" to str(signature)))
                    }
                }
                history += wireMessage
            }

            suspend fun appendUserMessage(message: StoredMessage) {
                val attachments = if (message.files.isEmpty()) JsonArray(emptyList()) else
                    aiService.toolAttachments(message, http.requireFiles(), importedAttachments)
                history += obj(
                    "role" to str("user"),
                    "content" to str(message.content + toolAttachmentReferences(attachments))
                ).let { base ->
                    if (message.files.isEmpty()) base else JsonObject(base + ("_attachments" to attachments))
                }
            }

            val budget = ToolBudget()
            val fileSender = SendFileTool(http)
            var fileDeliveryAvailable = false
            var round = 0
            // A null limit means unlimited rounds; the loop then ends only when the model stops calling tools.
            while (maxToolRounds().let { limit -> limit == null || round < limit }) {
                round++
                currentCoroutineContext().ensureActive()
                val allowed = tools.filter { tool ->
                    if (tool.remoteMachineId != null) {
                        configuredMachines.firstOrNull { it.id == tool.remoteMachineId }?.let { machine ->
                            remoteMachineToolEnabled(settings(), machine, tool.originalName)
                        } == true
                    } else if (tool.serverId == null) {
                        if (tool.name == SEND_FILE_TOOL_NAME) fileDeliveryAvailable && settings().sendFileEnabled
                        else settings().system[tool.name]?.enabled == true
                    } else {
                        settings().quickMcp[tool.serverId] != false &&
                            globalMcpToolEnabled(settings(), tool.serverId, tool.originalName)
                    }
                }

                val streaming = provider.streamEnabledFor(model)
                val turn = request(
                    provider,
                    model,
                    history,
                    allowed,
                    prompt,
                    sendThinking = sendThinkingContent,
                    onText = { send(GenerationEvent.Text(it)) },
                    onReasoning = { send(GenerationEvent.Reasoning(it)) }
                )
                val reasoningSignature = turn.text("reasoning_signature").takeIf { it.isNotBlank() }
                if (!streaming) {
                    turn.text("reasoning_content").takeIf { it.isNotBlank() }?.let {
                        send(GenerationEvent.Reasoning(it, reasoningSignature))
                    }
                    turn.text("content").takeIf { it.isNotBlank() }?.let {
                        send(GenerationEvent.Text(it))
                    }
                } else if (reasoningSignature != null) {
                    send(GenerationEvent.Reasoning("", reasoningSignature))
                }

                val calls = turn["tool_calls"] as? JsonArray ?: JsonArray(emptyList())
                if (calls.isEmpty()) return@channelFlow
                check(calls.size <= 16) { "Too many tool calls in a single response" }
                // A turn that calls tools must keep its reasoning chain on the following requests
                // (Auto and On); Off never sends it.
                if (passbackConfig.sendsThinkingBack(hasToolCalls = true)) sendThinkingContent = true
                history += turn

                calls.forEach { value ->
                    val call = value.jsonObject
                    val function = call.requireObject("function")
                    val name = function.text("name")
                    val tool = allowed.find { it.name == name }
                    val rawArguments = when (val raw = function["arguments"]) {
                        null, JsonNull -> ""
                        is JsonPrimitive -> raw.content
                        else -> raw.toString()
                    }
                    val activity = ToolActivity(
                        id = UUID.randomUUID().toString(),
                        name = tool?.let { definition ->
                            servers.find { it.id == definition.serverId }
                                ?.let { "${it.name} / ${definition.originalName}" }
                                ?: configuredMachines.find { it.id == definition.remoteMachineId }
                                    ?.let { "${it.name} / ${definition.originalName}" }
                                ?: definition.originalName
                        } ?: name,
                        arguments = rawArguments
                    )
                    val contentApi = tool?.serverId?.let { serverId ->
                        servers.find { it.id == serverId }?.isContentApiPreset()
                    } == true
                    val mediaTool = tool != null && tool.serverId == null && tool.remoteMachineId == null &&
                        name in setOf("generate_image", "generate_video")
                    send(GenerationEvent.Tool(activity))

                    var result: JsonObject
                    try {
                        require(tool != null) { "Tool is not enabled" }
                        budget.consume(if (tool.serverId == null) name else null)
                        val args = toolArguments(function["arguments"])
                        result = if (tool.remoteMachineId != null) {
                            val machine = configuredMachines.firstOrNull { it.id == tool.remoteMachineId }
                                ?: error("Remote Machine is no longer configured")
                            check(remoteMachineToolEnabled(settings(), machine, tool.originalName)) {
                                "Remote Machine or tool disabled"
                            }
                            remoteMachines?.executeTool(machine, tool.originalName, args)
                                ?: error("Remote Machine service is unavailable")
                        } else if (tool.serverId != null) {
                            check(
                                settings().quickMcp[tool.serverId] != false &&
                                    globalMcpToolEnabled(settings(), tool.serverId, tool.originalName)
                            ) { "MCP tool disabled" }
                            (sessions[tool.serverId] ?: error("MCP session unavailable"))
                                .call(tool.originalName, args, retainLongTextInMemory = !contentApi)
                        } else if (name == SEND_FILE_TOOL_NAME) {
                            check(settings().sendFileEnabled) { "Send File tool disabled" }
                            fileSender.execute(args)
                        } else {
                            val cfg = settings().system[name] ?: error("System tool is not configured")
                            check(cfg.enabled) { "System tool disabled" }
                            val mediaProvider = providers.find { it.id == cfg.providerId }
                                ?: error("Select a provider for this system tool")
                            val kind = if (name == "generate_image") {
                                ModelType.IMAGE_GENERATION
                            } else {
                                ModelType.VIDEO_GENERATION
                            }
                            check(
                                mediaProvider.config.modelConfigs[cfg.modelId]?.modelType == kind &&
                                    mediaProvider.config.modelIds?.contains(cfg.modelId) != false
                            ) { "Select an available media model in System tools" }
                            SystemMediaTools(http).generate(
                                name,
                                mediaProvider,
                                cfg.modelId,
                                args.text("prompt"),
                                cfg.imageOptions,
                                mediaInputImageArgument(args),
                                mediaInputImagesArgument(args),
                                videoOptions = cfg.videoOptions,
                                inputImageUrl = mediaInputImageUrlArgument(args)
                            )
                        }

                        val isError = (result["isError"] as? JsonPrimitive)?.booleanOrNull == true
                        val contentApiMedia = if (contentApi) resolveContentApiMedia(result, http) else null
                        if (contentApiMedia != null) result = contentApiMedia.content
                        val summary = if (contentApi || mediaTool) {
                            summarizeToolResult(result, http.requireFiles())
                        } else {
                            summarizeGenericToolResult(result)
                        }
                        // The model may only get a bounded view of the result, but the UI keeps the complete response.
                        val completeResponse = result.toString()
                        result = summary.content
                        if (tool.serverId != null && !contentApi) {
                            val availableFiles = (summary.files + contentApiMedia?.files.orEmpty()).distinct()
                            result = fileDeliveryResult(result, availableFiles, settings().sendFileEnabled)
                        }
                        send(
                            GenerationEvent.Tool(
                                activity.copy(
                                    status = if (isError) "error" else "success",
                                    summary = when {
                                        name == SEND_FILE_TOOL_NAME && tool.serverId == null -> "File sent to chat."
                                        contentApiMedia?.files?.isNotEmpty() == true -> "Displayed ${contentApiMedia.files.size} media item(s) in chat."
                                        else -> summary.preview
                                    },
                                    files = when {
                                        contentApiMedia?.files?.isNotEmpty() == true -> contentApiMedia.files
                                         tool.serverId != null -> summary.files.distinct()
                                        name == SEND_FILE_TOOL_NAME -> sentFileNames(result)
                                        else -> summary.files
                                    },
                                    inlineMedia = contentApiMedia?.files?.isNotEmpty() == true ||
                                        tool.serverId == null && name == SEND_FILE_TOOL_NAME,
                                    responsePreviewLimit = if (contentApi || mediaTool) 0 else 6_000,
                                    response = completeResponse
                                )
                            )
                        )
                    } catch (e: CancellationException) {
                        send(GenerationEvent.Tool(activity.copy(status = "cancelled", summary = "Stopped")))
                        throw e
                    } catch (e: Exception) {
                        result = obj(
                            "isError" to JsonPrimitive(true),
                            "status" to str("error"),
                            "error" to str(e.message.orEmpty().take(500))
                        )
                        send(
                            GenerationEvent.Tool(
                                activity.copy(
                                    status = "error",
                                    summary = e.message.orEmpty().take(500),
                                    response = result.toString()
                                )
                            )
                        )
                    }

                    val contentApiTool = tool?.serverId?.let { serverId ->
                        servers.find { it.id == serverId }?.isContentApiPreset()
                    } == true
                    if (tool?.serverId != null && !contentApiTool) {
                        if (!fileDeliveryAvailable) tools += sendFileDefinition
                        fileDeliveryAvailable = true
                    }
                    history += obj(
                        "role" to str("tool"),
                        "tool_call_id" to str(call.text("id")),
                        "name" to str(name),
                        "content" to str(result.toString())
                    )
                }

                // A pending user message belongs after the complete tool response(s),
                // before the next model request. This lets the model react to the new
                // instruction without waiting for the current tool loop to finish.
                val canStartAnotherRound = maxToolRounds().let { limit ->
                    limit == null || round < limit
                }
                if (canStartAnotherRound) {
                    takePendingMessage()?.let { pending ->
                        appendUserMessage(pending)
                        send(GenerationEvent.UserMessage(pending.copy(isQueued = false)))
                    }
                }
            }
            error("Stopped after $round tool rounds. Send another message to continue.")
        } finally {
            sessions.values.forEach { it.close() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Tool names are visible to the model, so they use the machine name the user sees in the app
     * rather than its internal id, which stays in [ToolDefinition.remoteMachineId] for dispatch only.
     * Same-named machines are told apart later by withUniqueWireNames (name_2, name_3...).
     */
    private fun remoteToolName(machine: RemoteMachine, tool: String): String {
        val readable = java.text.Normalizer.normalize(machine.name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')
            .replace(Regex("[^A-Za-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "unnamed" }
        val prefix = "machine_"
        val suffix = "_$tool"
        return prefix + readable.take((64 - prefix.length - suffix.length).coerceAtLeast(1)) + suffix
    }

    private fun RemoteMachineType.label(): String = when (this) {
        RemoteMachineType.SSH -> "SSH"
        RemoteMachineType.GCLOUD_SHELL -> "Google Cloud Shell"
    }

    private fun remoteToolSchema(tool: String): JsonObject = when (tool) {
        REMOTE_TOOL_START_MACHINE, REMOTE_TOOL_UPDATE_IP, REMOTE_TOOL_DISCONNECT -> buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }
        else -> buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "The shell command to run on the remote machine")
                })
                put("timeout_seconds", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional timeout in seconds, from 1 to 600")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("command"))))
        }
    }

    private suspend fun request(
        p: ModelProvider,
        model: String,
        history: List<JsonObject>,
        tools: List<ToolDefinition>,
        prompt: String,
        sendThinking: Boolean,
        onText: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit
    ): JsonObject {
        val defs = JsonArray(tools.map { obj("type" to str("function"),"function" to obj("name" to str(it.name),"description" to str(it.description),"parameters" to it.schema)) })
        val wireProvider = aiService.prepareAccountProvider(p, model)
        val base = providerBase(wireProvider)
        val config = p.config.modelConfigs[model] ?: ModelConfiguration()
        suspend fun modelResponse(url: String, body: JsonObject, provider: ModelProvider,
            stream: Boolean, text: suspend (String) -> Unit, reasoning: suspend (String) -> Unit = {}): JsonObject {
            val requestBody = if (p.type == ModelApiType.ANTIGRAVITY) JsonObject(body + ("model" to str(model))) else body
            return http.modelResponse(aiService.accountRequestUrl(p, provider, url),
                aiService.normalizeAccountToolRequest(p, provider, requestBody, prompt), aiService.prepareAccountRequest(p, provider, requestBody), stream, text, reasoning,
                { aiService.unwrapAccountResponse(p, it) })
        }

        return when(wireProvider.type) {
            ModelApiType.OPENAI_RESPONSES -> {
                val sendReasoning = sendThinking
                val isDeepSeek = wireProvider.baseUrl.contains("deepseek.com", ignoreCase = true) || model.startsWith("deepseek-", ignoreCase = true)
                val input = history.flatMap { message ->
                    when {
                        message["responsesOutput"] is JsonArray -> {
                            val items = message.requireArray("responsesOutput").toList()
                            if (sendReasoning) items else items.filterNot { it.requireObject().text("type") == "reasoning" }
                        }
                        message.text("role") == "tool" -> listOf(obj(
                            "type" to str("function_call_output"), "call_id" to str(message.text("tool_call_id")),
                            "output" to str(message.text("content"))
                        ))
                        message.text("role") == "assistant" -> buildList {
                            message.text("reasoning_content").takeIf { sendReasoning && it.isNotBlank() }?.let { reasoning ->
                                add(obj(
                                    "type" to str("reasoning"),
                                    "content" to JsonArray(listOf(obj("type" to str("reasoning_text"), "text" to str(reasoning))))
                                ))
                            }
                            add(obj("role" to str("assistant"), "content" to str(message.text("content"))))
                        }
                        else -> listOf(JsonObject(message.filterKeys {
                            it != "reasoning_content" && it != "reasoning_signature" && it != "_attachments"
                        } + ("content" to toolMessageContent(message, ModelApiType.OPENAI_RESPONSES))))
                    }
                }
                val requestBody = buildJsonObject {
                    put("model", model); put("input", JsonArray(input)); put("store", false)
                    if (!isDeepSeek) put("include", JsonArray(listOf(str("reasoning.encrypted_content"))))
                    put("max_output_tokens", p.config.maxTokens)
                    config.temperature?.let { put("temperature", it) }
                    config.topP?.let { put("top_p", it) }
                    config.wireReasoningEffort?.trim()?.takeIf { it.isNotEmpty() }?.let { effort ->
                        put("reasoning", buildJsonObject { put("effort", effort) })
                    }
                    if (tools.isNotEmpty()) put("tools", JsonArray(tools.map {
                        obj("type" to str("function"), "name" to str(it.name), "description" to str(it.description),
                            "parameters" to it.schema, "strict" to JsonPrimitive(false))
                    }))
                }
                val response = modelResponse(
                    "$base/responses",
                    requestBody,
                    wireProvider,
                    p.streamEnabledFor(model),
                    onText,
                    onReasoning
                )
                providerTurn(wireProvider.type, response)
            }
            ModelApiType.OPENAI, ModelApiType.OLLAMA -> {
                val ollama = wireProvider.type == ModelApiType.OLLAMA
                val sendReasoning = sendThinking && !ollama
                val wireHistory = history.map { message ->
                    var clean = JsonObject(message.filterKeys { key ->
                        key != "_attachments" && key != "responsesOutput" && key != "reasoning_signature" && (sendReasoning || key != "reasoning_content")
                    } + ("content" to toolMessageContent(message, wireProvider.type)))
                    if (ollama && message["_attachments"] is JsonArray) {
                        val images = message.getValue("_attachments").jsonArray.map {
                            val file = it.jsonObject
                            require(file.text("mimeType").startsWith("image/")) { "Unsupported Ollama attachment" }
                            file.getValue("data")
                        }
                        clean = JsonObject(clean + ("images" to JsonArray(images)))
                    }
                    when (message.text("role")) {
                        "tool" -> if (ollama) JsonObject(clean + ("tool_name" to str(message.text("name")))) else clean
                        "assistant" -> {
                            val calls = (message["tool_calls"] as? JsonArray).orEmpty().map { c ->
                                val call = c.requireObject()
                                val f = call.requireObject("function")
                                val rawArgs = f["arguments"]
                                val args = if (ollama) {
                                    rawArgs as? JsonObject ?: Json.parseToJsonElement(f.text("arguments"))
                                } else {
                                    str(if (rawArgs is JsonPrimitive) rawArgs.content else rawArgs?.toString() ?: "{}")
                                }
                                if (ollama) obj("function" to JsonObject(f + ("arguments" to args)))
                                else obj(
                                    "id" to str(call.text("id")),
                                    "type" to str("function"),
                                    "function" to JsonObject(f + ("arguments" to args))
                                )
                            }
                            JsonObject(clean + ("tool_calls" to JsonArray(calls)))
                        }
                        else -> clean
                    }
                }
                val request = buildJsonObject {
                    put("model", model); put("messages", JsonArray(wireHistory)); put("stream", false)
                    if (tools.isNotEmpty()) put("tools", defs)
                    if (ollama) put("options", buildJsonObject {
                        config.temperature?.let { put("temperature", it) }
                        config.topP?.let { put("top_p", it) }
                        config.topK?.let { put("top_k", it) }
                        put("num_predict", p.config.maxTokens)
                    }) else {
                        put("max_tokens", p.config.maxTokens)
                        config.temperature?.let { put("temperature", it) }
                        config.topP?.let { put("top_p", it) }
                        config.wireReasoningEffort?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            put("reasoning_effort", it)
                        }
                    }
                }
                val response = modelResponse(
                    if (ollama) base.removeSuffix("/api") + "/api/chat" else "$base/chat/completions",
                    request, wireProvider, p.streamEnabledFor(model), onText, onReasoning
                )
                providerTurn(wireProvider.type, response)
            }
            ModelApiType.ANTHROPIC -> {
                val native = mutableListOf<JsonObject>()
                history.filter { it.text("role") != "system" }.forEach { message ->
                    val role = message.text("role")
                    val blocks = mutableListOf<JsonElement>()
                    if (role == "tool") {
                        blocks += obj(
                            "type" to str("tool_result"),
                            "tool_use_id" to str(message.text("tool_call_id")),
                            "content" to str(message.text("content"))
                        )
                    } else {
                        if (role == "assistant" && sendThinking) {
                            message.text("reasoning_content").takeIf { it.isNotBlank() }?.let { reasoning ->
                                val signature = message.text("reasoning_signature")
                                blocks += buildJsonObject {
                                    put("type", "thinking")
                                    put("thinking", reasoning)
                                    if (signature.isNotBlank()) put("signature", signature)
                                }
                            }
                        }
                        blocks += toolAttachmentParts(message, ModelApiType.ANTHROPIC)
                        if (message.text("content").isNotEmpty()) {
                            blocks += obj("type" to str("text"), "text" to str(message.text("content")))
                        }
                        (message["tool_calls"] as? JsonArray).orEmpty().forEach { c ->
                            val call = c.jsonObject
                            val f = call.requireObject("function")
                            blocks += obj(
                                "type" to str("tool_use"),
                                "id" to str(call.text("id")),
                                "name" to str(f.text("name")),
                                "input" to (f["arguments"] as? JsonObject ?: Json.parseToJsonElement(f.text("arguments")))
                            )
                        }
                    }
                    val targetRole = if (role == "assistant") "assistant" else "user"
                    if (native.lastOrNull()?.text("role") == targetRole) {
                        val last = native.removeAt(native.lastIndex)
                        native += obj("role" to str(targetRole), "content" to JsonArray(last.requireArray("content") + blocks))
                    } else {
                        native += obj("role" to str(targetRole), "content" to JsonArray(blocks))
                    }
                }
                val response = modelResponse(base.removeSuffix("/v1") + "/v1/messages", buildJsonObject {
                    put("model", model); put("max_tokens", p.config.maxTokens); put("system", prompt); put("messages", JsonArray(native))
                    config.claudeThinkingParameters(model, p.config.maxTokens).forEach { (key, value) -> put(key, value) }
                    if (tools.isNotEmpty()) put("tools", JsonArray(tools.map {
                        obj("name" to str(it.name), "description" to str(it.description), "input_schema" to it.schema)
                    }))
                    if (config.claudeAllowsSampling(model, p.config.maxTokens)) {
                        config.temperature?.let { put("temperature", it) }
                        config.topP?.let { put("top_p", it) }
                        config.topK?.let { put("top_k", it) }
                    }
                }, wireProvider, p.streamEnabledFor(model), onText, onReasoning)
                providerTurn(wireProvider.type, response)
            }
            ModelApiType.GOOGLE -> {
                val native=history.map { message ->
                    val role=message.text("role"); val parts=mutableListOf<JsonElement>()
                    if(role=="tool") parts += obj("functionResponse" to obj("name" to str(message.text("name")),"response" to obj("result" to str(message.text("content")))))
                    else {
                        parts += toolAttachmentParts(message, ModelApiType.GOOGLE)
                        if(message.text("content").isNotBlank()) parts += obj("text" to str(message.text("content")))
                        (message["tool_calls"] as? JsonArray).orEmpty().forEach { c -> val f=c.requireObject().requireObject("function")
                            parts += (c.jsonObject["googlePart"] ?: obj("functionCall" to obj("name" to str(f.text("name")),"args" to (f["arguments"] as? JsonObject ?: Json.parseToJsonElement(f.text("arguments")))))) }
                    }
                    obj("role" to str(if(role=="assistant") "model" else "user"),"parts" to JsonArray(parts))
                }
                val root=if(Regex("/v1(?:beta|alpha)?$").containsMatchIn(base)) base else "$base/v1beta"
                val response=modelResponse("$root/models/$model:generateContent",buildJsonObject {
                    put("contents",JsonArray(native)); put("systemInstruction",obj("parts" to JsonArray(listOf(obj("text" to str(prompt))))))
                    if(tools.isNotEmpty()) put("tools",JsonArray(listOf(obj("functionDeclarations" to JsonArray(tools.map { tool -> buildJsonObject { put("name", str(tool.name)); put("description", str(tool.description)); geminiFunctionParameters(tool.schema)?.let { put("parameters", it) } } })))))
                    put("generationConfig",buildJsonObject { put("maxOutputTokens",p.config.maxTokens); config.temperature?.let { put("temperature",it) }; config.topP?.let { put("topP",it) }; config.topK?.let { put("topK",it) }; config.googleThinkingConfig()?.let { put("thinkingConfig", it) } })
                },wireProvider,p.streamEnabledFor(model),onText,onReasoning)
                providerTurn(wireProvider.type, response)
            }
            else -> error("Unsupported provider protocol: ${wireProvider.type.displayName}")
        }
    }
}
