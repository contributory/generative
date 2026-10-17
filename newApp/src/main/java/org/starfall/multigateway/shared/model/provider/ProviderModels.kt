package org.starfall.multigateway.shared.model.provider

import org.starfall.multigateway.shared.model.language.LanguageModel

import kotlinx.serialization.Serializable

@Serializable
data class ProviderModels(
    val id: String,
    val models: List<LanguageModel> = emptyList()
)
