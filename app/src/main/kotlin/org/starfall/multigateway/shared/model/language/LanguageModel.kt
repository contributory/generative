package org.starfall.multigateway.shared.model.language

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LanguageModel(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val icon: String? = null,
    @SerialName("provider_id") val providerId: String,
    @SerialName("input_capabilities") val inputCapabilities: Capabilities = Capabilities(),
    @SerialName("output_capabilities") val outputCapabilities: Capabilities = Capabilities(),
    @SerialName("model_tools") val modelTools: List<String> = emptyList()
)
