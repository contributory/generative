package org.starfall.multigateway.shared.model.provider

import kotlinx.serialization.Serializable

@Serializable
data class ProviderGroup(
    val id: String,
    val name: String,
    val sortOrder: Int = Int.MAX_VALUE,
    val icon: String? = null
)
