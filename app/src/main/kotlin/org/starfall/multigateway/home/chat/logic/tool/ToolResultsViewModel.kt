package org.starfall.multigateway.home.chat.logic.tool

import kotlinx.serialization.json.*
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.str

internal class ToolBudget {
    private var total = 0
    private var images = 0
    private var videos = 0
    fun consume(systemTool: String?) {
        check(total < 24) { "Tool limit reached: 24 calls per message. Send another message to continue." }
        check(systemTool != "generate_image" || images < 2) { "Image generation limit reached: 2 calls per message." }
        check(systemTool != "generate_video" || videos < 1) { "Video generation limit reached: 1 call per message." }
        total++
        if (systemTool == "generate_image") images++
        if (systemTool == "generate_video") videos++
    }
}
private const val TOOL_RESULT_FILE_LIMIT = 10_000
private const val TOOL_RESULT_PREVIEW_LIMIT = 6_000
internal data class ToolSummary(
    val content: JsonObject,
    val preview: String,
    val files: List<String>
)

/**
 * Summary for generated media and Content-API responses. Only the media itself is stored as files;
 * the response JSON is never written to disk, so nothing extra is left for the user to clean up.
 */
@Suppress("UNUSED_PARAMETER")
internal fun summarizeToolResult(result: JsonObject, files: ToolFiles): ToolSummary {
    val media = Regex("tool-file:([a-zA-Z0-9._-]+)").findAll(result.toString()).map { it.groupValues[1] }.distinct().take(32).toList()
    val isError = (result["isError"] as? JsonPrimitive)?.booleanOrNull == true
    val preview = when {
        isError -> "Tool reported an error."
        media.isNotEmpty() -> "Saved ${media.size} file(s). Open Storage for details."
        else -> "Tool completed."
    }
    return ToolSummary(result, preview, media)
}

/** Compact response handling for ordinary MCP tools, separate from media and Content-API. */
internal suspend fun summarizeGenericToolResult(result: JsonObject): ToolSummary {
    val completeResult = result
    val full = completeResult.toString()
    val media = Regex("tool-file:([a-zA-Z0-9._-]+)").findAll(full).map { it.groupValues[1] }.distinct().take(32).toList()
    val truncated = full.length > TOOL_RESULT_FILE_LIMIT
    val content = if (!truncated) completeResult else obj(
        "truncated" to JsonPrimitive(true), "characters" to JsonPrimitive(full.length),
        "preview" to str(full.take(TOOL_RESULT_PREVIEW_LIMIT)),
        "files" to JsonArray(media.map { str("tool-file:$it") }),
        "isError" to (completeResult["isError"] ?: JsonPrimitive(false)))
    val preview = when {
        (completeResult["isError"] as? JsonPrimitive)?.booleanOrNull == true && truncated ->
            "Tool reported an error. Open details to view the result."
        truncated -> "Tool completed. Open details to view the result."
        media.isNotEmpty() -> "Tool completed with ${media.size} file(s). Open details to view."
        else -> "Tool completed."
    }
    return ToolSummary(content = content, preview = preview, files = media)
}
