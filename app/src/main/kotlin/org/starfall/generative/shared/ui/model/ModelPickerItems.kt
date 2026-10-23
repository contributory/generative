package org.starfall.generative.shared.ui.model

import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup

private const val MODEL_LABEL_PREFIX_LIMIT = 20

/** Keep the model's final path segment visible when a namespace or prefix is unusually long. */
internal fun compactModelLabel(value: String): String {
    val slash = value.indexOf('/')
    if (slash <= MODEL_LABEL_PREFIX_LIMIT) return value
    val prefix = value.substring(0, slash)
    return "${prefix.first()}...${prefix.takeLast(5)}/${value.substring(slash + 1)}"
}

sealed interface ModelPickerItem {
    data class Group(
        val group: ProviderGroup,
        val providerCount: Int
    ) : ModelPickerItem

    data class Provider(
        val provider: ModelProvider,
        val modelCount: Int,
        val depth: Int,
        val hasSelectedModel: Boolean = false
    ) : ModelPickerItem

    data class Model(
        val provider: ModelProvider,
        val modelId: String,
        val config: ModelConfiguration,
        val isSelected: Boolean,
        val depth: Int
    ) : ModelPickerItem
}

internal val ModelPickerItem.folderId: String?
    get() = when (this) {
        is ModelPickerItem.Group -> group.id
        is ModelPickerItem.Provider -> provider.groupId.takeIf { depth == 1 }
        is ModelPickerItem.Model -> provider.groupId.takeIf { depth == 2 }
    }

internal data class ModelPickerTab(
    val key: String,
    val folderId: String?,
    val label: String,
    val ungroupedOnly: Boolean = false
)

internal val chatModelPickerFilter: (ModelProvider, String, ModelConfiguration) -> Boolean = { _, _, config ->
    config.modelType == org.starfall.generative.shared.model.language.ModelType.TEXT_GENERATION
}

fun computeModelPickerItems(
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup>,
    dynamicModelsMap: Map<String, List<String>>,
    selectedProviderId: String,
    selectedModelId: String,
    query: String = "",
    providerFilterId: String? = null,
    collapsedGroupIds: Set<String> = emptySet(),
    collapsedProviderIds: Set<String> = emptySet(),
    expandSearchResults: Boolean = true,
    selectedFolderId: String? = null,
    ungroupedOnly: Boolean = false,
    modelFilter: (ModelProvider, String, ModelConfiguration) -> Boolean = { _, _, _ -> true }
): List<ModelPickerItem> {
    data class ProviderNode(val provider: ModelProvider, val models: List<String>)

    val defaultConfig = ModelConfiguration()
    val normalizedQuery = query.trim()
    val groupById = providerGroups.associateBy { it.id }
    val providerOrder = compareBy<ModelProvider> { it.id != selectedProviderId }
        .thenBy { it.sortOrder }
        .thenBy { it.id }
    val orderedProviders = when {
        selectedFolderId != null -> providers
            .filter { it.groupId == selectedFolderId }
            .sortedWith(providerOrder)
        ungroupedOnly -> providers
            .filter { it.groupId == null || it.groupId !in groupById }
            .sortedWith(providerOrder)
        else -> buildList {
            providerGroups
                .sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.name.lowercase() })
                .forEach { group ->
                    addAll(providers.filter { it.groupId == group.id }.sortedWith(providerOrder))
                }
            addAll(
                providers
                    .filter { it.groupId == null || it.groupId !in groupById }
                    .sortedWith(providerOrder)
            )
        }
    }
    val visibleProviders = orderedProviders.mapNotNull { provider ->
        if (providerFilterId != null && provider.id != providerFilterId) return@mapNotNull null

        val groupMatches = provider.groupId
            ?.let(groupById::get)
            ?.name
            ?.contains(normalizedQuery, ignoreCase = true) == true
        val providerMatches = provider.name.contains(normalizedQuery, ignoreCase = true)
        val models = providerModels(provider, dynamicModelsMap, selectedProviderId, selectedModelId)
            .filter { modelId ->
                modelFilter(provider, modelId, provider.config.modelConfigs[modelId] ?: defaultConfig)
            }
            .filter { modelId ->
                normalizedQuery.isBlank() ||
                    groupMatches ||
                    providerMatches ||
                    modelId.contains(normalizedQuery, ignoreCase = true) ||
                    provider.config.modelConfigs[modelId]?.displayName.orEmpty()
                        .contains(normalizedQuery, ignoreCase = true)
            }

        if (models.isEmpty()) null else ProviderNode(provider, models)
    }

    val forceExpanded = (normalizedQuery.isNotBlank() && expandSearchResults) || providerFilterId != null
    fun modelItems(node: ProviderNode, depth: Int): List<ModelPickerItem.Model> =
        node.models.map { modelId ->
            val config = node.provider.config.modelConfigs[modelId] ?: defaultConfig
            // Model IDs are scoped to a provider. Display names are presentation-only and may
            // legitimately be shared by multiple models, so selection must use both IDs exactly.
            val isSelected = selectedProviderId.isNotBlank() &&
                selectedModelId.isNotBlank() &&
                node.provider.id == selectedProviderId &&
                modelId == selectedModelId
            ModelPickerItem.Model(node.provider, modelId, config, isSelected, depth)
        }

    return buildList {
        visibleProviders.forEach { node ->
            val models = modelItems(node, depth = 1)
            add(ModelPickerItem.Provider(
                node.provider,
                node.models.size,
                depth = 0,
                hasSelectedModel = models.any { it.isSelected }
            ))
            addAll(if (!forceExpanded && node.provider.id in collapsedProviderIds) {
                // Keep the selected model visible so collapsing a provider never hides
                // the model that the picker is currently representing.
                models.filter { it.isSelected }
            } else models)
        }
    }
}

private fun providerModels(
    provider: ModelProvider,
    dynamicModelsMap: Map<String, List<String>>,
    selectedProviderId: String,
    selectedModelId: String
): List<String> = (
        provider.config.modelIds
            ?: ((dynamicModelsMap[provider.id]
                ?: defaultProviderModels(provider.type)) + provider.config.modelConfigs.keys)
        )
    .plus(if (provider.id == selectedProviderId && selectedModelId.isNotBlank()) listOf(selectedModelId) else emptyList())
    .distinct()


internal fun defaultProviderModels(type: ModelApiType): List<String> = emptyList()

/** Folder tabs for the picker: default, one per folder that has providers, then ungrouped when needed. */
internal fun computeModelPickerTabs(
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup>,
    defaultLabel: String,
    ungroupedLabel: String
): List<ModelPickerTab> {
    val groupsById = providerGroups.associateBy { it.id }
    val providerGroupIds = providers.mapNotNull { it.groupId }.filter { it in groupsById }.toSet()
    val hasUngroupedProviders = providers.any { provider ->
        provider.groupId == null || provider.groupId !in groupsById
    }
    return buildList {
        add(ModelPickerTab(key = "default", folderId = null, label = defaultLabel))
        providerGroups
            .asSequence()
            .filter { it.id in providerGroupIds }
            .sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.name.lowercase() })
            .forEach { group ->
                add(ModelPickerTab(key = "folder_${group.id}", folderId = group.id, label = group.name))
            }
        if (hasUngroupedProviders) {
            add(ModelPickerTab(key = "ungrouped", folderId = null, label = ungroupedLabel, ungroupedOnly = true))
        }
    }
}
