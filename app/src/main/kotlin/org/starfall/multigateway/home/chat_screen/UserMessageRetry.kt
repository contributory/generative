package org.starfall.multigateway.home.chat_screen

import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.hasOtherRealVersions
import org.starfall.multigateway.shared.model.looksLikeErrorText

internal fun isErrorOnlyResponse(content: String): Boolean = looksLikeErrorText(content)

/** A failed or stopped response that can be retried from the preceding user bubble. */
private fun StoredMessage.isRetryableResponse(): Boolean =
    role == ChatRole.MODEL && !hasOtherRealVersions() &&
        (activeVersion.generationInterrupted || activeVersion.generationError != null ||
            isErrorOnlyResponse(content))

internal fun unansweredUserMessageIds(messages: List<StoredMessage>): Set<String> =
    messages.asReversed().takeWhile { it.role != ChatRole.MODEL }
        .filter { it.role == ChatRole.USER && !it.isQueued }.mapTo(mutableSetOf()) { it.id }
        .apply {
            if (messages.lastOrNull()?.isRetryableResponse() != true) return@apply
            messages.dropLast(1).lastOrNull { it.role == ChatRole.USER && !it.isQueued }
                ?.let { add(it.id) }
        }

/**
 * Reset the conversation at the selected user message and insert a fresh answer placeholder.
 *
 * A resend is a branch reset, rather than another answer version: the selected user version is
 * made the only version and every message after it is discarded.
 */
internal fun prepareUserMessageRetry(
    conversation: Conversation,
    messageId: String,
    responseId: String,
    version: MessageVersion
): Conversation? {
    val index = conversation.messages.indexOfFirst { it.id == messageId }
    val user = conversation.messages.getOrNull(index)?.takeIf {
        it.role == ChatRole.USER && !it.isQueued
    } ?: return null
    val resetUser = user.copy(
        versions = listOf(user.activeVersion),
        activeVersionIndex = 0,
        isQueued = false
    )
    val response = StoredMessage(responseId, ChatRole.MODEL, listOf(version))
    return conversation.copy(
        messages = conversation.messages.take(index) + resetUser + response,
        summary = conversation.summary?.takeUnless { summary ->
            conversation.messages.indexOfFirst { it.id == summary.throughMessageId } >= index
        },
        updatedAt = System.currentTimeMillis()
    )
}
