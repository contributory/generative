package org.starfall.multigateway.shared.model.language

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.starfall.multigateway.shared.model.media.MediaGenerationMode

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ModelConfiguration(
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    // null inherits the provider setting; false is an explicit override.
    val supportStream: Boolean? = null,
    val displayName: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    val modelType: ModelType = ModelType.TEXT_GENERATION,
    // Image input capability. Kept as supportsVision for backward compatibility with saved configs.
    val supportsVision: Boolean = true,
    val supportsVideoInput: Boolean = false,
    val supportsAudioInput: Boolean = false,
    val supportsThinking: Boolean = true,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    // Reasoning levels saved for this model; reasoning-capable models default to the common levels.
    @SerialName("reasoning_efforts")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reasoningEfforts: List<String> = DEFAULT_REASONING_EFFORTS,
    // Last chosen thinking level (set from the chat's selected-model sheet). Kept while thinking
    // is switched off so turning it back on restores the level; null means the provider default.
    @SerialName("thinking_level")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thinkingLevel: String? = null,
    val supportsToolCalls: Boolean = true,
    @SerialName("model_tools") val modelTools: List<String> = emptyList(),
    @SerialName("enabled_model_tools") val enabledModelTools: List<String> = emptyList(),
    // Legacy on/off switch, read only to migrate saved configs (true -> On, anything else -> Auto).
    // Never written again once [thinkingPassback] is set.
    @SerialName("send_thinking_content")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val legacySendThinkingContent: Boolean? = null,
    @SerialName("thinking_passback")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thinkingPassback: ThinkingPassback? = null,
    val icon: String? = null,
    @SerialName("model_json")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val modelJson: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    /** API integration selected for image/video models; omitted legacy values use the standard API. */
    val mediaApiType: MediaGenerationMode = MediaGenerationMode.DEFAULT,
    /** Reusable Custom profile selected for this image/video model. */
    val mediaRequestProfileId: String = ""
) {
    val supportedModelTools: List<String>
        get() = modelTools.ifEmpty { modelToolsFromMetadata(modelJson) }
}
