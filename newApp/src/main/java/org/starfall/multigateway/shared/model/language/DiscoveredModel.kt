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
            supportStream = supported("streaming"),
            modelJson = metadata)
    }
}
