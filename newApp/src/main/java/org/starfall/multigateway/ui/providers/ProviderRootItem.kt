package org.starfall.multigateway.ui.providers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.starfall.multigateway.shared.model.ModelProvider
import org.starfall.multigateway.shared.model.ProviderGroup
import org.starfall.multigateway.shared.model.ProviderRootOrderItem

internal sealed interface ProviderRootItem {
    val id: String
    val sortOrder: Int

    data class GroupItem(val group: ProviderGroup) : ProviderRootItem {
        override val id: String get() = group.id
        override val sortOrder: Int get() = group.sortOrder
    }

    data class ProviderItem(val provider: ModelProvider) : ProviderRootItem {
        override val id: String get() = provider.id
        override val sortOrder: Int get() = provider.sortOrder
    }
}

internal fun providerRootItems(
    providers: List<ModelProvider>,
    groups: List<ProviderGroup>
): List<ProviderRootItem> {
    val groupIds = groups.mapTo(hashSetOf()) { it.id }
    return buildList {
        groups.forEach { add(ProviderRootItem.GroupItem(it)) }
        providers
            .filter { it.groupId == null || it.groupId !in groupIds }
            .forEach { add(ProviderRootItem.ProviderItem(it)) }
    }.sortedWith(
        compareBy<ProviderRootItem> { it.sortOrder }
            .thenBy { if (it is ProviderRootItem.GroupItem) 0 else 1 }
            .thenBy { it.id }
    )
}

internal fun ProviderRootItem.toOrderItem(): ProviderRootOrderItem =
    ProviderRootOrderItem(id = id, isGroup = this is ProviderRootItem.GroupItem)

internal fun ProviderRootItem.cellKey(): String =
    if (this is ProviderRootItem.GroupItem) "group_$id" else "provider_$id"
