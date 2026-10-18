package org.starfall.multigateway.shared.model.language

import kotlinx.serialization.Serializable

@Serializable
data class Capabilities(
    val text: Boolean = true,
    val image: Boolean = true,
    val video: Boolean = false,
    val embed: Boolean = false,
    val audio: Boolean = false,
    val others: String? = null
)
