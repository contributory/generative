package org.starfall.multigateway.home.chat.logic

import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.StoredMessage

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
 * The `scrollOffset` for `LazyListState.scrollToItem` that places a just-sent message at the first of
 * the two scroll positions: its top at the vertical middle of the visible chat area, so the other half
 * is free for the reply. A negative offset pushes the item *down* from the top of the content area.
 *
 * A message taller than half the area cannot keep its top at the middle without hiding its end, so
 * it is aligned by its bottom edge instead (this also covers a message taller than the whole area).
 *
 * @param visibleHeight height of the chat area between the app bar and the input bar, in px
 * @param messageHeight measured height of the sent message, in px
 */
internal fun sentMessageScrollOffset(visibleHeight: Int, messageHeight: Int): Int =
    -minOf(visibleHeight / 2, visibleHeight - messageHeight)

/**
 * Blank space to keep after the newest content while a sent message is anchored, in px.
 *
 * The list can only scroll as far as its content allows. Placing the message in the middle, and
 * letting the user drag it up to the app bar (the second position), both need the content from the
 * message to the end to be at least one visible area tall. The reply starts out shorter than that, so
 * this fills the difference and shrinks to nothing as the reply grows; the total never changes, which
 * is why the list does not move while a reply streams.
 */
internal fun anchorTailSpacer(visibleHeight: Int, tailHeight: Int): Int =
    (visibleHeight - tailHeight).coerceAtLeast(0)
