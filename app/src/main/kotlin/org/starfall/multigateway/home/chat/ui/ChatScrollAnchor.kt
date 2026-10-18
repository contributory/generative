package org.starfall.multigateway.home.chat.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import org.starfall.multigateway.home.chat.logic.SentMessageDetector
import org.starfall.multigateway.home.chat.logic.anchorTailSpacer
import org.starfall.multigateway.home.chat.logic.sentMessageScrollOffset

/** Key of the blank item that keeps the list scrollable far enough for a sent message to be anchored. */
internal const val ANCHOR_SPACER_KEY = "anchor-tail-spacer"

private const val MAX_WAIT_FRAMES = 90
private const val MAX_SETTLE_PASSES = 3
private const val SETTLE_TOLERANCE_PX = 2

/**
 * The scroll position of a just-sent user message. The chat has two deliberate positions for it:
 *
 * 1. **Middle** (the default). When a message is sent, including a pending one that starts once the
 *    previous response ends, it is placed in the middle of the chat area so its reply has room below.
 *    Only this class's settle routine puts it there.
 * 2. **Top**, flush with the app bar. Only the user gets it there, by dragging up. [ChatAnchorTailSpacer]
 *    keeps the content tall enough for that drag, and nothing automatic scrolls to it.
 *
 * Every other automatic scroll (following a streaming reply, jump to latest, opening a chat) must stay
 * out of the way while [settling] and must not use "scroll to the end of the content", which with the
 * spacer present is the top position.
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

    /** Last measured height of each item, kept while it is scrolled out of the visible window. */
    val heights = mutableStateMapOf<String, Int>()

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
        heights.clear()
    }
}

/** Height of the chat area (between the app bar and the input bar) in the layout's own coordinates. */
private fun LazyListLayoutInfo.visibleContentHeight(): Int =
    (viewportEndOffset - afterContentPadding) - beforeContentPadding

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

    // Items scrolled out of the window are not in layoutInfo, so their heights are remembered here.
    LaunchedEffect(anchor, anchor.messageId) {
        if (anchor.messageId == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }.collect { info ->
            for (item in info.visibleItemsInfo) {
                val key = item.key as? String ?: continue
                if (key != ANCHOR_SPACER_KEY && anchor.heights[key] != item.size) anchor.heights[key] = item.size
            }
        }
    }

    LaunchedEffect(anchor.request) {
        val id = anchor.messageId ?: return@LaunchedEffect
        val request = anchor.request
        try {
            // The message and the spacer reach the list a frame or two after the send.
            var frames = 0
            while (frames++ < MAX_WAIT_FRAMES &&
                !(currentIndexOf(id) >= 0 && listState.layoutInfo.totalItemsCount >= currentItemCount())
            ) androidx.compose.runtime.withFrameNanos { }

            // The first pass animates towards the middle assuming a short message. The message is
            // measured on arrival, so the next pass corrects by its real height; the last one verifies.
            repeat(MAX_SETTLE_PASSES) {
                val index = currentIndexOf(id)
                if (index < 0) return@LaunchedEffect
                val info = listState.layoutInfo
                val visible = info.visibleContentHeight()
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    listState.animateScrollToItem(index, sentMessageScrollOffset(visible, 0))
                } else {
                    val wantedTop = -sentMessageScrollOffset(visible, item.size)
                    val delta = (item.offset - info.beforeContentPadding) - wantedTop
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

/** Blank item after the newest content; see [anchorTailSpacer]. Reads heights here so only it recomposes. */
@Composable
internal fun ChatAnchorTailSpacer(anchor: ChatScrollAnchor, tailIds: List<String>, visibleHeight: Dp) {
    val density = LocalDensity.current
    val tailPx = tailIds.sumOf { anchor.heights[it] ?: 0 }
    val spacerPx = anchorTailSpacer(with(density) { visibleHeight.roundToPx() }, tailPx)
    Spacer(Modifier.height(with(density) { spacerPx.toDp() }))
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
