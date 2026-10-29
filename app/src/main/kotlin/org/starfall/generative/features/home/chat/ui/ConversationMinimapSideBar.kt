package org.starfall.generative.features.home.chat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.shared.model.StoredMessage
import kotlin.math.roundToInt
import kotlin.math.abs

/** Visibility is opened only by an inward swipe from the chat's right edge. */
internal class ConversationMinimapSideBarState {
    var isOpen by mutableStateOf(false)
    var isInteracting by mutableStateOf(false)
    var interactionRevision by mutableIntStateOf(0)

    fun markInteraction() { interactionRevision++ }
}

private const val MINIMAP_IDLE_TIMEOUT_MS = 1_500L

private val MinimapHandleWidth = 32.dp
private val MinimapTrackPadding = 12.dp
private val MinimapThumbMinHeight = 48.dp
private val MinimapBubbleHeight = 28.dp

/**
 * Observe the gesture on the chat list's parent, rather than covering its right edge with an
 * invisible hit target. Children keep receiving taps and vertical scrolling normally.
 * Claim only an inward horizontal swipe that started within the existing 32 dp thumb width.
 */
internal fun Modifier.minimapEdgeSwipe(state: ConversationMinimapSideBarState, enabled: Boolean = true): Modifier =
    pointerInput(state, enabled) {
        val edgeWidthPx = MinimapHandleWidth.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!enabled || state.isOpen || down.position.x < size.width - edgeWidthPx) return@awaitEachGesture
            var claimed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size != 1) break
                    val change = event.changes.first()
                    if (!change.pressed) break
                    if (claimed) {
                        change.consume()
                        continue
                    }
                    if (change.isConsumed) break
                    val movement = change.position - down.position
                    val horizontal = abs(movement.x)
                    val vertical = abs(movement.y)
                    if (vertical > viewConfiguration.touchSlop && vertical >= horizontal) break
                    if (horizontal > viewConfiguration.touchSlop) {
                        if (movement.x < 0f && horizontal > vertical) {
                            change.consume()
                            state.isOpen = true
                            state.isInteracting = true
                            claimed = true
                        } else break
                    }
                }
            } finally {
                if (claimed) {
                    state.isInteracting = false
                    state.markInteraction()
                }
            }
        }
    }

/**
 * A chat minimap opened by an inward swipe from the right edge. It fades after inactivity;
 * only the visible thumb handles vertical drags. Taps and ordinary chat scrolling never open it.
 * Position and thumb size come from the native lazy-list layout in turn units.
 */
@Composable
internal fun ConversationMinimapSideBar(
    conversationKey: String,
    messages: List<StoredMessage>,
    listState: LazyListState,
    topObscuredPx: Int,
    bottomObscuredPx: Int,
    onScrub: (towardsEnd: Boolean) -> Unit,
    state: ConversationMinimapSideBarState,
    modifier: Modifier = Modifier
) {
    val turns = remember(messages) { chatMessageTurns(messages) }
    val turnIds = remember(turns) { turns.map { it.first().id } }
    val turnIndices = remember(turnIds) { turnIds.withIndex().associate { it.value to it.index } }
    val latestCount by rememberUpdatedState(turns.size)
    val latestIds by rememberUpdatedState(turnIds)
    val latestScrub by rememberUpdatedState(onScrub)
    val description = stringResource(R.string.chat_minimap_navigate)
    val scope = rememberCoroutineScope()
    val minThumbPx = with(LocalDensity.current) { MinimapThumbMinHeight.toPx() }
    // While a finger holds the thumb, the finger (not the lagging layout) decides where it is drawn.
    var dragProgress by remember(conversationKey) { mutableStateOf<Float?>(null) }
    var dragHeight by remember(conversationKey) { mutableFloatStateOf(1f) }
    var dragLength by remember(conversationKey) { mutableFloatStateOf(1f) }
    val spanState = remember(turnIndices, listState, topObscuredPx, bottomObscuredPx) {
        derivedStateOf {
            val layout = listState.layoutInfo
            minimapVisibleSpan(turnIndices, layout.visibleItemsInfo.mapNotNull { item ->
                (item.key as? String)?.let { MinimapVisibleTurn(it, item.offset, item.size) }
            }, layout.viewportStartOffset + topObscuredPx, layout.viewportEndOffset - bottomObscuredPx)
        }
    }
    // Gestures outlive recompositions, so they always read the newest derived state holder.
    val latestSpanState by rememberUpdatedState(spanState)
    fun liveSpan(): MinimapSpan? = latestSpanState.value
    fun currentThumb(track: Float): MinimapThumb {
        val held = dragProgress
        return if (held != null) MinimapThumb(held, dragHeight)
        else minimapThumb(latestCount, liveSpan(), minThumbPx / track.coerceAtLeast(1f))
    }
    val bubbleTurn by remember {
        derivedStateOf {
            dragProgress?.let { minimapPosition(latestCount, dragLength, it)?.index?.plus(1)?.coerceAtMost(latestCount) }
        }
    }
    LaunchedEffect(conversationKey, turns.isEmpty()) { state.isOpen = false }
    LaunchedEffect(state.isOpen) {
        if (!state.isOpen) { dragProgress = null; state.isInteracting = false }
    }
    LaunchedEffect(state.isOpen, state.isInteracting, state.interactionRevision) {
        if (state.isOpen && !state.isInteracting) {
            delay(MINIMAP_IDLE_TIMEOUT_MS)
            state.isOpen = false
        }
    }
    val shown = state.isOpen && turns.isNotEmpty()
    val alpha = animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "minimap-alpha")
    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val top = with(density) { topObscuredPx.toDp() }.coerceIn(0.dp, maxHeight)
        val bottom = with(density) { bottomObscuredPx.toDp() }.coerceIn(0.dp, maxHeight - top)
        val railHeightPx = with(density) { (maxHeight - top - bottom).roundToPx() }.coerceAtLeast(0)
        val latestRail by rememberUpdatedState(railHeightPx)
        // Everything lives between the app bar and the composer, never over the send button.
        Box(Modifier.fillMaxSize().padding(top = top, bottom = bottom)) {
            // Drawing only: this layer has no pointer input, so it can never block a touch.
            if (turns.isNotEmpty()) Canvas(Modifier.fillMaxSize().testTag("chat-minimap-map")) {
                val visible = alpha.value
                if (visible <= 0.01f) return@Canvas
                val pad = MinimapTrackPadding.toPx()
                val track = (size.height - 2f * pad).coerceAtLeast(1f)
                val held = dragProgress != null
                val thumb = currentThumb(track)
                val centerX = size.width - 8.dp.toPx()
                val trackWidth = 4.dp.toPx()
                val thumbWidth = (if (held) 8.dp else 4.dp).toPx()
                if (held) drawRoundRect(trackColor.copy(alpha = 0.5f * visible),
                    Offset(centerX - trackWidth / 2f, pad), Size(trackWidth, track), CornerRadius(trackWidth / 2f))
                drawRoundRect(thumbColor.copy(alpha = (if (held) 1f else 0.7f) * visible),
                    Offset(centerX - thumbWidth / 2f, pad + thumb.top * track),
                    Size(thumbWidth, thumb.height * track), CornerRadius(thumbWidth / 2f))
            }
            if (shown) {
                // The touch target is exactly the thumb, placed where it is drawn.
                Box(Modifier.align(AbsoluteAlignment.TopRight)
                    .layout { measurable, _ ->
                        val width = MinimapHandleWidth.roundToPx()
                        val pad = MinimapTrackPadding.roundToPx()
                        val track = (latestRail - 2 * pad).coerceAtLeast(1)
                        val thumb = currentThumb(track.toFloat())
                        val height = (thumb.height * track).roundToInt().coerceIn(1, track)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, latestRail) {
                            placeable.place(0, pad + (thumb.top * track).roundToInt())
                        }
                    }
                    .testTag("chat-minimap-thumb")
                    .semantics {
                        contentDescription = description
                        val current = (liveSpan()?.start ?: 0f).toInt()
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current.toFloat(), 0f..(turns.size - 1).coerceAtLeast(0).toFloat(),
                            (turns.size - 2).coerceAtLeast(0))
                        setProgress { value ->
                            if (!value.isFinite()) false else {
                                val index = value.toInt().coerceIn(0, (latestCount - 1).coerceAtLeast(0))
                                latestScrub(index >= current)
                                scope.launch { listState.scrollToItem(index) }
                                state.markInteraction()
                                true
                            }
                        }
                    }
                    // detectVerticalDragGestures only takes a gesture once it moves vertically past
                    // touch slop, so a horizontal swipe that starts on the thumb is left to others.
                    .pointerInput(conversationKey, state, listState) {
                        var travel = 1f
                        var count = 0
                        var length = 1f
                        var direction: Boolean? = null
                        fun scrub(progress: Float) {
                            val previous = dragProgress ?: progress
                            if (progress != previous || direction == null) {
                                val towardsEnd = progress >= previous
                                if (direction != towardsEnd) { direction = towardsEnd; latestScrub(towardsEnd) }
                            }
                            dragProgress = progress
                            val target = minimapPosition(count, length, progress) ?: return
                            // Only an on-screen turn has a measured height; for any other turn the
                            // target is its start rather than a guess.
                            val size = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == latestIds.getOrNull(target.index) }?.size ?: 0
                            scope.launch {
                                listState.scrollToItem(target.index, (target.fraction * size).roundToInt())
                            }
                        }
                        fun finish() {
                            dragProgress = null
                            state.isInteracting = false
                            state.markInteraction()
                        }
                        detectVerticalDragGestures(
                            onDragStart = {
                                count = latestCount
                                val track = (latestRail - 2f * MinimapTrackPadding.toPx()).coerceAtLeast(1f)
                                length = (liveSpan()?.length ?: count.toFloat()).coerceIn(0f, count.toFloat())
                                val thumb = currentThumb(track)
                                travel = (track * (1f - thumb.height)).coerceAtLeast(1f)
                                dragProgress = thumb.progress
                                dragHeight = thumb.height
                                dragLength = length
                                direction = null
                                state.isInteracting = true
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                if (count > 0) scrub(((dragProgress ?: 0f) + amount / travel).coerceIn(0f, 1f))
                            }
                        )
                    })
            }
            // The turn counter floats to the left of the thumb while it is held.
            val turn = bubbleTurn
            if (turn != null && shown) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(14.dp), shadowElevation = 2.dp,
                    modifier = Modifier.align(AbsoluteAlignment.TopRight).offset {
                        val pad = MinimapTrackPadding.roundToPx()
                        val track = (railHeightPx - 2 * pad).coerceAtLeast(1)
                        val center = pad + ((dragProgress ?: 0f) * (1f - dragHeight) + dragHeight / 2f) * track
                        IntOffset(-(MinimapHandleWidth + 8.dp).roundToPx(),
                            (center - MinimapBubbleHeight.toPx() / 2f).roundToInt())
                    }.testTag("chat-minimap-bubble")) {
                    Box(Modifier.height(MinimapBubbleHeight).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text("$turn / $latestCount", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
