package org.starfall.generative.shared.icons.model

/** An app-owned icon together with its matching rules and optional theme variants. */
data class StoredIcon(
    val image: String,
    val filename: String,
    val patterns: List<String>,
    val lightImage: String = image,
    val darkImage: String? = null
)
