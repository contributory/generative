package org.starfall.generative.features.home.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.StoredMessage

internal class ConversationMinimapSideBarState {
    var isOpen by mutableStateOf(false)
    var boundsInRoot: Rect? = null
}

/** Observe taps on the chat ancestor so dismissing the rail does not block underlying controls. */
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
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    if (change.changedToUpIgnoreConsumed()) {
                        state.isOpen = false
                        break
                    }
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
    modifier: Modifier = Modifier
) {
    val latestOnOpen by rememberUpdatedState(onOpen)
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    val openLabel = stringResource(R.string.chat_minimap_open)
    val closeLabel = stringResource(R.string.chat_minimap_close)
    LaunchedEffect(messages.isEmpty()) { if (messages.isEmpty()) state.isOpen = false }
    BackHandler(enabled = state.isOpen) { state.isOpen = false }
    if (messages.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val railWidth = minOf(44.dp, maxWidth * 0.30f)
        val topInset = with(LocalDensity.current) { topObscuredPx.toDp() }.coerceIn(0.dp, maxHeight)
        val bottomInset = with(LocalDensity.current) { bottomObscuredPx.toDp() }
            .coerceIn(0.dp, maxHeight - topInset)
        if (maxHeight - topInset - bottomInset <= 0.dp) return@BoxWithConstraints
        // Both the hidden edge target and the visible rail live inside the same measured gap.
        // Clip the slide animation too, so neither can cover the app bar or the input panel.
        Box(Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset).clipToBounds()) {
            if (!state.isOpen) {
                // Only the central handle asks Android to yield its Back gesture.
                Box(
                    Modifier.align(AbsoluteAlignment.CenterRight)
                        .width(24.dp).fillMaxHeight()
                        .testTag("chat-minimap-edge")
                        .clickable(onClickLabel = openLabel) { latestOnOpen(); state.isOpen = true }
                        .semantics { contentDescription = openLabel }
                        .pointerInput(conversationKey, threshold) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onDragEnd = { if (distance < -threshold) { latestOnOpen(); state.isOpen = true } },
                                onDragCancel = { distance = 0f },
                                onHorizontalDrag = { change, dx -> change.consume(); distance += dx }
                            )
                        },
                    contentAlignment = AbsoluteAlignment.CenterRight
                ) {
                    Box(Modifier.width(24.dp).height(160.dp).systemGestureExclusion())
                }
            }
            AnimatedVisibility(
                visible = state.isOpen,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(AbsoluteAlignment.CenterRight).padding(end = 4.dp)
            ) {
                DisposableEffect(state) { onDispose { state.boundsInRoot = null } }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.width(railWidth)
                        .heightIn(max = 480.dp).fillMaxHeight()
                        .testTag("chat-minimap-sidebar")
                        .onGloballyPositioned { state.boundsInRoot = it.boundsInRoot() }
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
                                messages, listState, topObscuredPx, bottomObscuredPx, onNavigate,
                                Modifier.fillMaxSize()
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
    onNavigate: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
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
            val estimatedLines = maxOf(text.count { it == '\n' } + 1, message.content.length / (width / 8).coerceAtLeast(20))
            MinimapPreview(message.id, message.role == ChatRole.USER, lines, media > 0,
                (48f + estimatedLines * 20f + media * 120f) * density)
        }
    }
    LaunchedEffect(listState, entries, heights) {
        val ids = entries.map { it.id }.toSet()
        heights.keys.toList().filterNot { it in ids }.forEach { heights.remove(it) }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key to it.size } }
            .collect { visible ->
                visible.forEach { (key, size) ->
                    if (key is String && key in ids && size > 0) heights[key] = size
                }
            }
    }
    // Keep the full conversation fitted to the rail. Measurements collected during scrolling
    // are used on the next content change rather than shifting the map under the user's finger.
    val spans = remember(entries) {
        minimapSpans(entries.map { it.id to (heights[it.id]?.toFloat() ?: it.estimatedHeight) })
    }
    // Keep the map fixed under the finger while newly visited messages are measured.
    var draggingSpans by remember { mutableStateOf<List<MinimapSpan>?>(null) }
    val displayedSpans = draggingSpans ?: spans
    val latestSpans by rememberUpdatedState(spans)
    val layout = listState.layoutInfo
    val viewport = minimapViewport(
        displayedSpans,
        layout.visibleItemsInfo.mapNotNull { item -> (item.key as? String)?.let { MinimapVisibleItem(it, item.offset, item.size) } },
        layout.viewportStartOffset + bottomObscuredPx,
        layout.viewportEndOffset - topObscuredPx
    )
    val navigate: (Float) -> Unit = { position ->
        minimapTarget(displayedSpans, position)?.let { target ->
            latestOnNavigate()
            navigationJob?.cancel()
            navigationJob = scope.launch {
                val current = latestMessages
                val chronologicalIndex = current.indexOfFirst { it.id == target.id }
                if (chronologicalIndex >= 0) {
                    val index = current.lastIndex - chronologicalIndex
                    // The first jump brings even an unmeasured/offscreen message into the lazy layout.
                    listState.scrollToItem(index)
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.key == target.id }
                    if (item != null) {
                        val readableHeight = info.viewportEndOffset - info.viewportStartOffset - topObscuredPx - bottomObscuredPx
                        listState.scrollToItem(index, minimapScrollOffset(item.size, target.fraction, readableHeight))
                    }
                }
            }
        }
    }
    val currentNavigate by rememberUpdatedState(navigate)
    val description = stringResource(R.string.chat_minimap_navigate)
    val userColor = MaterialTheme.colorScheme.primary
    val modelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.primary
    Canvas(modifier.testTag("chat-minimap-map")
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(viewport?.start ?: 0f, 0f..1f)
            setProgress { currentNavigate(it); true }
        }
        .pointerInput(Unit) { detectTapGestures { currentNavigate(it.y / size.height.coerceAtLeast(1)) } }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { point ->
                    draggingSpans = latestSpans
                    currentNavigate(point.y / size.height.coerceAtLeast(1))
                },
                onVerticalDrag = { change, _ ->
                    change.consume()
                    currentNavigate(change.position.y / size.height.coerceAtLeast(1))
                },
                onDragEnd = { draggingSpans = null },
                onDragCancel = { draggingSpans = null }
            )
        }
    ) {
        displayedSpans.forEachIndexed { index, span ->
            val entry = entries[index]
            val y = span.start * size.height
            val spanHeight = (span.end - span.start) * size.height
            if (spanHeight <= 0f) return@forEachIndexed
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
        viewport?.let { range ->
            val top = range.start * size.height
            val bottom = maxOf(range.endInclusive * size.height, top + 3.dp.toPx()).coerceAtMost(size.height)
            drawRect(highlight.copy(alpha = 0.12f), Offset(0f, top), Size(size.width, bottom - top))
            drawRect(highlight.copy(alpha = 0.8f), Offset(0f, top), Size(size.width, bottom - top), style = Stroke(1.dp.toPx()))
        }
    }
}

private data class MinimapPreview(
    val id: String, val user: Boolean, val lines: List<Float>, val hasMedia: Boolean, val estimatedHeight: Float
)
