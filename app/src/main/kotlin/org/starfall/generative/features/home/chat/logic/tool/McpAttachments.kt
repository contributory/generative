package org.starfall.generative.features.home.chat.logic.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.starfall.generative.shared.model.ToolDefinition
import org.starfall.generative.shared.networking.obj
import org.starfall.generative.shared.networking.str

internal const val LIST_MCP_TOOL_NAME = "list_mcp"
internal const val MCPS_PAGINATE_TOOL_NAME = "mcps_paginate"
internal const val SEARCH_MCP_TOOL_NAME = "search_mcp"
internal val mcpPaginationToolNames = setOf(LIST_MCP_TOOL_NAME, MCPS_PAGINATE_TOOL_NAME, SEARCH_MCP_TOOL_NAME)

/** The internal tools only appear when at least this many MCP servers are connected. */
internal const val MCP_PAGINATION_THRESHOLD = 5
internal const val MCP_PAGE_DEFAULT_LENGTH = 5
private const val MCP_PAGE_MAX_LENGTH = 50

/**
 * Keeps the tool list small when many MCP servers are enabled. Only one page of servers is attached to
 * each model request; the model switches pages with [MCPS_PAGINATE_TOOL_NAME]. State lives for one generation.
 * Pages are 1-based and index 0 attaches every server.
 */
internal class McpPagination(private val entries: List<Entry>) {
    data class Entry(val serverId: String, val name: String, val tools: List<ToolDefinition>)

    val active: Boolean = entries.size >= MCP_PAGINATION_THRESHOLD
    private var page = 1
    private var length = MCP_PAGE_DEFAULT_LENGTH
    private var showDescription = false

    /** Short app-side summary of the last internal call, shown in the tool activity. */
    var lastPreview: String = ""
        private set

    fun includes(serverId: String): Boolean = !active || attached().any { it.serverId == serverId }

    /** MCP tool descriptions are the bulk of the input, so they are dropped unless requested. */
    fun present(tool: ToolDefinition): ToolDefinition =
        if (!active || showDescription || tool.serverId == null) tool else tool.copy(description = "")

    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = LIST_MCP_TOOL_NAME,
            description = "List the names of all ${entries.size} enabled MCP servers and which of them are currently attached. " +
                "Only one page of MCP servers is attached at a time.",
            schema = emptySchema()
        ),
        ToolDefinition(
            name = MCPS_PAGINATE_TOOL_NAME,
            description = "Choose which MCP servers are attached to the next requests. index is the 1-based page number " +
                "(page size = length, default $MCP_PAGE_DEFAULT_LENGTH); index 0 or omitted attaches all MCP servers. " +
                "Tool descriptions are hidden unless show_description is true. Returns OK; the attached tools appear in the next request.",
            schema = obj(
                "type" to str("object"),
                "properties" to obj(
                    "index" to obj("type" to str("integer"), "description" to str("1-based page number. 0 or omitted attaches all MCP servers.")),
                    "length" to obj("type" to str("integer"), "description" to str("MCP servers per page. Default $MCP_PAGE_DEFAULT_LENGTH.")),
                    "show_description" to obj("type" to str("boolean"), "description" to str("Include tool descriptions. Default false."))
                )
            )
        ),
        ToolDefinition(
            name = SEARCH_MCP_TOOL_NAME,
            description = "Search the enabled MCP servers by keyword in the server name, tool name or tool description. " +
                "Returns matching MCP names, the page each is on and matching tool names; use mcps_paginate to attach that page.",
            schema = obj(
                "type" to str("object"),
                "properties" to obj("query" to obj("type" to str("string"), "description" to str("Keywords to search for."))),
                "required" to JsonArray(listOf(str("query")))
            )
        )
    )

    fun execute(name: String, args: JsonObject): JsonObject = when (name) {
        LIST_MCP_TOOL_NAME -> list()
        MCPS_PAGINATE_TOOL_NAME -> paginate(args)
        SEARCH_MCP_TOOL_NAME -> search(args)
        else -> error("Unknown internal tool: $name")
    }

    private fun attached(): List<Entry> =
        if (page == 0) entries else entries.drop((page - 1) * length).take(length)

    private fun list(): JsonObject {
        lastPreview = "Listed ${entries.size} MCP(s)."
        return obj(
            "total" to JsonPrimitive(entries.size),
            "mcps" to JsonArray(entries.map { str(it.name) }),
            "attached" to JsonArray(attached().map { str(it.name) })
        )
    }

    private fun paginate(args: JsonObject): JsonObject {
        val index = args.int("index") ?: 0
        val newLength = args.int("length") ?: MCP_PAGE_DEFAULT_LENGTH
        require(newLength in 1..MCP_PAGE_MAX_LENGTH) { "length must be between 1 and $MCP_PAGE_MAX_LENGTH." }
        val pages = (entries.size + newLength - 1) / newLength
        require(index in 0..pages) {
            "Page $index does not exist: there are $pages page(s) of $newLength. Use index 0 to attach all MCPs."
        }
        page = index
        length = newLength
        showDescription = args["show_description"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val names = attached().joinToString { it.name }
        lastPreview = if (index == 0) "Attached all ${entries.size} MCP(s)." else "Attached page $index/$pages: $names"
        return obj("content" to JsonArray(listOf(obj("type" to str("text"), "text" to str("OK")))))
    }

    private fun search(args: JsonObject): JsonObject {
        val query = args["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        require(query.isNotEmpty()) { "query is required." }
        val terms = query.lowercase().split(Regex("\\s+"))
        val matches = entries.mapIndexedNotNull { position, entry ->
            val serverHit = terms.all { entry.name.lowercase().contains(it) }
            val tools = entry.tools.filter { tool ->
                val text = "${tool.originalName} ${tool.description}".lowercase()
                terms.all { text.contains(it) }
            }
            if (!serverHit && tools.isEmpty()) null else obj(
                "name" to str(entry.name),
                "page" to JsonPrimitive(position / length + 1),
                "matching_tools" to JsonArray(tools.take(5).map { str(it.originalName) })
            )
        }
        lastPreview = "Found ${matches.size} MCP(s) for \"$query\"."
        return obj("query" to str(query), "page_size" to JsonPrimitive(length), "matches" to JsonArray(matches))
    }

    private fun emptySchema() = obj("type" to str("object"), "properties" to obj())

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()
}
