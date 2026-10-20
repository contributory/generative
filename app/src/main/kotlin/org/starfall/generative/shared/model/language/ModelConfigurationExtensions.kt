package org.starfall.generative.shared.model.language


/**
 * Only for reusable model templates (ModelConfigurationMemory): context windows at or below the
 * app default collapse to the default. Never apply this to a model the user has edited or to a
 * limit reported by the provider API, or a deliberately smaller context window would be lost.
 */
fun ModelConfiguration.normalizedForTemplate(): ModelConfiguration =
    if (contextWindowTokens > DEFAULT_CONTEXT_WINDOW_TOKENS) this
    else copy(contextWindowTokens = DEFAULT_CONTEXT_WINDOW_TOKENS)

/** The effective passback mode; saved configs without the new field migrate from the old switch. */
val ModelConfiguration.passbackMode: ThinkingPassback
    get() = thinkingPassback
        ?: if (legacySendThinkingContent == true) ThinkingPassback.ON else ThinkingPassback.OFF

fun ModelConfiguration.withThinkingPassback(mode: ThinkingPassback): ModelConfiguration =
    copy(thinkingPassback = mode, legacySendThinkingContent = null)

/** Resolves the mode for a request: Auto passes reasoning back only once tool calls are involved. */
fun ModelConfiguration.sendsThinkingBack(hasToolCalls: Boolean): Boolean = when (passbackMode) {
    ThinkingPassback.ON -> true
    ThinkingPassback.OFF -> false
    ThinkingPassback.AUTO -> hasToolCalls
}
