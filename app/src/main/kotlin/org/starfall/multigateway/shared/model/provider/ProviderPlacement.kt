package org.starfall.multigateway.shared.model.provider

/** Commit a drag preview's membership and ordering in one local transaction. */
data class ProviderPlacement(
    val providerId: String,
    val groupId: String?,
    val rootOrder: List<ProviderRootOrderItem>,
    val groupOrders: Map<String, List<String>>
)
