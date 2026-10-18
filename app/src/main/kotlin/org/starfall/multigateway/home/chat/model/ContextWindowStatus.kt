package org.starfall.multigateway.home.chat.model

data class ContextWindowStatus(val estimatedTokens: Long, val limit: Int) {
    val shouldSuggestSummary get() = estimatedTokens * 100 >= limit.toLong() * 90
    val shouldAutoSummarize get() = estimatedTokens * 100 >= limit.toLong() * 99
    val percentage get() = (estimatedTokens * 100 / limit.coerceAtLeast(1)).coerceAtMost(100).toInt()
}
