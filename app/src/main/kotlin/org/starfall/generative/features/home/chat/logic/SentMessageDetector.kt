package org.starfall.generative.features.home.chat.logic

import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.StoredMessage

/**
 * Decides when a user message has been *sent*, i.e. has just entered conversation history.
 *
 * "Sent" is deliberately defined on history alone. A pending (queued) message lives in a separate
 * list, and it reaches history by two different routes: the tool loop hands the queued message over
 * with the same id, while the end-of-response drain builds a brand-new message with a new id. Comparing
 * history before and after covers both, and a plain send, without tracking queue ids or `isQueued`.
 *
 * Not thread-safe; call it from the UI event/effect thread only.
 */
internal class SentMessageDetector {
    private var knownIds: Set<String>? = null
    private var lastHistory: List<StoredMessage>? = null
    private var lastStructuralRevision: Long? = null
    internal var idScanCount: Long = 0L
        private set

    /** Compatibility helper for callers without an explicit structural epoch. */
    fun observe(history: List<StoredMessage>): String? {
        if (history === lastHistory) return null
        return observe(history, (lastStructuralRevision ?: -1L) + 1L)
    }

    /** The id of a user message that entered [history] since the previous structural revision, or null. */
    fun observe(history: List<StoredMessage>, structuralRevision: Long): String? {
        if (lastStructuralRevision == structuralRevision &&
            lastHistory?.isEmpty() == history.isEmpty()) return null
        lastStructuralRevision = structuralRevision
        lastHistory = history
        idScanCount += 1L
        val previous = knownIds
        knownIds = history.mapTo(HashSet(history.size * 2)) { it.id }
        // The first snapshot is the baseline: opening a chat or switching to one is not a send.
        // A chat that was empty has nothing above a new message to scroll against either.
        if (previous.isNullOrEmpty()) return null
        val added = history.filter { it.id !in previous }
        // A send adds the user message plus its answer placeholder. More than that is a bulk change
        // (reload, import, branch switch), which must not be mistaken for the user sending.
        if (added.isEmpty() || added.size > MAX_MESSAGES_PER_SEND) return null
        return added.lastOrNull { it.role == ChatRole.USER && !it.isQueued }?.id
    }

    private companion object {
        const val MAX_MESSAGES_PER_SEND = 2
    }
}
