package org.starfall.multigateway.shared.model.provider

import org.starfall.multigateway.shared.model.api.ModelApiType

/** Update each default field independently; custom provider details survive type changes. */
fun ModelProvider.withModelApiType(newType: ModelApiType): ModelProvider = copy(
    type = newType,
    name = if (name == type.defaultName) newType.defaultName else name,
    baseUrl = if (baseUrl.trimEnd('/') == type.defaultBaseUrl) newType.defaultBaseUrl else baseUrl
)

fun ModelProvider.streamEnabledFor(modelId: String): Boolean =
    config.modelConfigs[modelId]?.supportStream ?: config.supportStream
