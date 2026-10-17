package org.starfall.multigateway.shared.model.provider

/** Ordering never removes a selected model or replaces its configuration. */
internal fun ProviderConfiguration.reorderedModels(requestedIds: List<String>): ProviderConfiguration {
    val existingIds = modelIds ?: modelConfigs.keys.toList()
    val existingSet = existingIds.toSet()
    val preferred = requestedIds.filter { it in existingSet }.distinct()
    val preferredSet = preferred.toSet()
    val ordered = preferred + existingIds.filterNot { it in preferredSet }
    val configOrder = ordered.filter { it in modelConfigs } + modelConfigs.keys.filterNot { it in ordered }
    return copy(modelIds = ordered, modelConfigs = configOrder.associateWith { modelConfigs.getValue(it) })
}
