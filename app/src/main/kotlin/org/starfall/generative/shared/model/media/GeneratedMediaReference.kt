package org.starfall.generative.shared.model.media

data class GeneratedMediaReference(
    val value: String,
    val isRemoteUrl: Boolean,
    val useProviderAuth: Boolean = false
)
