package org.starfall.generative.shared.model.provider

import kotlinx.serialization.Serializable

/** Lifetime of an Anthropic ephemeral cache entry; every cache hit refreshes it. */
@Serializable
enum class AnthropicCacheTtl(val wireValue: String) {
    FIVE_MINUTES("5m"),
    ONE_HOUR("1h")
}

/**
 * Anthropic Messages prompt caching. Each enabled part places one `cache_control` breakpoint
 * (the API allows at most four per request), so the prefix up to it can be reused by later requests.
 */
@Serializable
data class AnthropicPromptCache(
    val enabled: Boolean = true,
    val ttl: AnthropicCacheTtl = AnthropicCacheTtl.FIVE_MINUTES,
    val cacheSystem: Boolean = true,
    val cacheTools: Boolean = true,
    val cacheConversation: Boolean = true
)
