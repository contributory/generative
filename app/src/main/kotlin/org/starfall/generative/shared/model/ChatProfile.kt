package org.starfall.generative.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Keep the stored config/system_prompt shape so existing profiles retain their prompts.
@Serializable
data class AiChatConfig(
    @SerialName("system_prompt") val systemPrompt: String = "",
    val mcpAccess: Map<String, McpAccess> = emptyMap()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatProfile(
    val id: String,
    val name: String,
    val icon: String? = null,
    val config: AiChatConfig = AiChatConfig(),
    val sortOrder: Int = Int.MAX_VALUE,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("active_mcp") val activeMcp: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("active_model_tools") val activeModelTools: List<String> = emptyList()
)
