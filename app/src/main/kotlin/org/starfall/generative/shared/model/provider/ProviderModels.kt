package org.starfall.generative.shared.model.provider

import org.starfall.generative.shared.model.language.LanguageModel

import kotlinx.serialization.Serializable

@Serializable
data class ProviderModels(
    val id: String,
    val models: List<LanguageModel> = emptyList()
)
