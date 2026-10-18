package org.starfall.multigateway.home.chat.logic.tool

import org.starfall.multigateway.home.shared.logic.summarizeToolResult
import org.starfall.multigateway.home.shared.logic.ToolSummary

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import org.starfall.multigateway.integrations.media.mediaInputImageArgument
import org.starfall.multigateway.integrations.media.mediaInputImageUrlArgument
import org.starfall.multigateway.integrations.media.mediaInputImagesArgument
import org.starfall.multigateway.integrations.media.mediaToolSchema
import org.starfall.multigateway.integrations.media.resolveContentApiMedia
import org.starfall.multigateway.integrations.media.toolAttachmentReferences
import org.starfall.multigateway.integrations.mcp.McpService
import org.starfall.multigateway.integrations.mcp.McpSession
import org.starfall.multigateway.integrations.remote.RemoteMachineService
import org.starfall.multigateway.integrations.modelapis.AiService
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.REMOTE_TOOL_DISCONNECT
import org.starfall.multigateway.shared.model.REMOTE_TOOL_START_MACHINE
import org.starfall.multigateway.shared.model.REMOTE_TOOL_UPDATE_IP
import org.starfall.multigateway.shared.model.RemoteMachine
import org.starfall.multigateway.shared.model.RemoteMachineType
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.ToolActivity
import org.starfall.multigateway.shared.model.ToolDefinition
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.generation.hasToolCalls
import org.starfall.multigateway.shared.model.globalMcpToolEnabled
import org.starfall.multigateway.shared.model.isContentApiPreset
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.language.sendsThinkingBack
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.streamEnabledFor
import org.starfall.multigateway.shared.model.remoteMachineToolEnabled
import org.starfall.multigateway.shared.model.withUniqueWireNames
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.requireObject
import org.starfall.multigateway.shared.networking.str
import org.starfall.multigateway.shared.networking.text
import org.starfall.multigateway.shared.networking.toolArguments
import org.starfall.multigateway.home.chat.logic.SEND_FILE_TOOL_NAME
import org.starfall.multigateway.home.chat.logic.SendFileTool
import org.starfall.multigateway.home.chat.logic.fileDeliveryResult
import org.starfall.multigateway.home.chat.logic.sendFileDefinition
import org.starfall.multigateway.home.shared.logic.ToolExecutionService
import org.starfall.multigateway.home.shared.logic.ToolMediaGenerationService
import org.starfall.multigateway.home.shared.logic.ToolRequestService

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
class ToolChatViewModel(
    private val http: ToolHttp,
    private val mcp: McpService,
    private val aiService: AiService,
    private val remoteMachines: RemoteMachineService? = null
) {
    private val toolRequest = ToolRequestService(http, aiService)
    private val mediaGeneration = ToolMediaGenerationService(http, aiService)
    private val toolExecution = ToolExecutionService(mcp, remoteMachines)
    /** Closes persistent Remote Machine SSH connections; called when the user switches chat. */
    fun disconnectRemoteMachines() {
        toolExecution.disconnectRemoteMachines()
    }

    fun generateMedia(
        provider: ModelProvider,
        model: String,
        kind: ModelType,
        prompt: String,
        imageOptions: JsonObject = obj(),
        attachments: List<String> = emptyList(),
        videoOptions: JsonObject = obj()
    ): Flow<GenerationEvent> = mediaGeneration.generateMedia(
        provider, model, kind, prompt, imageOptions, attachments, videoOptions
    )

    suspend fun completeText(
        provider: ModelProvider,
        model: String,
        messages: List<StoredMessage>,
        prompt: String,
        maxOutputTokens: Int? = null
    ): String = toolRequest.completeText(provider, model, messages, prompt, maxOutputTokens)

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
        val configuredMachines = toolExecution.configuredMachines()
        try {
            servers
                .filter { settings().quickMcp[it.id] != false }
                .forEach { server ->
                    val activity = ToolActivity(UUID.randomUUID().toString(), "${server.name}: connect")
                    send(GenerationEvent.Tool(activity))
                    try {
                        val session = toolExecution.connect(server)
                        sessions[server.id] = session
                        tools += toolExecution.discoverTools(session).filter {
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
                toolRequest.streamEvents(provider, model, messages, prompt).collect { send(it) }
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
                val activities = if (message.role == ChatRole.MODEL) {
                    // Connection handshakes are transport state, not conversation.
                    message.activeVersion.toolActivity.filterNot { it.name.endsWith(": connect") }
                } else emptyList()
                val replay = replayToolActivities(message.content, activities, http.requireFiles())
                val attachments = if (message.files.isEmpty()) JsonArray(emptyList()) else
                    aiService.toolAttachments(message, http.requireFiles(), importedAttachments)
                val tail = replay.trailingContent + toolAttachmentReferences(attachments)
                val wireMessages = replay.messages.toMutableList()
                if (replay.messages.isEmpty() || tail.isNotBlank()) {
                    val baseMessage = obj(
                        "role" to str(if (message.role == ChatRole.MODEL) "assistant" else "user"),
                        "content" to str(tail)
                    )
                    wireMessages += if (message.files.isEmpty()) baseMessage else
                        JsonObject(baseMessage + ("_attachments" to attachments))
                }
                val reasoning = message.reasoningContent?.takeIf {
                    sendThinkingContent && message.role == ChatRole.MODEL && it.isNotBlank()
                }
                val lastAssistant = wireMessages.indexOfLast { it.text("role") == "assistant" }
                if (reasoning != null && lastAssistant >= 0) {
                    var wireMessage = JsonObject(wireMessages[lastAssistant] + ("reasoning_content" to str(reasoning)))
                    message.reasoningSignature?.takeIf { it.isNotBlank() }?.let { signature ->
                        wireMessage = JsonObject(wireMessage + ("reasoning_signature" to str(signature)))
                    }
                    wireMessages[lastAssistant] = wireMessage
                }
                history += wireMessages
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
                val turn = toolRequest.request(
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
                        wireName = name,
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
                            toolExecution.executeRemoteTool(machine, tool.originalName, args)
                        } else if (tool.serverId != null) {
                            check(
                                settings().quickMcp[tool.serverId] != false &&
                                    globalMcpToolEnabled(settings(), tool.serverId, tool.originalName)
                            ) { "MCP tool disabled" }
                            toolExecution.callMcpTool(
                                sessions[tool.serverId] ?: error("MCP session unavailable"),
                                tool.originalName, args, retainLongTextInMemory = !contentApi
                            )
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
                            mediaGeneration.executeConfiguredTool(
                                name = name,
                                provider = mediaProvider,
                                model = cfg.modelId,
                                prompt = args.text("prompt"),
                                imageOptions = cfg.imageOptions,
                                inputImage = mediaInputImageArgument(args),
                                inputImages = mediaInputImagesArgument(args),
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


}
