package org.starfall.generative.shared.model.language

/** Off is just another reasoning level ("none"/"off"), not a separate flag. */
val ModelConfiguration.reasoningEffortDisabled: Boolean
    get() = reasoningEffort?.trim()?.lowercase() in listOf("none", "off")

/** The levels this model offers, in canonical order. */
val ModelConfiguration.supportedReasoningEfforts: List<String>
    get() = SELECTABLE_REASONING_EFFORTS.filter { it in reasoningEfforts }

/**
 * The saved effort, but only when the selector can actually show it: Off, Active or a level this
 * model supports. Anything else is shown as Default, so it is never sent either.
 */
val ModelConfiguration.requestReasoningEffort: String?
    get() = reasoningEffort?.trim()?.lowercase()?.takeIf {
        it in listOf("none", "off", REASONING_EFFORT_ACTIVE) || it in supportedReasoningEfforts
    }

/** The level written into the request body; Active carries no level. */
val ModelConfiguration.wireReasoningEffort: String?
    get() = requestReasoningEffort?.takeUnless { it == REASONING_EFFORT_ACTIVE }

fun ModelConfiguration.withConversationReasoning(effort: String?): ModelConfiguration {
    if (effort.isNullOrBlank()) return this
    return copy(reasoningEffort = if (effort == "off") "none" else effort)
}

/** Persist a selected-model-sheet choice; Off is stored as the "none" effort level. */
fun ModelConfiguration.withSelectedReasoningEffort(effort: String?): ModelConfiguration {
    val applied = if (effort.isNullOrBlank()) copy(reasoningEffort = null)
    else withConversationReasoning(effort.trim().lowercase())
    val rememberedLevel = if (applied.reasoningEffortDisabled) {
        thinkingLevel?.takeUnless { it.trim().lowercase() in listOf("none", "off") }
            ?: reasoningEffort?.takeUnless { it.trim().lowercase() in listOf("none", "off") }
    } else applied.reasoningEffort
    return applied.copy(
        thinkingLevel = if (applied.reasoningEffortDisabled) rememberedLevel else applied.reasoningEffort
    )
}



// Keep Claude budgets inside its output token limit.
fun ModelConfiguration.reasoningBudget(maxTokens: Int): Int =
    minOf(when (reasoningEffort) {
        "minimal", "low" -> 1024
        "medium" -> 2048
        "high" -> 4096
        "xhigh", "max" -> 8192
        REASONING_EFFORT_ACTIVE -> ACTIVE_THINKING_BUDGET
        else -> 1024
    }, maxTokens - 1)

fun ModelConfiguration.googleThinkingConfig(): kotlinx.serialization.json.JsonObject? {
    val effort = requestReasoningEffort
    return when {
        reasoningEffortDisabled -> kotlinx.serialization.json.buildJsonObject {
            put("thinkingBudget", kotlinx.serialization.json.JsonPrimitive(0))
            put("includeThoughts", kotlinx.serialization.json.JsonPrimitive(false))
        }
        effort != null -> kotlinx.serialization.json.buildJsonObject {
            wireReasoningEffort?.let { put("thinkingLevel", kotlinx.serialization.json.JsonPrimitive(it)) }
            put("includeThoughts", kotlinx.serialization.json.JsonPrimitive(true))
        }
        else -> null
    }
}
