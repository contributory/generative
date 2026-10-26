package org.starfall.generative.features.home.chat.logic

import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.StoredMessage

/**
 * Replaces a conversation's message values and advances the structural epoch only when row identity,
 * order, role, or queued status changes. Text and version edits leave the epoch unchanged.
 */
internal fun Conversation.withUpdatedMessageStructure(messages: List<StoredMessage>): Conversation =
    copy(
        messages = messages,
        structuralRevision = if (messages.sameStructureAs(this.messages)) structuralRevision else structuralRevision + 1L
    )

/** Explicit append/insert/remove boundary when the caller already knows the structure changed. */
internal fun Conversation.withStructuralMessageChange(messages: List<StoredMessage>): Conversation =
    copy(messages = messages, structuralRevision = structuralRevision + 1L)

private fun List<StoredMessage>.sameStructureAs(other: List<StoredMessage>): Boolean =
    size == other.size && indices.all { index ->
        val left = this[index]
        val right = other[index]
        left.id == right.id && left.role == right.role && left.isQueued == right.isQueued
    }
