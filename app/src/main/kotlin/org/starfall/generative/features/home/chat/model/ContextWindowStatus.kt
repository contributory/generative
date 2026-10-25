package org.starfall.generative.features.home.chat.model

import org.starfall.generative.shared.model.generation.TokenUsage

data class ContextWindowStatus(
    val estimatedTokens: Long,
    val limit: Int,
    val apiUsage: TokenUsage? = null,
    val estimatedAdditionalTokens: Long = 0
) {
    val usedTokens get() = estimatedTokens
    val isEstimated get() = apiUsage == null || estimatedAdditionalTokens > 0
    val shouldSuggestSummary get() = reachedPercent(90)
    fun reachedPercent(percent: Int) = usedTokens >=
        (limit.coerceAtLeast(1).toLong() * percent.coerceIn(0, 100) + 99) / 100
    val shouldAutoSummarize get() = reachedPercent(99)
    val percentage get() = (usedTokens.coerceIn(0, limit.coerceAtLeast(1).toLong()) * 100 /
        limit.coerceAtLeast(1)).toInt()
}
