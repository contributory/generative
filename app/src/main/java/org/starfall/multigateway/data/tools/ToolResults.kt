package org.starfall.multigateway.data.tools

import kotlinx.serialization.json.*

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
private const val INLINE_TOOL_RESULT_LIMIT = 10_000
internal data class ToolSummary(val content: JsonObject, val preview: String, val files: List<String>, val responseFile: String)
internal suspend fun summarizeToolResult(result: JsonObject, files: ToolFiles): ToolSummary {
    val full = result.toString()
    val media = Regex("tool-file:([a-zA-Z0-9._-]+)").findAll(full).map { it.groupValues[1] }.distinct().take(32).toList()
    val needsResponseFile = media.isNotEmpty() || full.length >= INLINE_TOOL_RESULT_LIMIT
    val details = if (needsResponseFile) files.save(full.byteInputStream(), "application/json") else ""
    val truncated = full.length >= INLINE_TOOL_RESULT_LIMIT
    val content = if (!truncated) result else obj(
        "truncated" to JsonPrimitive(true), "characters" to JsonPrimitive(full.length),
        "preview" to str(full.take(2000)),
        "files" to JsonArray((media + details).filter { it.isNotBlank() }.map { str("tool-file:$it") }),
        "isError" to (result["isError"] ?: JsonPrimitive(false)))
    val preview = when {
        (result["isError"] as? JsonPrimitive)?.booleanOrNull == true && details.isNotBlank() ->
            "Tool reported an error. Open the saved result for details."
        details.isNotBlank() && media.isEmpty() -> "Tool completed. Open the saved result for details."
        media.isNotEmpty() -> "Saved ${media.size} file(s). Open Storage for details."
        else -> "Tool completed."
    }
    return ToolSummary(content, preview, (media + details).filter { it.isNotBlank() }, details)
}
