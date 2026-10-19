package org.starfall.multigateway.home.drawer.logic

import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.ConversationListItem
import org.starfall.multigateway.shared.model.provider.ModelProvider

internal data class ConversationModelIcon(val image: String?, val name: String, val id: String)

/** Use the last displayed model reply, including its selected regenerated version. */
internal fun Conversation.lastModelIcon(providers: Map<String, ModelProvider>): ConversationModelIcon? {
    val version = messages.lastOrNull { it.role == ChatRole.MODEL && it.activeVersion.modelId.isNotBlank() }?.activeVersion
    val id = version?.modelId ?: modelId.takeIf { it.isNotBlank() } ?: return null
    val provider = providers[version?.providerId?.takeIf { it.isNotBlank() } ?: providerId]
    val config = provider?.config?.modelConfigs?.get(id)
    val name = config?.displayName?.takeIf { it.isNotBlank() }
        ?: version?.modelDisplayName?.takeIf { it.isNotBlank() }
        ?: id
    return ConversationModelIcon(config?.icon, name, id)
}

/** Metadata-only fallback used by the sidebar without loading the conversation history. */
internal fun ConversationListItem.lastModelIcon(providers: Map<String, ModelProvider>): ConversationModelIcon? {
    val id = modelId.takeIf { it.isNotBlank() } ?: return null
    val config = providers[providerId]?.config?.modelConfigs?.get(id)
    val name = config?.displayName?.takeIf { it.isNotBlank() } ?: id
    return ConversationModelIcon(config?.icon, name, id)
}
