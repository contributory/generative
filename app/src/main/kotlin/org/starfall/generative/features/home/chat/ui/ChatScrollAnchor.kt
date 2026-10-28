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
private const val MAX_SETTLE_PASSES = 8
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
    itemCount: () -> Int,
    measuredUserHeight: (String) -> Boolean = { true }
) {
    val currentIndexOf by rememberUpdatedState(itemIndexOf)
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentHeightReady by rememberUpdatedState(measuredUserHeight)

    LaunchedEffect(anchor.request) {
        val id = anchor.messageId ?: return@LaunchedEffect
        val request = anchor.request
        try {
            // The message and the spacer reach the list a frame or two after the send.
            var frames = 0
            while (frames++ < MAX_WAIT_FRAMES &&
                !(currentIndexOf(id) >= 0 && listState.layoutInfo.totalItemsCount >= currentItemCount())
            ) androidx.compose.runtime.withFrameNanos { }

            // A just-appended reply initially uses an estimated user-bubble height. Keep
            // correcting until the real bubble is measured and its tail spacer has remeasured;
            // otherwise the final 2-3 lines of needed scroll can be left unapplied.
            var alignedFrames = 0
            repeat(MAX_SETTLE_PASSES) {
                if (!anchor.settling) return@LaunchedEffect
                val index = currentIndexOf(id)
                if (index < 0) return@LaunchedEffect
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    alignedFrames = 0
                    listState.animateScrollToItem(index, 0)
                } else {
                    // Offset 0 is the content start below the existing AppBar top padding.
                    val delta = item.offset
                    if (abs(delta) > SETTLE_TOLERANCE_PX) {
                        alignedFrames = 0
                        listState.animateScrollBy(delta.toFloat())
                    } else if (currentHeightReady(id) && ++alignedFrames >= 2) {
                        return@LaunchedEffect
                    }
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
    onNaturalHeight: ((Int) -> Unit)? = null,
    onSpacer: ((Int) -> Unit)? = null
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    onNaturalHeight?.invoke(placeable.height)
    val height = maxOf(placeable.height, visiblePx)
    onSpacer?.invoke(height - placeable.height)
    layout(placeable.width, height) {
        placeable.placeRelative(0, 0)
    }
}

/** Tracks the preceding user's height so the latest AI reply has scroll room below it. */
@Stable
internal class TailTurnAnchor(private val fallbackUserHeightPx: Int) {
    private val userHeights = HashMap<String, Int>()
    private var version by mutableIntStateOf(0)

    fun recordUser(id: String, px: Int) {
        if (userHeights.put(id, px) != px) version += 1
    }

    fun hasMeasured(id: String): Boolean = id in userHeights

    fun userHeight(id: String?): Int {
        version // invalidate the last reply's measurement when the user message is measured
        return if (id == null) fallbackUserHeightPx else userHeights[id] ?: fallbackUserHeightPx
    }
}

/** Record the preceding user bubble without inserting blank space between it and the reply. */
internal fun Modifier.recordTurnUserHeight(anchor: TailTurnAnchor, id: String): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        anchor.recordUser(id, placeable.height)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

internal fun replyTailHeight(naturalHeight: Int, visiblePx: Int, userHeight: Int, bottomGapPx: Int): Int =
    maxOf(naturalHeight + bottomGapPx, anchorTailSpacer(visiblePx, userHeight))

/** Largest height the newest reply reached while it streamed; the item never shrinks below it afterwards. */
internal class ReplyHeightFloor {
    var px = 0
}

internal fun Modifier.replyTailPadding(
    anchor: TailTurnAnchor,
    userId: String?,
    visiblePx: Int,
    bottomGapPx: Int,
    heightFloor: ReplyHeightFloor? = null,
    recordFloor: Boolean = false,
    onSpacer: ((Int) -> Unit)? = null
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    // Retain the breathing room after generation finishes as well. Removing it on Stop or
    // completion would resize the last item and make LazyColumn jump back to an old position.
    var height = replyTailHeight(placeable.height, visiblePx, anchor.userHeight(userId), bottomGapPx)
    if (heightFloor != null) {
        // The loader disappears and the final Markdown re-lays out when a stream ends; a shorter
        // item would clamp the scroll position, so it keeps the height it had while streaming.
        if (recordFloor && height > heightFloor.px) heightFloor.px = height
        height = maxOf(height, heightFloor.px)
    }
    onSpacer?.invoke(height - placeable.height)
    layout(placeable.width, height) {
        placeable.placeRelative(0, 0)
    }
}

/** The last item includes invisible tail padding; only its real content is a scroll target. */
internal fun latestContentDistancePx(
    itemOffset: Int,
    itemHeight: Int,
    trailingSpacerPx: Int,
    viewportEndPx: Int,
    bottomGapPx: Int
): Int = itemOffset + itemHeight - trailingSpacerPx.coerceAtLeast(0) - viewportEndPx + bottomGapPx

/** Reveal the last message content, leaving its requested bottom gap visible rather than
 * scrolling through the invisible spacer (which can hide the answer behind the AppBar). */
internal suspend fun LazyListState.revealItemBottom(
    index: Int,
    trailingSpacerPx: () -> Int = { 0 },
    bottomGapPx: Int = 0
) {
    var info = layoutInfo
    var item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        scrollToItem(index)
        androidx.compose.runtime.withFrameNanos { }
        info = layoutInfo
        item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }
    val distance = latestContentDistancePx(
        item.offset, item.size, trailingSpacerPx(), info.viewportEndOffset, bottomGapPx
    )
    if (distance != 0) scrollBy(distance.toFloat())
}
