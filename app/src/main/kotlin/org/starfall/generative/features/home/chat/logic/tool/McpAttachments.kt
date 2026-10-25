package org.starfall.generative.features.home.chat.logic.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.starfall.generative.shared.model.ToolDefinition
import org.starfall.generative.shared.networking.obj
import org.starfall.generative.shared.networking.str
import org.starfall.generative.shared.storage.preferences.McpAttachmentMode
import org.starfall.generative.shared.storage.preferences.McpAttachmentSettings
import org.starfall.generative.shared.storage.preferences.McpDynamicStrategy

internal const val LIST_MCPS_TOOL_NAME = "list_mcps"
internal const val LOAD_MCPS_TOOL_NAME = "load_mcps"
internal val mcpDynamicToolNames = setOf(LIST_MCPS_TOOL_NAME, LOAD_MCPS_TOOL_NAME)

/** A loaded MCP is unloaded once this many user messages passed without it being loaded again or used. */
internal const val MCP_AUTO_UNLOAD_USER_TURNS = 3

/** Earlier paging tools. They are gone, but old conversations may still contain calls to them. */
internal val legacyMcpPaginationToolNames = setOf("list_mcp", "mcps_paginate", "search_mcp")

/**
 * Decides which enabled MCP tools are attached to each model request, following the "MCP Attachments" preferences.
 *
 * Classic attaches every MCP tool. Dynamic attaches nothing up front (or only the first MCPs below the minimum, with
 * the Overflow strategy) and gives the model [LIST_MCPS_TOOL_NAME] / [LOAD_MCPS_TOOL_NAME] to load the rest on demand.
 * Dynamic only switches on when the MCP count and total tool count reach the configured minimums; below them everything
 * is attached classically.
 *
 * Loaded MCPs are not stored: each generation rebuilds them by replaying the conversation (see [startUserTurn],
 * [replayLoad], [markUsed]), so they also expire correctly after a restart, retry or branch.
 */
internal class McpAttachments(private val entries: List<Entry>, private val settings: McpAttachmentSettings) {
    data class Entry(val serverId: String, val name: String, val tools: List<ToolDefinition>)

    /** MCPs attached classically while Dynamic is active: those below the minimum with the Overflow strategy. */
    private val classicEntries: List<Entry>
    /** MCPs the model has to load with [LOAD_MCPS_TOOL_NAME]. */
    private val dynamicEntries: List<Entry>

    /** True when the model gets the list/load tools. */
    val active: Boolean

    init {
        val thresholdsMet = settings.mode == McpAttachmentMode.DYNAMIC && entries.isNotEmpty() &&
            (settings.minMcps == 0 || entries.size >= settings.minMcps) &&
            (settings.minTools == 0 || entries.sumOf { it.tools.size } >= settings.minTools)
        val classicCount = if (settings.strategy == McpDynamicStrategy.OVERFLOW) {
            (settings.minMcps - 1).coerceAtLeast(0)
        } else 0
        classicEntries = if (thresholdsMet) entries.take(classicCount) else entries
        dynamicEntries = if (thresholdsMet) entries.drop(classicCount) else emptyList()
        active = dynamicEntries.isNotEmpty()
    }

    private val classicIds = classicEntries.mapTo(HashSet()) { it.serverId }

    /** Loaded MCPs, oldest first, mapped to whether their tool descriptions are attached. */
    private val loaded = LinkedHashMap<String, Boolean>()

    private val lastActiveTurn = HashMap<String, Int>()
    private var userTurn = 0

    /** Call once per user message, in order. MCPs idle for [MCP_AUTO_UNLOAD_USER_TURNS] user messages are unloaded. */
    fun startUserTurn() {
        userTurn++
        loaded.keys.removeAll { userTurn - (lastActiveTurn[it] ?: userTurn) >= MCP_AUTO_UNLOAD_USER_TURNS }
    }

    /** Using one of a loaded MCP's tools keeps it loaded. */
    fun markUsed(serverId: String) {
        if (serverId in loaded) lastActiveTurn[serverId] = userTurn
    }

    /** Re-applies a successful earlier load_mcps call while rebuilding the state from the conversation. */
    fun replayLoad(args: JsonObject) {
        runCatching { load(args) }
        lastPreview = ""
    }

    /** Short app-side summary of the last internal call, shown in the tool activity. */
    var lastPreview: String = ""
        private set

    fun includes(serverId: String): Boolean = serverId in classicIds || serverId in loaded || dynamicEntries.none { it.serverId == serverId }

    /** MCP tool descriptions are the bulk of the input, so they are only sent where the settings or the model ask for them. */
    fun present(tool: ToolDefinition): ToolDefinition {
        val serverId = tool.serverId ?: return tool
        val withDescription = if (serverId in loaded) loaded.getValue(serverId) else settings.includeToolDescription
        return if (withDescription) tool else tool.copy(description = "")
    }

    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = LIST_MCPS_TOOL_NAME,
            description = "List the MCP servers that are not attached yet, as {mcp_name, mcp_id}. " +
                "Their tools are only available after load_mcps.",
            schema = obj("type" to str("object"), "properties" to obj())
        ),
        ToolDefinition(
            name = LOAD_MCPS_TOOL_NAME,
            description = "Load MCP servers by mcp_id so their tools are added to the next requests; call them right after loading. " +
                "At most ${settings.loadLimit} MCP servers stay loaded; loading more unloads the oldest, " +
                "and a server not used for $MCP_AUTO_UNLOAD_USER_TURNS user messages is unloaded automatically. " +
                "Returns only which servers were loaded, not their tools. detailed=true also attaches the tool descriptions.",
            schema = obj(
                "type" to str("object"),
                "properties" to obj(
                    "mcp_ids" to obj(
                        "type" to str("array"),
                        "items" to obj("type" to str("string")),
                        "description" to str("The mcp_id values returned by list_mcps.")
                    ),
                    "detailed" to obj(
                        "type" to str("boolean"),
                        "description" to str("Attach tool descriptions too. Default false.")
                    )
                ),
                "required" to JsonArray(listOf(str("mcp_ids")))
            )
        )
    )

    fun execute(name: String, args: JsonObject): JsonObject = when (name) {
        LIST_MCPS_TOOL_NAME -> list()
        LOAD_MCPS_TOOL_NAME -> load(args)
        else -> error("Unknown internal tool: $name")
    }

    private fun list(): JsonObject {
        val shown = dynamicEntries.take(settings.listLimit)
        lastPreview = "Listed ${shown.size} MCP(s)."
        val mcps = JsonArray(shown.map { obj("mcp_name" to str(it.name), "mcp_id" to str(it.serverId)) })
        return if (shown.size < dynamicEntries.size) {
            obj("mcps" to mcps, "total" to JsonPrimitive(dynamicEntries.size), "truncated" to JsonPrimitive(true))
        } else {
            obj("mcps" to mcps)
        }
    }

    private fun load(args: JsonObject): JsonObject {
        val requested = requestedIds(args["mcp_ids"])
        require(requested.isNotEmpty()) { "mcp_ids is required." }
        require(requested.size <= settings.loadLimit) {
            "At most ${settings.loadLimit} MCP(s) can be loaded at once, but ${requested.size} were requested."
        }
        val detailed = args["detailed"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val resolved = mutableListOf<Entry>()
        val notFound = mutableListOf<String>()
        requested.forEach { id ->
            // Models sometimes pass the name instead of the id.
            val entry = dynamicEntries.firstOrNull { it.serverId == id }
                ?: dynamicEntries.filter { it.name.equals(id, ignoreCase = true) }.singleOrNull()
            if (entry == null) notFound += id else if (entry !in resolved) resolved += entry
        }
        require(resolved.isNotEmpty()) { "Unknown mcp_id: ${notFound.joinToString()}. Call list_mcps for the available ones." }

        resolved.forEach { entry ->
            loaded.remove(entry.serverId)
            loaded[entry.serverId] = detailed
            lastActiveTurn[entry.serverId] = userTurn
        }
        val unloaded = mutableListOf<String>()
        while (loaded.size > settings.loadLimit) {
            val oldest = loaded.keys.first()
            loaded.remove(oldest)
            dynamicEntries.firstOrNull { it.serverId == oldest }?.let { unloaded += it.name }
        }
        lastPreview = "Loaded ${resolved.joinToString { it.name }}."
        val fields = mutableListOf<Pair<String, JsonElement>>(
            "status" to str("OK"),
            "loaded" to JsonArray(resolved.map { str(it.name) })
        )
        if (unloaded.isNotEmpty()) fields += "unloaded" to JsonArray(unloaded.map { str(it) })
        if (notFound.isNotEmpty()) fields += "not_found" to JsonArray(notFound.map { str(it) })
        return obj(*fields.toTypedArray())
    }

    private fun requestedIds(value: JsonElement?): List<String> = when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> value.contentOrNull.orEmpty().split(',')
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
