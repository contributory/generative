package org.starfall.generative.home.chat.logic.tool

import kotlinx.serialization.json.*
import org.starfall.generative.shared.networking.obj
import org.starfall.generative.shared.networking.str
import org.starfall.generative.home.shared.logic.ToolSummary

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
/**
 * Keep the complete result for the model. `preview` is app-only activity metadata and is never
 * substituted for the result placed in the model conversation history.
 */
internal suspend fun prepareGenericToolResult(result: JsonObject): ToolSummary {
    val media = Regex("tool-file:([a-zA-Z0-9._-]+)").findAll(result.toString())
        .map { it.groupValues[1] }.distinct().take(32).toList()
    val preview = when {
        (result["isError"] as? JsonPrimitive)?.booleanOrNull == true -> "Tool reported an error."
        media.isNotEmpty() -> "Tool completed with ${media.size} file(s). Open details to view."
        else -> "Tool completed."
    }
    return ToolSummary(content = result, preview = preview, files = media)
}
