package org.starfall.multigateway.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.language.ModelType

@Serializable
data class McpAccess(val enabled: Boolean = false, val tools: Map<String, Boolean> = emptyMap())

@Serializable
data class SystemToolConfig(
    val enabled: Boolean = false,
    val providerId: String = "",
    val modelId: String = "",
    val prompt: String = "",
    val imageOptionsByModel: Map<String, JsonObject> = emptyMap(),
    val videoOptionsByModel: Map<String, JsonObject> = emptyMap(),
    /** Optional media-tool parameters hidden from the model and ignored if it passes them anyway. */
    val disabledParams: Set<String> = emptySet()
) {
    val imageOptions: JsonObject
        get() = imageOptionsByModel["$providerId/$modelId"] ?: JsonObject(emptyMap())

    fun withImageOptions(options: JsonObject) =
        copy(imageOptionsByModel = imageOptionsByModel + ("$providerId/$modelId" to options))

    val videoOptions: JsonObject
        get() = videoOptionsByModel["$providerId/$modelId"] ?: JsonObject(emptyMap())

    fun withVideoOptions(options: JsonObject) =
        copy(videoOptionsByModel = videoOptionsByModel + ("$providerId/$modelId" to options))
}

@Serializable
data class ToolSettings(
    val system: Map<String, SystemToolConfig> = emptyMap(),
    val quickMcp: Map<String, Boolean> = emptyMap(),
    val mcpTools: Map<String, Map<String, Boolean>> = emptyMap(),
    /** Per-server, per-tool parameter visibility and optional default values. */
    val mcpToolParameters: Map<String, Map<String, Map<String, McpToolParameterConfig>>> = emptyMap(),
    /** Master switch for Remote Machine tools in the chat tool picker. */
    val machinesEnabled: Boolean = false,
    /** Machine-level switches shown in Tools Manage. Entries override the legacy master switch. */
    val enabledMachines: Map<String, Boolean> = emptyMap(),
    /** Legacy per-machine tool switches retained for decoding older settings. */
    val machineTools: Map<String, Map<String, Boolean>> = emptyMap(),
    /** Lets the AI display files in chat through send_file. On unless the user turns it off. */
    val sendFileEnabled: Boolean = true
)

@Serializable
data class ToolActivity(
    val id: String,
    val name: String,
    /** Function name the model used for this call; [name] is the display name. */
    val wireName: String = "",
    val status: String = "running",
    val summary: String = "",
    val files: List<String> = emptyList(),
    val arguments: String = "",
    val response: String = "",
    val contentOffset: Int = 0,
    val reasoningOffset: Int? = null,
    val inlineMedia: Boolean = false,
    /** Non-zero marks a generic MCP tool response (not media / Content-API). It does not limit [response]. */
    val responsePreviewLimit: Int = 0
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val schema: JsonObject,
    val serverId: String? = null,
    val originalName: String = name,
    /** A tool owned by a Remote Machine, mutually exclusive with [serverId]. */
    val remoteMachineId: String? = null
)


@Serializable
data class McpToolParameterConfig(
    val enabled: Boolean = true,
    /** Stored as text in the editor and converted to the parameter's schema type when called. */
    val defaultValue: String = ""
)

fun configuredMcpTool(tool: ToolDefinition, settings: ToolSettings): ToolDefinition {
    val serverId = tool.serverId ?: return tool
    val overrides = settings.mcpToolParameters[serverId]?.get(tool.originalName).orEmpty()
    if (overrides.isEmpty()) return tool
    val properties = tool.schema["properties"] as? JsonObject ?: return tool
    val required = (tool.schema["required"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    val visibleProperties = buildJsonObject {
        properties.forEach { (name, schema) ->
            val config = overrides[name]
            if (config?.enabled != false) put(name, schema)
        }
    }
    val visibleRequired = required.filter { name -> overrides[name]?.enabled != false }
    val schema = buildJsonObject {
        tool.schema.forEach { (key, value) ->
            when (key) {
                "properties" -> put(key, visibleProperties)
                "required" -> if (visibleRequired.isNotEmpty()) put(key, JsonArray(visibleRequired.map(::JsonPrimitive)))
                else -> put(key, value)
            }
        }
        if ("properties" !in tool.schema) put("properties", visibleProperties)
    }
    return tool.copy(schema = schema)
}

fun configuredMcpArguments(tool: ToolDefinition, arguments: JsonObject, settings: ToolSettings): JsonObject {
    val serverId = tool.serverId ?: return arguments
    val overrides = settings.mcpToolParameters[serverId]?.get(tool.originalName).orEmpty()
    if (overrides.isEmpty()) return arguments
    val properties = tool.schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
    return buildJsonObject {
        arguments.forEach { (name, value) ->
            val config = overrides[name]
            if (config?.enabled != false) put(name, value)
        }
        overrides.forEach { (name, config) ->
            if (config.defaultValue.isBlank() || arguments.containsKey(name)) return@forEach
            val type = (properties[name] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
            val value = runCatching {
                when (type) {
                    "integer" -> JsonPrimitive(config.defaultValue.trim().toLong())
                    "number" -> JsonPrimitive(config.defaultValue.trim().toDouble())
                    "boolean" -> JsonPrimitive(config.defaultValue.trim().toBooleanStrict())
                    "object", "array" -> Json.parseToJsonElement(config.defaultValue)
                    else -> JsonPrimitive(config.defaultValue)
                }
            }.getOrElse { JsonPrimitive(config.defaultValue) }
            put(name, value)
        }
    }
}

/**
 * Server and machine names are user-controlled, so two entries can still produce the same readable
 * wire name after sanitization. Disambiguate only those collisions with a plain ordinal
 * (name, name_2, name_3...); the normal name stays untouched. Internal ids are never used here
 * because wire names are shown to the model.
 */
internal fun List<ToolDefinition>.withUniqueWireNames(): List<ToolDefinition> {
    val used = mutableSetOf<String>()
    return map { definition ->
        var name = definition.name
        var ordinal = 1
        while (!used.add(name)) {
            ordinal++
            val suffix = "_$ordinal"
            name = definition.name.take(64 - suffix.length) + suffix
        }
        if (name == definition.name) definition else definition.copy(name = name)
    }
}

fun systemMediaToolAvailable(
    name: String,
    config: SystemToolConfig,
    providers: List<ModelProvider>
): Boolean {
    if (config.providerId.isBlank() || config.modelId.isBlank()) return false
    val requiredType = when (name) {
        "generate_image" -> ModelType.IMAGE_GENERATION
        "generate_video" -> ModelType.VIDEO_GENERATION
        else -> return false
    }
    val provider = providers.find { it.id == config.providerId } ?: return false
    if (!provider.type.isOpenAi && provider.type != ModelApiType.GOOGLE) return false
    val model = provider.config.modelConfigs[config.modelId] ?: return false
    return model.modelType == requiredType && provider.config.modelIds?.contains(config.modelId) != false
}

fun globalMcpToolEnabled(settings: ToolSettings, serverId: String, name: String): Boolean =
    settings.mcpTools[serverId]?.get(name) != false

fun remoteMachineEnabled(settings: ToolSettings, machine: org.starfall.multigateway.shared.model.RemoteMachine): Boolean =
    if (settings.enabledMachines.isNotEmpty()) {
        settings.enabledMachines[machine.id] == true
    } else {
        settings.machinesEnabled && machine.aiToolsEnabled
    }

fun remoteMachineToolEnabled(
    settings: ToolSettings,
    machine: org.starfall.multigateway.shared.model.RemoteMachine,
    toolName: String
): Boolean = remoteMachineEnabled(settings, machine) &&
    machine.isToolEnabled(toolName)

fun toolAllowed(
    access: McpAccess?,
    quick: Boolean?,
    globalEnabled: Boolean,
    name: String
): Boolean =
    access?.enabled == true && quick != false && globalEnabled && (access.tools[name] != false)

const val DEFAULT_TITLE_GENERATION_PROMPT = "Generate a concise title for this conversation. Return only the title, without quotation marks or extra commentary. Keep it under 8 words."
const val DEFAULT_CHAT_SUMMARY_PROMPT = "Summarize the conversation faithfully for use as future context. Preserve user goals, decisions, constraints, important facts, code or technical details, unresolved issues, and commitments. Remove repetition and incidental chatter. Do not invent information."
