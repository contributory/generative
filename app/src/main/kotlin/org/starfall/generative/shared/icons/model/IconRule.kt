package org.starfall.generative.shared.icons.model

import java.util.UUID
import kotlinx.serialization.Serializable

/** User-defined regular-expression rule mapping a provider/model name to an icon. */
@Serializable
data class IconRule(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
    val image: String
)
