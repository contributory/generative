package org.starfall.generative.home.chat.logic

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
 * Not thread-safe; call it from the composition/apply thread only.
 */
internal class SentMessageDetector {
    private var knownIds: Set<String>? = null
    private var lastHistory: List<StoredMessage>? = null

    /** The id of a user message that entered [history] since the previous call, or null. */
    fun observe(history: List<StoredMessage>): String? {
        if (history === lastHistory) return null
        lastHistory = history
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

/**
 * Minimum height of the newest item while a sent message is anchored, in px: the part of one visible
 * area that the items before it do not already fill.
 *
 * The list can only scroll as far as its content allows. Placing the message flush under the
 * app bar, and letting the user drag it back there, needs the content from the message to the end to
 * be at least one visible area tall. The reply starts out shorter than that, so
 * this fills the difference and shrinks to nothing as the reply grows; the total never changes, which
 * is why the list does not move while a reply streams.
 */
internal fun anchorTailSpacer(visibleHeight: Int, tailHeight: Int): Int =
    (visibleHeight - tailHeight).coerceAtLeast(0)
