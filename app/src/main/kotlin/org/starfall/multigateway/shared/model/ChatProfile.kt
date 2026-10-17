package org.starfall.multigateway.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Keep the stored config/system_prompt shape so existing profiles retain their prompts.
@Serializable
data class AiChatConfig(
    @SerialName("system_prompt") val systemPrompt: String = "",
    val mcpAccess: Map<String, McpAccess> = emptyMap()
)

@Serializable
data class ChatProfile(
    val id: String,
    val name: String,
    val icon: String? = null,
    val config: AiChatConfig = AiChatConfig(),
    val sortOrder: Int = Int.MAX_VALUE
)
