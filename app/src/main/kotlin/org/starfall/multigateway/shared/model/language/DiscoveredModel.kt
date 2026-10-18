package org.starfall.multigateway.shared.model.language

import kotlinx.serialization.json.*

data class DiscoveredModel(
    val id: String,
    val contextWindowTokens: Int? = null,
    val displayName: String = "",
    val metadata: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
) {
    fun configuration(): ModelConfiguration {
        val supports = (metadata["capabilities"] as? JsonObject)?.get("supports") as? JsonObject
        fun supported(key: String) = (supports?.get(key) as? JsonPrimitive)?.booleanOrNull
        val thinking = supports?.let {
            supported("adaptive_thinking") == true || (it["reasoning_effort"] as? JsonArray)?.isNotEmpty() == true ||
                listOf("min_thinking_budget", "max_thinking_budget").any { key ->
                    (it[key] as? JsonPrimitive)?.longOrNull?.let { budget -> budget > 0 } == true
                }
        }
        return ModelConfiguration(displayName = displayName,
            contextWindowTokens = contextWindowTokens ?: DEFAULT_CONTEXT_WINDOW_TOKENS,
            supportsVision = supported("vision") ?: true,
            supportsThinking = thinking ?: true,
            supportsToolCalls = supported("tool_calls") ?: true,
            modelTools = modelToolsFromMetadata(metadata),
            supportStream = supported("streaming"),
            modelJson = metadata)
    }
}


internal fun modelToolsFromMetadata(metadata: JsonObject): List<String> {
    val value = metadata["model_tools"]
        ?: (metadata["metadata"] as? JsonObject)?.get("model_tools")
        ?: (metadata["capabilities"] as? JsonObject)?.get("model_tools")
    return (value as? JsonArray).orEmpty().mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive -> entry.contentOrNull?.takeIf { it.isNotBlank() }?.let(::canonicalModelToolName)
            is JsonObject -> ((entry["name"] ?: entry["type"] ?: entry["id"]) as? JsonPrimitive)
                ?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::canonicalModelToolName)
            else -> null
        }
    }.distinct()
}

private fun canonicalModelToolName(name: String): String = when {
    name.startsWith("web_search_") -> "web_search"
    name.startsWith("web_fetch_") -> "web_fetch"
    name.startsWith("code_execution_") -> "code_execution"
    name == "google_search_retrieval" -> "google_search"
    else -> name
}
