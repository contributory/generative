package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.MarkdownElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mikepenz.markdown.compose.components.MarkdownComponents
import org.intellij.markdown.ast.ASTNode
import org.starfall.generative.R
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.features.home.chat.ui.MarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackMarkdownVirtualizedScope
import org.starfall.generative.shared.ui.text.LocalStreamingTextFade

/**
 * The active parser keeps an append-only stable AST prefix and a bounded unstable tail. This renderer
 * slices source only for mounted blocks, so a growing transcript is never copied into every element.
 */
@Composable
internal fun VirtualizedIncrementalMarkdown(
    stableNodes: List<ASTNode>,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    overflowStart: Int?,
    parserKey: Any,
    components: MarkdownComponents,
    modifier: Modifier,
    typographyKey: Int,
    optionsKey: Int
) {
    val viewport = LocalMarkdownViewportState.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measuredRootTop = remember(parserKey) { mutableFloatStateOf(Float.NaN) }
    val measureRevision = remember(parserKey) { mutableIntStateOf(0) }
    val heightIndexes = remember(parserKey, typographyKey, optionsKey) {
        LinkedHashMap<StreamingLayoutProfile, MarkdownBlockHeightIndex>()
    }
    val measuredTailHeights = remember(parserKey) { LinkedHashMap<TailHeightKey, Int>() }

    if (stableNodes.size + unstableNodes.size <= SMALL_INCREMENTAL_DOCUMENT_BLOCK_LIMIT && overflowStart == null) {
        Column(modifier.fillMaxWidth()) {
            stableNodes.forEachIndexed { index, node ->
                androidx.compose.runtime.key(incrementalBlockSlot("stable", index, node)) {
                    StreamingMarkdownSourceBlock(node, source, stable = true, components = components)
                }
            }
            unstableNodes.forEachIndexed { index, node ->
                androidx.compose.runtime.key(incrementalBlockSlot("tail", index, node)) {
                    StreamingMarkdownSourceBlock(node, source, stable = false, components = components)
                }
            }
        }
        return
    }

    SubcomposeLayout(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val top = coordinates.boundsInRoot().top
            if (measuredRootTop.floatValue.isNaN() || kotlin.math.abs(measuredRootTop.floatValue - top) >= 0.5f) {
                measuredRootTop.floatValue = top
            }
        }
    ) { constraints ->
        @Suppress("UNUSED_VARIABLE") val observedRevision = measureRevision.intValue
        val viewportBounds = viewport?.bounds
        val rootTop = measuredRootTop.floatValue
        val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
        val profile = StreamingLayoutProfile(
            widthPx = width,
            minWidthPx = constraints.minWidth,
            maxWidthPx = constraints.maxWidth,
            minHeightPx = constraints.minHeight,
            maxHeightPx = constraints.maxHeight,
            densityMilli = (density.density * 1_000f).toInt(),
            fontScaleMilli = (density.fontScale * 1_000f).toInt(),
            direction = direction,
            typographyKey = typographyKey,
            optionsKey = optionsKey
        )
        val heightIndex = heightIndexes.getOrPut(profile) {
            if (heightIndexes.size >= MAX_STREAMING_LAYOUT_PROFILES) heightIndexes.clear()
            MarkdownBlockHeightIndex(intArrayOf())
        }
        val spacingPx = with(density) { STREAMING_BLOCK_SPACING.roundToPx() }
        while (heightIndex.size < stableNodes.size) {
            val node = stableNodes[heightIndex.size]
            heightIndex.append(
                estimateMarkdownBlockHeight(
                    sourceLength = (node.endOffset - node.startOffset).coerceAtLeast(0),
                    typeName = node.type.name,
                    widthPx = width,
                    density = density.density,
                    spacingPx = spacingPx
                )
            )
        }

        val tailCount = if (overflowStart == null) unstableNodes.size else 0
        val tailHeights = IntArray(tailCount) { index ->
            val node = unstableNodes[index]
            val key = TailHeightKey(profile, node)
            measuredTailHeights[key] ?: estimateMarkdownBlockHeight(
                sourceLength = (node.endOffset - node.startOffset).coerceAtLeast(0),
                typeName = node.type.name,
                widthPx = width,
                density = density.density,
                spacingPx = spacingPx
            )
        }
        val previewLength = if (overflowStart == null) 0 else {
            (source.length - overflowStart).coerceIn(0L, MAX_STREAMING_TAIL_PREVIEW_CHARS.toLong()).toInt()
        }
        val overflowHeight = if (overflowStart == null) 0 else estimateMarkdownBlockHeight(
            sourceLength = previewLength,
            typeName = "PARAGRAPH",
            widthPx = width,
            density = density.density,
            spacingPx = spacingPx
        )
        val stableHeight = heightIndex.totalHeight.coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX)
        val tailHeight = (tailHeights.sum() + overflowHeight)
            .coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX - stableHeight)
        val totalHeight = (stableHeight + tailHeight).coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX)

        val visibleStart = if (viewportBounds == null || rootTop.isNaN()) 0
        else (viewportBounds.top - rootTop).toInt()
        val visibleEnd = if (viewportBounds == null || rootTop.isNaN()) INITIAL_INCREMENTAL_VIEWPORT_PX
        else (viewportBounds.bottom - rootTop).toInt()
        val viewportHeight = if (viewportBounds == null) INITIAL_INCREMENTAL_VIEWPORT_PX
        else (viewportBounds.bottom - viewportBounds.top).toInt().coerceAtLeast(1)
        val overscan = (viewportHeight * STREAMING_OVERSCAN_FRACTION).toInt().coerceAtLeast(1)
        val startOffset = (visibleStart - overscan).coerceAtLeast(0)
        val endOffset = (visibleEnd + overscan).coerceAtMost(totalHeight)
        val outsideBelow = viewportBounds != null && rootTop > viewportBounds.bottom
        val firstStable = if (outsideBelow) stableNodes.size else heightIndex.blockAt(startOffset)
        val stableEndOffset = endOffset.coerceAtMost(stableHeight)
        val measuredStable = ArrayList<Pair<Int, androidx.compose.ui.layout.Placeable>>()
        var stableIndex = firstStable
        while (stableIndex < stableNodes.size && heightIndex.topOf(stableIndex) < stableEndOffset) {
            val node = stableNodes[stableIndex]
            val nodeIndex = stableIndex
            val placeable = subcompose(incrementalBlockSlot("stable", stableIndex, node)) {
                Box(
                    Modifier.fillMaxWidth().onSizeChanged { size ->
                        if (heightIndex.update(nodeIndex, size.height)) measureRevision.intValue += 1
                    }
                ) {
                    TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
                    StreamingMarkdownSourceBlock(node, source, stable = true, components = components)
                }
            }.single().measure(Constraints(
                minWidth = 0,
                maxWidth = width,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            ))
            measuredStable += stableIndex to placeable
            stableIndex++
        }

        val tailStartY = stableHeight
        val hasTail = tailCount + (if (overflowStart == null) 0 else 1) > 0
        val tailVisible = hasTail && !outsideBelow && (
            viewportBounds == null ||
                (visibleEnd + overscan > tailStartY && visibleStart - overscan < totalHeight)
            )
        val measuredTail = ArrayList<Pair<Int, androidx.compose.ui.layout.Placeable>>()
        if (tailVisible) {
            var top = tailStartY
            unstableNodes.take(tailCount).forEachIndexed { index, node ->
                val estimatedHeight = tailHeights[index]
                if (top + estimatedHeight >= startOffset && top < endOffset) {
                    val key = TailHeightKey(profile, node)
                    val placeable = subcompose(incrementalBlockSlot("tail", index, node)) {
                        Box(
                            Modifier.fillMaxWidth().onSizeChanged { size ->
                                if (size.height > 0 && measuredTailHeights[key] != size.height) {
                                    measuredTailHeights[key] = size.height
                                    while (measuredTailHeights.size > MAX_REMEMBERED_TAIL_HEIGHTS) {
                                        val iterator = measuredTailHeights.entries.iterator()
                                        if (iterator.hasNext()) {
                                            iterator.next()
                                            iterator.remove()
                                        } else break
                                    }
                                    measureRevision.intValue += 1
                                }
                            }
                        ) {
                            TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
                            StreamingMarkdownSourceBlock(node, source, stable = false, components = components)
                        }
                    }.single().measure(Constraints(
                        minWidth = 0,
                        maxWidth = width,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity
                    ))
                    measuredTail += top to placeable
                }
                top += estimatedHeight
            }
        }

        val measuredPreview = if (tailVisible && overflowStart != null && overflowHeight > 0) {
            val previewTop = stableHeight
            val placeable = subcompose(IncrementalTailPreviewSlot) {
                Box(Modifier.fillMaxWidth()) {
                    TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
                    IncrementalTailPreview(source, overflowStart)
                }
            }.single().measure(Constraints(
                minWidth = 0,
                maxWidth = width,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            ))
            previewTop to placeable
        } else null

        val layoutWidth = constraints.constrainWidth(width)
        val actualTailHeight = maxOf(
            tailHeight,
            measuredTail.maxOfOrNull { (top, placeable) ->
                (top - stableHeight + placeable.height).coerceAtLeast(0)
            } ?: 0,
            measuredPreview?.second?.height ?: 0
        )
        val layoutHeight = constraints.constrainHeight(
            (stableHeight + actualTailHeight).coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX)
        )
        layout(layoutWidth, layoutHeight) {
            measuredStable.forEach { (index, placeable) ->
                placeable.place(0, heightIndex.topOf(index).coerceAtMost(layoutHeight))
            }
            measuredTail.forEach { (top, placeable) ->
                placeable.place(0, top.coerceAtMost(layoutHeight))
            }
            measuredPreview?.let { (top, placeable) -> placeable.place(0, top.coerceAtMost(layoutHeight)) }
        }
    }
}

@Composable
private fun StreamingMarkdownSourceBlock(
    node: ASTNode,
    source: StreamTextSnapshot,
    stable: Boolean,
    components: MarkdownComponents
) {
    val sourceLength = source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val startOffset = node.startOffset.coerceIn(0, sourceLength)
    val endOffset = node.endOffset.coerceIn(startOffset, sourceLength)
    val sourceLengthKey = if (stable) 0L else source.length
    val text = remember(node, startOffset, endOffset, sourceLengthKey) {
        source.slice(startOffset, endOffset)
    }
    val shiftedNode = remember(node, startOffset) { ShiftedMarkdownAstNode(node, startOffset) }
    val fingerprint = remember(text) { markdownContentFingerprint(text) }
    CompositionLocalProvider(
        LocalStreamingTextFade provides !stable,
        LocalMarkdownContentFingerprint provides fingerprint
    ) {
        MarkdownElement(
            node = shiftedNode,
            components = components,
            content = text,
            includeSpacer = true
        )
    }
}

@Composable
private fun IncrementalTailPreview(source: StreamTextSnapshot, overflowStart: Int) {
    val end = source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val start = maxOf(overflowStart, end - MAX_STREAMING_TAIL_PREVIEW_CHARS).coerceAtLeast(0)
    val preview = remember(source.latestSegment, source.length, overflowStart) { source.slice(start, end) }
    var expandedSource by remember(overflowStart) { mutableStateOf<StreamTextSnapshot?>(null) }
    val sourceAtOpen = expandedSource
    val chunks by produceState(emptyList<String>(), sourceAtOpen, overflowStart) {
        value = if (sourceAtOpen == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                val snapshotEnd = sourceAtOpen.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                chunkLargeMarkdownParagraph(
                    sourceAtOpen.slice(overflowStart, snapshotEnd),
                    chunkSize = STREAMING_TAIL_DIALOG_CHUNK_CHARS
                )
            }
        }
    }
    val style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(STREAMING_BLOCK_SPACING))
        SelectionContainer {
            Text(
                text = (if (start > overflowStart) "…\n" else "") + preview,
                style = style,
                maxLines = MAX_STREAMING_TAIL_PREVIEW_LINES
            )
        }
        androidx.compose.material3.TextButton(onClick = { expandedSource = source }) {
            Text(stringResource(R.string.expand))
        }
    }
    if (sourceAtOpen != null) {
        AlertDialog(
            onDismissRequest = { expandedSource = null },
            title = { Text(stringResource(R.string.expand)) },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SelectionContainer {
                        LazyColumn {
                            itemsIndexed(chunks, key = { index, _ -> index }) { _, chunk ->
                                Text(text = chunk, style = style)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expandedSource = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

private data class StreamingLayoutProfile(
    val widthPx: Int,
    val minWidthPx: Int,
    val maxWidthPx: Int,
    val minHeightPx: Int,
    val maxHeightPx: Int,
    val densityMilli: Int,
    val fontScaleMilli: Int,
    val direction: LayoutDirection,
    val typographyKey: Int,
    val optionsKey: Int
)

private data class TailHeightKey(val profile: StreamingLayoutProfile, val node: ASTNode)

private fun incrementalBlockSlot(kind: String, index: Int, node: ASTNode): String =
    "incremental-$kind:$index:${node.type.name}:${node.startOffset}:${node.endOffset}"

private const val SMALL_INCREMENTAL_DOCUMENT_BLOCK_LIMIT = 12
private const val MAX_STREAMING_LAYOUT_PROFILES = 2
private const val MAX_REMEMBERED_TAIL_HEIGHTS = 32
private const val STREAMING_OVERSCAN_FRACTION = 0.5f
private const val INITIAL_INCREMENTAL_VIEWPORT_PX = 1_200
private const val MAX_INCREMENTAL_LAYOUT_HEIGHT_PX = 16_000_000
private const val MAX_STREAMING_TAIL_PREVIEW_CHARS = 4_000
private const val MAX_STREAMING_TAIL_PREVIEW_LINES = 16
private const val STREAMING_TAIL_DIALOG_CHUNK_CHARS = 2_048
private val STREAMING_BLOCK_SPACING = 8.dp
private const val IncrementalTailPreviewSlot = "incremental-tail-preview"
