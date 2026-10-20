package org.starfall.generative.shared.model.generation

import kotlinx.serialization.Serializable

/** A snapshot of one API request, never a sum across conversation/tool requests. */
@Serializable
data class TokenUsage(
    val inputTokens: Long,
    val outputTokens: Long? = null
) {
    val isValid: Boolean
        get() = inputTokens >= 0 && (outputTokens == null || outputTokens >= 0) &&
            inputTokens <= Long.MAX_VALUE - (outputTokens ?: 0)

    val contextTokens: Long get() = inputTokens + (outputTokens ?: 0)
}

/** Binds API usage to a specific active history and request configuration without storing prompts. */
@Serializable
data class ContextUsageSnapshot(
    val usage: TokenUsage,
    val contextKey: String,
    val messagesFingerprint: String
)
