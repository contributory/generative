package org.starfall.generative.shared.model

/**
 * The metadata needed to render and sort the conversation list.
 *
 * This is deliberately separate from [Conversation]. In particular, it does not
 * contain message bodies or summary content, so callers can observe the list
 * without loading every conversation history.
 */
data class ConversationListItem(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tokenCount: Int?,
    val providerId: String,
    val modelId: String,
    val profileId: String?,
    val reasoningEffort: String?,
    val hasMessages: Boolean,
    val hasSummary: Boolean,
    val hasMigrationIssue: Boolean = false
)
