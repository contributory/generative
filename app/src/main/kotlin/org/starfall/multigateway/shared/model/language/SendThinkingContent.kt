package org.starfall.multigateway.shared.model.language

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Whether earlier reasoning is sent back to the model with the conversation history.
 * [AUTO] sends it only in conversations that use tool calls, where providers need the reasoning
 * chain to stay intact; [ON] always sends it; [OFF] never does.
 */
@Serializable
enum class SendThinkingContent {
    @SerialName("auto")
    AUTO,

    @SerialName("on")
    ON,

    @SerialName("off")
    OFF
}

typealias ThinkingPassback = SendThinkingContent
