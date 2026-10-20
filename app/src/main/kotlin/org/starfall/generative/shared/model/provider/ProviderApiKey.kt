package org.starfall.generative.shared.model.provider

import kotlinx.serialization.Serializable

@Serializable
data class ProviderApiKey(
    val id: String,
    val label: String = "",
    val value: String
)
