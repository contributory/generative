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
import org.starfall.generative.features.home.chat.logic.SentMessageDetector
import org.starfall.generative.features.home.chat.logic.anchorTailSpacer

private const val MAX_WAIT_FRAMES = 90
private const val MAX_SETTLE_PASSES = 3
private const val SETTLE_TOLERANCE_PX = 2

/** Pins a newly sent user message under the app bar until settled or manually interrupted. */
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
                if (!anchor.settling) return@LaunchedEffect
                val index = currentIndexOf(id)
                if (index < 0) return@LaunchedEffect
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    listState.animateScrollToItem(index, 0)
                } else {
                    val delta = item.offset - info.beforeContentPadding
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

/** Pad *after* the newest user bubble, never above it or between it and a reply. */
internal fun Modifier.tailScrollPadding(
    visiblePx: Int,
    onNaturalHeight: ((Int) -> Unit)? = null
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    onNaturalHeight?.invoke(placeable.height)
    layout(placeable.width, maxOf(placeable.height, visiblePx)) {
        placeable.placeRelative(0, 0)
    }
}

/** Keeps the free space *after* the newest AI reply until it grows past the viewport. */
@Stable
internal class TailTurnAnchor(private val fallbackUserHeightPx: Int) {
    private val userHeights = HashMap<String, Int>()
    private var version by mutableIntStateOf(0)

    fun recordUser(id: String, px: Int) {
        if (userHeights.put(id, px) != px) version += 1
    }

    fun userHeight(id: String): Int {
        version // invalidate the last reply's measurement when the user message is measured
        return userHeights[id] ?: fallbackUserHeightPx
    }
}

/** Record the preceding user bubble without inserting blank space between it and the reply. */
internal fun Modifier.recordTurnUserHeight(anchor: TailTurnAnchor, id: String): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        anchor.recordUser(id, placeable.height)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

internal fun Modifier.replyTailPadding(
    anchor: TailTurnAnchor,
    userId: String,
    visiblePx: Int
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val remaining = anchorTailSpacer(visiblePx, anchor.userHeight(userId))
    layout(placeable.width, maxOf(placeable.height, remaining)) {
        placeable.placeRelative(0, 0)
    }
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
    val remaining = item.offset + item.size - info.viewportEndOffset
    if (remaining > 0) scrollBy(remaining.toFloat())
}
