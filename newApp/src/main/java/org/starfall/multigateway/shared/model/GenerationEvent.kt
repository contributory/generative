package org.starfall.multigateway.shared.model

/** Events emitted across the model/tool orchestration boundary during a generation turn. */
sealed interface GenerationEvent {
    data class Text(val text: String) : GenerationEvent
    data class Reasoning(val text: String, val signature: String? = null) : GenerationEvent
    data class Tool(val activity: ToolActivity) : GenerationEvent
    /** A user message inserted between a completed tool turn and the next model turn. */
    data class UserMessage(val message: StoredMessage) : GenerationEvent
}
