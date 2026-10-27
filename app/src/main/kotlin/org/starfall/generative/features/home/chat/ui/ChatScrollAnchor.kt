package org.starfall.generative.features.home.chat.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.abs
import kotlin.math.roundToInt
import org.starfall.generative.features.home.chat.logic.SentMessageDetector
import org.starfall.generative.features.home.chat.logic.anchorTailSpacer

private const val MAX_WAIT_FRAMES = 90
private const val MAX_SETTLE_PASSES = 3
private const val SETTLE_TOLERANCE_PX = 2

/**
 * The scroll position of a just-sent user message: flush under the app bar. When a message is sent,
 * including a pending one that starts once the previous response ends, [ChatAnchorEffects] places it
 * there once; the user can drag it away and back. Only user messages get this: [anchorTailItem]
 * keeps the content tall enough to reach it, and AI messages scroll as before. A chat that is opened
 * ending with a user message is padded the same way (without a settle), so that message can be dragged
 * there too.
 *
 * Every other automatic scroll (following a streaming reply, jump to latest, opening a chat) must stay
 * out of the way while [settling], and must not use "scroll to the end of the content", which with the
 * spacer present would move the user message to the top.
 */
@Stable
internal class ChatScrollAnchor {
    val detector = SentMessageDetector()

    /** The sent message that is currently anchored, or null when no message was sent in this chat. */
    var messageId by mutableStateOf<String?>(null)
        private set

    /** Bumped for every new anchor; keys the one effect that scrolls, so message updates cannot cancel it. */
    var request by mutableIntStateOf(0)
        private set

    /** True from the send until the message is in place, or until the user takes over. */
    var settling by mutableStateOf(false)
        private set

    // Natural (unpadded) height of each item, written while the item is measured. Plain storage plus a
    // version that only the newest item's measure reads, so a prefix change re-measures just that item.
    private val naturalHeights = HashMap<String, Int>()
    private var heightsVersion by mutableIntStateOf(0)

    fun recordHeight(id: String, px: Int, notify: Boolean) {
        if (naturalHeights.put(id, px) != px && notify) heightsVersion += 1
    }

    /** Minimum height of the newest item: one visible area minus the items of the turn before it. */
    fun newestItemMinHeight(visiblePx: Int, precedingIds: List<String>): Int {
        heightsVersion // observed by the measuring item
        return anchorTailSpacer(visiblePx, precedingIds.sumOf { naturalHeights[it] ?: 0 })
    }

    fun start(id: String) {
        messageId = id
        request += 1
        settling = true
    }

    fun finishSettling(request: Int) {
        if (request == this.request) settling = false
    }

    /** The user touched the list: whatever was being applied is cancelled by the scroll mutex already. */
    fun release() {
        settling = false
    }

    fun end() {
        messageId = null
        settling = false
        naturalHeights.clear()
    }
}

/**
 * Applies a [ChatScrollAnchor.start] request. Everything that scrolls for it lives in one effect keyed
 * only by the request, so message and streaming updates cannot cancel it half way.
 *
 * @param itemIndexOf list index of a message id, or -1
 * @param itemCount number of list items once the tail spacer is present
 */
@Composable
internal fun ChatAnchorEffects(
    anchor: ChatScrollAnchor,
    listState: LazyListState,
    itemIndexOf: (String) -> Int,
    itemCount: () -> Int
) {
    val currentIndexOf by rememberUpdatedState(itemIndexOf)
    val currentItemCount by rememberUpdatedState(itemCount)

    LaunchedEffect(anchor.request) {
        val id = anchor.messageId ?: return@LaunchedEffect
        val request = anchor.request
        try {
            // The message and the spacer reach the list a frame or two after the send.
            var frames = 0
            while (frames++ < MAX_WAIT_FRAMES &&
                !(currentIndexOf(id) >= 0 && listState.layoutInfo.totalItemsCount >= currentItemCount())
            ) androidx.compose.runtime.withFrameNanos { }

            // The first pass animates to the message; the next passes correct for sizes that were
            // still unknown (the reply grows as it is measured).
            repeat(MAX_SETTLE_PASSES) {
                val index = currentIndexOf(id)
                if (index < 0) return@LaunchedEffect
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    listState.animateScrollToItem(index, 0)
                } else {
                    val delta = item.offset
                    if (abs(delta) <= SETTLE_TOLERANCE_PX) return@LaunchedEffect
                    listState.animateScrollBy(delta.toFloat())
                }
                androidx.compose.runtime.withFrameNanos { }
            }
        } finally {
            anchor.finishSettling(request)
        }
    }
}

/**
 * Measures an item and records its natural height; the newest item is then padded to at least
 * [ChatScrollAnchor.newestItemMinHeight] when [padNewest] is set. The caller sets it for a just-sent
 * message and for a chat that ends with a user message, so that message can be dragged under the
 * app bar. Because the padding is decided in the same measure pass as the item's own size, content
 * that shrinks (a placeholder replaced by short text) never lets the list clamp its scroll position
 * the way a separate spacer, updated a frame later, would.
 */
internal fun Modifier.anchorTailItem(
    anchor: ChatScrollAnchor,
    id: String,
    isNewest: Boolean,
    precedingIds: List<String>,
    visiblePx: Int,
    padNewest: Boolean
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    anchor.recordHeight(id, placeable.height, notify = !isNewest)
    val height = if (isNewest && padNewest) {
        maxOf(placeable.height, anchor.newestItemMinHeight(visiblePx, precedingIds))
    } else placeable.height
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/**
 * Adds scroll room to the newest item in a reverse-layout chat list.
 *
 * The list starts at the bottom, so the blank area goes *below* the bubble on screen (at the start
 * edge of the list). A newest user message grows to the full visible height, so it sits right under
 * the app bar and can be dragged back there, and it reports its natural height through
 * [onNaturalHeight]. The reply that follows passes [minItemHeightPx] (visible height minus that
 * message) so the content from the user message to the end keeps the same total height and the user
 * message does not move when the reply starts; the spacer shrinks to zero as the reply grows.
 */
internal fun Modifier.reverseTailScrollPadding(
    visiblePx: Int,
    minItemHeightPx: Int? = null,
    onNaturalHeight: ((Int) -> Unit)? = null
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    onNaturalHeight?.invoke(placeable.height)
    val height = maxOf(placeable.height, minItemHeightPx ?: visiblePx)
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/**
 * Brings the bottom edge of the item at [index] into view, and does nothing when it already is.
 * Unlike scrolling to the end of the content, this never reaches the top position through the
 * anchor spacer.
 */
internal suspend fun LazyListState.revealItemBottom(index: Int) {
    var info = layoutInfo
    var item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        // Not composed: bring it in, which also gives it a measured size.
        scrollToItem(index)
        androidx.compose.runtime.withFrameNanos { }
        info = layoutInfo
        item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }
    val remaining = item.offset + item.size - (info.viewportEndOffset - info.afterContentPadding)
    if (remaining > 0) scrollBy(remaining.toFloat())
}
