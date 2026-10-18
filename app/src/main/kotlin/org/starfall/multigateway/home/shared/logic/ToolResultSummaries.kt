package org.starfall.multigateway.home.shared.logic

import kotlinx.serialization.json.*
import org.starfall.multigateway.shared.files.ToolFiles

internal data class ToolSummary(
    val content: JsonObject,
    val preview: String,
    val files: List<String>
)

/** Summary for generated media and Content-API responses. */
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
