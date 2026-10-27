package org.starfall.generative.features.home.chat.logic.tool

import kotlinx.serialization.json.*
import org.starfall.generative.shared.model.*
import org.starfall.generative.shared.networking.obj
import org.starfall.generative.shared.networking.str

internal const val CREATE_SKILL_TOOL_NAME = "create_skill"
internal const val SEARCH_TOOLS_TOOL_NAME = "search_tools"

internal val createSkillDefinition = ToolDefinition(
    CREATE_SKILL_TOOL_NAME,
    "Save a reusable agent skill in the user's Skills library. Call this when the user asks to create a skill. Provide a concise name and reusable instruction content, not a one-time answer.",
    obj("type" to str("object"), "properties" to obj(
        "name" to obj("type" to str("string"), "description" to str("Skill name")),
        "content" to obj("type" to str("string"), "description" to str("Complete reusable skill instructions"))
    ), "required" to JsonArray(listOf(str("name"), str("content"))))
)

internal val searchToolsDefinition = ToolDefinition(
    SEARCH_TOOLS_TOOL_NAME,
    "Search available feature and enabled-MCP tool names, including disabled tools. Returns only matching names and enabled states. Search by any partial word or name fragment.",
    obj("type" to str("object"), "properties" to obj(
        "query" to obj("type" to str("string"), "description" to str("Tool name or words to search for"))
    ), "required" to JsonArray(listOf(str("query"))))
)

internal data class SearchableTool(val name: String, val enabled: Boolean)

internal fun searchToolCatalog(query: String, catalog: List<SearchableTool>): JsonArray {
    val words = Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase()).map { it.value }.toList()
    return JsonArray(catalog.filter { tool ->
        val name = tool.name.lowercase()
        val parts = Regex("[\\p{L}\\p{N}]+").findAll(name).map { it.value }.toList()
        words.isEmpty() || words.any { word ->
            name.contains(word) || word.contains(name) || parts.any { it.contains(word) || word.contains(it) }
        }
    }.distinctBy { it.name }.map { tool ->
        obj("name" to str(tool.name), "enabled" to JsonPrimitive(tool.enabled))
    })
}
