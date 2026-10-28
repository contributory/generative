package org.starfall.generative.features.home.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.StoredMessage

internal class ConversationMinimapSideBarState {
    var isOpen by mutableStateOf(false)
    var boundsInRoot: Rect? = null
    var isInteracting by mutableStateOf(false)
    var interactionRevision by mutableIntStateOf(0)

    fun markInteraction() { interactionRevision++ }
}

private const val MINIMAP_IDLE_TIMEOUT_MS = 6_000L

/** Observe taps and drags on the chat ancestor without intercepting the underlying gestures. */
@Composable
internal fun Modifier.dismissMinimapOnOutsideTap(state: ConversationMinimapSideBarState): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onGloballyPositioned { coordinates = it }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val point = coordinates?.takeIf { it.isAttached }?.localToRoot(down.position)
            val outside = state.isOpen && point != null && state.boundsInRoot?.contains(point) == false
            if (outside) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                        change.changedToUpIgnoreConsumed()
                    ) {
                        state.isOpen = false
                        break
                    }
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                }
            }
        }
    }
}

/** Small right-edge navigator. It never composes a second copy of the chat's Markdown/media. */
@Composable
internal fun ConversationMinimapSideBar(
    conversationKey: String,
    messages: List<StoredMessage>,
    listState: LazyListState,
    topObscuredPx: Int,
    bottomObscuredPx: Int,
    onOpen: () -> Unit,
    onNavigate: () -> Unit,
    state: ConversationMinimapSideBarState,
    tailSpacerPx: (String) -> Int = { 0 },
    modifier: Modifier = Modifier
) {
    val latestOnOpen by rememberUpdatedState(onOpen)
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    val openLabel = stringResource(R.string.chat_minimap_open)
    val closeLabel = stringResource(R.string.chat_minimap_close)
    LaunchedEffect(messages.isEmpty()) { if (messages.isEmpty()) state.isOpen = false }
    LaunchedEffect(state.isOpen, state.isInteracting, state.interactionRevision) {
        if (state.isOpen && !state.isInteracting) {
            delay(MINIMAP_IDLE_TIMEOUT_MS)
            state.isOpen = false
        }
    }
    BackHandler(enabled = state.isOpen) { state.isOpen = false }
    val hasMessages = messages.isNotEmpty()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val railWidth = minOf(44.dp, maxWidth * 0.30f)
        val topInset = with(LocalDensity.current) { topObscuredPx.toDp() }.coerceIn(0.dp, maxHeight)
        val bottomInset = with(LocalDensity.current) { bottomObscuredPx.toDp() }
            .coerceIn(0.dp, maxHeight - topInset)
        if (maxHeight - topInset <= 0.dp) return@BoxWithConstraints
        // The edge gesture target extends below the app bar to the bottom of the screen.
        Box(Modifier.fillMaxSize().padding(top = topInset).clipToBounds()) {
            if (!state.isOpen || !hasMessages) {
                // Keep the original 24dp edge target. Claim its leftward swipe in the
                // Initial pass, before the surrounding Pager can start dragging. Keep this
                // guard for empty chats too; rightward drags still belong to the drawer.
                Box(
                    Modifier.align(AbsoluteAlignment.CenterRight)
                        .width(24.dp).fillMaxHeight()
                        .testTag("chat-minimap-edge")
                        .then(if (hasMessages) {
                            Modifier.clickable(onClickLabel = openLabel) { latestOnOpen(); state.isOpen = true }
                                .semantics { contentDescription = openLabel }
                        } else Modifier)
                        .pointerInput(conversationKey, threshold, hasMessages) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var distance = 0f
                                var claimed = false
                                var rejected = false
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val dx = change.position.x - change.previousPosition.x
                                    distance += dx
                                    if (!claimed && !rejected && kotlin.math.abs(distance) > viewConfiguration.touchSlop) {
                                        claimed = distance < 0f && kotlin.math.abs(change.position.y - down.position.y) < kotlin.math.abs(distance)
                                        rejected = !claimed
                                    }
                                    if (claimed && change.pressed) change.consume()
                                } while (change.pressed)
                                if (hasMessages && claimed && distance < -threshold) {
                                    latestOnOpen()
                                    state.isOpen = true
                                }
                            }
                        },
                    contentAlignment = AbsoluteAlignment.CenterRight
                ) {
                    Box(Modifier.width(24.dp).height(160.dp).systemGestureExclusion())
                }
            }
        }
        // Anchor the rail below the app bar; input resizing only limits its available height.
        Box(Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset).clipToBounds()) {
            AnimatedVisibility(
                visible = state.isOpen && hasMessages,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(AbsoluteAlignment.TopRight)
            ) {
                DisposableEffect(state) { onDispose { state.boundsInRoot = null } }
                val borderColor = MaterialTheme.colorScheme.outlineVariant
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = AbsoluteRoundedCornerShape(topLeft = 12.dp, bottomLeft = 12.dp),
                    modifier = Modifier.width(railWidth)
                        .heightIn(max = 480.dp).fillMaxHeight()
                        .testTag("chat-minimap-sidebar")
                        .drawWithCache {
                            val stroke = 1.dp.toPx()
                            val inset = stroke / 2f
                            val radius = minOf(12.dp.toPx(), size.height / 2f, size.width)
                            val border = Path().apply {
                                moveTo(size.width, inset)
                                lineTo(radius, inset)
                                quadraticTo(inset, inset, inset, radius)
                                lineTo(inset, size.height - radius)
                                quadraticTo(inset, size.height - inset, radius, size.height - inset)
                                lineTo(size.width, size.height - inset)
                            }
                            onDrawWithContent {
                                drawContent()
                                drawPath(border, borderColor, style = Stroke(stroke))
                            }
                        }
                        .onGloballyPositioned { state.boundsInRoot = it.boundsInRoot() }
                        // A finger held on the minimap is active interaction, even if the drag lasts over 6s.
                        .pointerInput(state) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                state.isInteracting = true
                                try {
                                    var pressed: Boolean
                                    do {
                                        pressed = awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.any { it.pressed }
                                    } while (pressed)
                                } finally {
                                    state.isInteracting = false
                                    state.markInteraction()
                                }
                            }
                        }
                        .semantics { dismiss(closeLabel) { state.isOpen = false; true } }
                        .pointerInput(conversationKey, threshold) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onDragEnd = { if (distance > threshold) state.isOpen = false },
                                onDragCancel = { distance = 0f },
                                onHorizontalDrag = { change, dx -> change.consume(); distance += dx }
                            )
                        }
                ) {
                    Box(Modifier.padding(horizontal = 5.dp, vertical = 8.dp)) {
                        key(conversationKey) {
                            ConversationMinimap(
                                messages, listState, topObscuredPx, bottomObscuredPx, tailSpacerPx,
                                onNavigate = {
                                    // Accessibility navigation has no touch gesture to restart the idle timer.
                                    if (!state.isInteracting) state.markInteraction()
                                    onNavigate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationMinimap(
    messages: List<StoredMessage>,
    listState: LazyListState,
    topObscuredPx: Int,
    bottomObscuredPx: Int,
    tailSpacerPx: (String) -> Int,
    onNavigate: () -> Unit,
    modifier: Modifier
) {
    val latestTailSpacer by rememberUpdatedState(tailSpacerPx)
    val commands = remember { Channel<MinimapCommand>(Channel.UNLIMITED) }
    DisposableEffect(commands) { onDispose { commands.close() } }
    val latestMessages by rememberUpdatedState(messages)
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current.density
    val heights = remember(width) { mutableStateMapOf<String, Int>() }
    val entries = remember(messages, width, density) {
        messages.map { message ->
            val text = message.content.ifBlank { message.reasoningContent.orEmpty() }.take(8_000)
            val lines = text.lineSequence().take(32).map { it.length.coerceIn(4, 60) / 60f }.toList()
            val media = message.files.size + message.activeVersion.toolActivity.sumOf { it.files.size }
            // Heights of unvisited messages are estimates; measured lazy items replace them below.
            val charsPerLine = (width / 8).coerceAtLeast(20)
            val estimatedLines = text.lineSequence().sumOf { line -> maxOf(1, (line.length + charsPerLine - 1) / charsPerLine) }
            MinimapPreview(message.id, message.role == ChatRole.USER, lines, media > 0,
                (48f + estimatedLines * 20f + media * 120f) * density)
        }
    }
    val entriesById = remember(entries) { entries.associateBy { it.id } }
    LaunchedEffect(listState, entries, heights) {
        val ids = entries.map { it.id }.toSet()
        heights.keys.toList().filterNot { it in ids }.forEach { heights.remove(it) }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key to it.size } }
            .collect { visible ->
                visible.forEach { (key, size) ->
                    if (key is String && key in ids) {
                        val natural = size - latestTailSpacer(key)
                        if (natural > 0) heights[key] = natural
                    }
                }
            }
    }
    // Measured heights replace estimates as soon as they exist. Estimates that are still left are
    // scaled by how far the measured ones deviated, so one unseen long reply cannot dominate the rail
    // and make the finger stall in one stretch and leap over another.
    val mapHeights = run {
        var measuredSum = 0.0
        var estimatedSum = 0.0
        var measuredCount = 0
        entries.forEach { entry ->
            heights[entry.id]?.let { measuredSum += it; estimatedSum += entry.estimatedHeight; measuredCount++ }
        }
        val scale = if (measuredCount >= 2 && estimatedSum > 0.0)
            (measuredSum / estimatedSum).toFloat().coerceIn(0.25f, 4f) else 1f
        entries.map { it.id to (heights[it.id]?.toFloat() ?: (it.estimatedHeight * scale)) }
    }
    val spans = remember(mapHeights) { minimapSpans(mapHeights) }
    val contentHeight = mapHeights.sumOf { it.second.toDouble() }.toFloat()
    val displayedSpans = spans
    val layout = listState.layoutInfo
    val readableStart = layout.viewportStartOffset + topObscuredPx
    val readableEnd = layout.viewportEndOffset - bottomObscuredPx
    val mappedViewport = minimapViewport(
        displayedSpans,
        layout.visibleItemsInfo.mapNotNull { item -> (item.key as? String)?.let { MinimapVisibleItem(it, item.offset, (item.size - tailSpacerPx(it)).coerceAtLeast(0)) } },
        readableStart,
        readableEnd
    )
    // The rail is a plain scrollbar. One continuous value, scroll in 0..1, maps linearly to the thumb
    // position and to a pixel offset in the chat; no message boundary takes part in the arithmetic.
    var railPx by remember { mutableIntStateOf(0) }
    val minThumbPx = with(LocalDensity.current) { 28.dp.toPx() }
    val viewportPx = (readableEnd - readableStart).coerceAtLeast(0).toFloat()
    val contentSafe = contentHeight.coerceAtLeast(1f)
    val scrollRangePx = (contentHeight - viewportPx).coerceAtLeast(0f)
    val thumbFraction = maxOf(viewportPx / contentSafe, if (railPx > 0) minThumbPx / railPx else 0f)
        .coerceIn(0f, 1f)
    val actualScroll = if (scrollRangePx <= 0f) 0f
        else ((mappedViewport?.start ?: 0f) * contentSafe / scrollRangePx).coerceIn(0f, 1f)
    var draggingScroll by remember { mutableStateOf<Float?>(null) }
    val thumbTop = (draggingScroll ?: actualScroll) * (1f - thumbFraction)
    val metrics by rememberUpdatedState(MinimapMetrics(contentHeight, viewportPx, thumbFraction, actualScroll))
    val latestSpans by rememberUpdatedState(spans)
    val latestInsets by rememberUpdatedState(topObscuredPx to bottomObscuredPx)
    LaunchedEffect(listState, commands) {
        // One consumer applies every move in order, so a new command never cancels a scroll that is
        // between measuring an offscreen item and positioning it. Pixel scrolls are merged per frame.
        suspend fun naturalHeightOf(id: String): Int? =
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                ?.let { it.size - latestTailSpacer(id) }

        suspend fun jump(target: MinimapTarget) {
            val index = latestMessages.indexOfFirst { it.id == target.id }
            if (index < 0) return
            var itemHeight = naturalHeightOf(target.id) ?: heights[target.id]
            if (itemHeight == null) {
                listState.scrollToItem(index)
                itemHeight = naturalHeightOf(target.id)
            }
            if (itemHeight != null && itemHeight > 0) {
                heights[target.id] = itemHeight
                val info = listState.layoutInfo
                val start = info.viewportStartOffset + latestInsets.first
                val end = info.viewportEndOffset - latestInsets.second
                listState.scrollToItem(index, minimapScrollOffset(itemHeight, target.fraction, end - start, start))
            }
        }

        suspend fun scrollToEnd() {
            val lastIndex = latestMessages.lastIndex
            if (lastIndex < 0) return
            val lastId = latestMessages[lastIndex].id
            var itemHeight = naturalHeightOf(lastId) ?: heights[lastId]
            if (itemHeight == null) {
                listState.scrollToItem(lastIndex)
                itemHeight = naturalHeightOf(lastId)
            }
            if (itemHeight != null && itemHeight > 0) {
                val info = listState.layoutInfo
                val end = info.viewportEndOffset - latestInsets.second
                // Put the bottom edge of the newest message on the bottom of the readable area.
                listState.scrollToItem(lastIndex, itemHeight - end)
            }
        }

        var carried: MinimapCommand? = null
        while (true) {
            val command = carried ?: commands.receiveCatching().getOrNull() ?: break
            carried = null
            when (command) {
                is MinimapCommand.Jump -> jump(command.target)
                is MinimapCommand.Scroll -> {
                    var px = command.px
                    while (true) {
                        val next = commands.tryReceive().getOrNull() ?: break
                        if (next is MinimapCommand.Scroll) px += next.px else { carried = next; break }
                    }
                    listState.scrollBy(px)
                }
                MinimapCommand.ToStart -> listState.scrollToItem(0, 0)
                MinimapCommand.ToEnd -> scrollToEnd()
            }
        }
    }
    val jumpTo: (Float) -> Unit = { scroll ->
        latestOnNavigate()
        val m = metrics
        when {
            scroll <= 0f -> commands.trySend(MinimapCommand.ToStart)
            scroll >= 1f -> commands.trySend(MinimapCommand.ToEnd)
            else -> minimapTarget(
                latestSpans, (scroll * m.scrollRangePx + m.viewportPx / 2f) / m.contentHeight.coerceAtLeast(1f)
            )?.let { commands.trySend(MinimapCommand.Jump(it)) }
        }
    }
    val currentJumpTo by rememberUpdatedState(jumpTo)
    val description = stringResource(R.string.chat_minimap_navigate)
    val userColor = MaterialTheme.colorScheme.primary
    val modelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.primary
    Canvas(modifier.testTag("chat-minimap-map")
        .onSizeChanged { railPx = it.height }
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(actualScroll, 0f..1f)
            setProgress { currentJumpTo(it.coerceIn(0f, 1f)); true }
        }
        .pointerInput(Unit) {
            // Press anywhere to go there, hold and drag to scroll. A drag moves the chat by the
            // same share of its remaining distance as the finger moves of the rail's remaining
            // distance, so motion is proportional and both ends are reached exactly.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val railHeight = size.height.toFloat().coerceAtLeast(1f)
                val slop = viewConfiguration.touchSlop
                var dragging = false
                var scroll = 0f
                var position = 0f
                var lastY = down.position.y
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragging) {
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                currentJumpTo(fingerToScroll(down.position.y / railHeight, metrics.thumbFraction))
                                break
                            }
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (maxOf(abs(dx), abs(dy)) > slop) {
                                // A mostly horizontal swipe belongs to the rail's dismiss gesture.
                                if (abs(dy) < abs(dx)) return@awaitEachGesture
                                dragging = true
                                latestOnNavigate()
                                val m = metrics
                                val finger = down.position.y / railHeight
                                val thumbStart = (draggingScroll ?: m.actualScroll) * (1f - m.thumbFraction)
                                if (finger in thumbStart..(thumbStart + m.thumbFraction)) {
                                    scroll = m.actualScroll // grabbed the thumb: no jump
                                } else {
                                    scroll = fingerToScroll(finger, m.thumbFraction)
                                    currentJumpTo(scroll)
                                }
                                position = scroll * m.scrollRangePx
                                draggingScroll = scroll
                                lastY = change.position.y
                                change.consume()
                            }
                            continue
                        }
                        if (!change.pressed) { change.consume(); break }
                        val m = metrics
                        val deltaFraction = (change.position.y - lastY) / railHeight
                        lastY = change.position.y
                        val newScroll = (scroll + deltaFraction / (1f - m.thumbFraction).coerceAtLeast(0.0001f))
                            .coerceIn(0f, 1f)
                        val delta = newScroll - scroll
                        if (delta != 0f) {
                            when {
                                newScroll >= 1f -> { commands.trySend(MinimapCommand.ToEnd); position = m.scrollRangePx }
                                newScroll <= 0f -> { commands.trySend(MinimapCommand.ToStart); position = 0f }
                                else -> {
                                    val px = if (delta > 0f) {
                                        delta / (1f - scroll).coerceAtLeast(0.0001f) * (m.scrollRangePx - position).coerceAtLeast(0f)
                                    } else {
                                        delta / scroll.coerceAtLeast(0.0001f) * position.coerceAtLeast(0f)
                                    }
                                    position += px
                                    commands.trySend(MinimapCommand.Scroll(px))
                                }
                            }
                            scroll = newScroll
                            draggingScroll = scroll
                        }
                        change.consume()
                    }
                } finally {
                    draggingScroll = null
                }
            }
        }
    ) {
        displayedSpans.forEach { span ->
            val entry = entriesById[span.id] ?: return@forEach
            val y = span.start * size.height
            val spanHeight = (span.end - span.start) * size.height
            if (spanHeight <= 0f) return@forEach
            val height = (spanHeight - minOf(2.dp.toPx(), spanHeight * 0.2f)).coerceAtLeast(0f)
            val blockWidth = size.width * if (entry.user) 0.72f else 0.90f
            val left = if (entry.user) size.width - blockWidth else 0f
            val color = if (entry.user) userColor else modelColor
            val lineCount = minOf(entry.lines.size.coerceAtLeast(1), (height / 3.dp.toPx()).toInt().coerceAtLeast(1))
            val lineHeight = minOf(2.dp.toPx(), height / lineCount)
            repeat(lineCount) { line ->
                val fraction = entry.lines.getOrElse(line) { 0.6f }
                drawRoundRect(color.copy(alpha = 0.65f), Offset(left, y + line * height / lineCount),
                    Size(blockWidth * fraction.coerceAtLeast(0.12f), lineHeight), CornerRadius(1.dp.toPx()))
            }
            if (entry.hasMedia && height > 6.dp.toPx()) {
                drawRoundRect(color.copy(alpha = 0.25f), Offset(left, y + height * 0.5f),
                    Size(blockWidth, height * 0.5f), CornerRadius(2.dp.toPx()))
            }
        }
        val top = thumbTop * size.height
        val bottom = (top + thumbFraction * size.height).coerceAtMost(size.height)
        drawRect(highlight.copy(alpha = 0.12f), Offset(0f, top), Size(size.width, bottom - top))
        drawRect(highlight.copy(alpha = 0.8f), Offset(0f, top), Size(size.width, bottom - top), style = Stroke(1.dp.toPx()))
    }
}

private sealed interface MinimapCommand {
    class Jump(val target: MinimapTarget) : MinimapCommand
    class Scroll(val px: Float) : MinimapCommand
    data object ToStart : MinimapCommand
    data object ToEnd : MinimapCommand
}

private class MinimapMetrics(
    val contentHeight: Float, val viewportPx: Float, val thumbFraction: Float, val actualScroll: Float
) {
    val scrollRangePx: Float get() = (contentHeight - viewportPx).coerceAtLeast(0f)
}

/** Scroll value (0..1) whose thumb is centered on the finger, clamped so the ends stay reachable. */
private fun fingerToScroll(finger: Float, thumbFraction: Float): Float =
    ((finger - thumbFraction / 2f) / (1f - thumbFraction).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)

private data class MinimapPreview(
    val id: String, val user: Boolean, val lines: List<Float>, val hasMedia: Boolean, val estimatedHeight: Float
)
