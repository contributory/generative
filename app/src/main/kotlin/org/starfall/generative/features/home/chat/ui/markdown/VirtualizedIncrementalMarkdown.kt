package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponents
import org.intellij.markdown.ast.ASTNode
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
        EagerIncrementalMarkdown(modifier, stableNodes, unstableNodes, source, components)
        return
    }
    RenderSubcomposedIncrementalMarkdown(
        modifier = modifier,
        stableNodes = stableNodes,
        unstableNodes = unstableNodes,
        source = source,
        overflowStart = overflowStart,
        components = components,
        viewport = viewport,
        density = density,
        direction = direction,
        measuredRootTop = measuredRootTop,
        measureRevision = measureRevision,
        heightIndexes = heightIndexes,
        measuredTailHeights = measuredTailHeights,
        typographyKey = typographyKey,
        optionsKey = optionsKey
    )
}

@Composable
private fun EagerIncrementalMarkdown(
    modifier: Modifier,
    stableNodes: List<ASTNode>,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    components: MarkdownComponents
) {
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
}

@Composable
private fun RenderSubcomposedIncrementalMarkdown(
    modifier: Modifier,
    stableNodes: List<ASTNode>,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    overflowStart: Int?,
    components: MarkdownComponents,
    viewport: MarkdownViewportState?,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    measuredRootTop: androidx.compose.runtime.MutableFloatState,
    measureRevision: androidx.compose.runtime.MutableIntState,
    heightIndexes: LinkedHashMap<StreamingLayoutProfile, MarkdownBlockHeightIndex>,
    measuredTailHeights: LinkedHashMap<TailHeightKey, Int>,
    typographyKey: Int,
    optionsKey: Int
) {
    SubcomposeLayout(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val top = coordinates.boundsInRoot().top
            if (measuredRootTop.floatValue.isNaN() || kotlin.math.abs(measuredRootTop.floatValue - top) >= 0.5f) {
                measuredRootTop.floatValue = top
            }
        }
    ) { constraints ->
        @Suppress("UNUSED_VARIABLE") val observedRevision = measureRevision.intValue
        val context = prepareIncrementalMarkdownLayout(
            constraints = constraints,
            stableNodes = stableNodes,
            unstableNodes = unstableNodes,
            source = source,
            overflowStart = overflowStart,
            viewportBounds = viewport?.bounds,
            rootTop = measuredRootTop.floatValue,
            density = density,
            direction = direction,
            heightIndexes = heightIndexes,
            measuredTailHeights = measuredTailHeights,
            typographyKey = typographyKey,
            optionsKey = optionsKey
        )
        val measuredStable = measureIncrementalStableBlocks(
            context, stableNodes, source, components, measureRevision = { measureRevision.intValue++ }
        )
        val measuredTail = measureIncrementalTailBlocks(
            context, unstableNodes, source, components, measuredTailHeights,
            measureRevision = { measureRevision.intValue++ }
        )
        val measuredPreview = if (context.previewVisible) {
            measureIncrementalOverflowPreview(context, source, overflowStart!!)
        } else null
        val layoutHeight = incrementalLayoutHeight(constraints, context, measuredTail, measuredPreview)
        layout(context.layoutWidth, layoutHeight) {
            placeIncrementalBlocks(context, measuredStable, measuredTail, measuredPreview, layoutHeight)
        }
    }
}

private data class IncrementalMarkdownLayout(
    val profile: StreamingLayoutProfile,
    val heightIndex: MarkdownBlockHeightIndex,
    val widthPx: Int,
    val stableHeight: Int,
    val estimatedTailHeight: Int,
    val visibleStableRows: List<Int>,
    val visibleTailRows: List<IncrementalTailRow>,
    val previewVisible: Boolean,
    val layoutWidth: Int
)

private data class IncrementalTailRow(val index: Int, val top: Int)

private data class IncrementalTailEstimate(
    val tailCount: Int,
    val tailHeights: IntArray,
    val overflowHeight: Int,
    val stableHeight: Int,
    val estimatedTailHeight: Int,
    val totalHeight: Int
)

private data class IncrementalViewportWindow(
    val visibleStart: Int,
    val visibleEnd: Int,
    val hasViewport: Boolean,
    val overscan: Int,
    val startOffset: Int,
    val endOffset: Int,
    val outsideBelow: Boolean
)

private data class IncrementalTailSelection(
    val rows: List<IncrementalTailRow>,
    val previewVisible: Boolean
)

private fun prepareIncrementalMarkdownLayout(
    constraints: Constraints,
    stableNodes: List<ASTNode>,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    overflowStart: Int?,
    viewportBounds: androidx.compose.ui.geometry.Rect?,
    rootTop: Float,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    heightIndexes: LinkedHashMap<StreamingLayoutProfile, MarkdownBlockHeightIndex>,
    measuredTailHeights: LinkedHashMap<TailHeightKey, Int>,
    typographyKey: Int,
    optionsKey: Int
): IncrementalMarkdownLayout {
    val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
    val profile = streamingLayoutProfile(constraints, width, density, direction, typographyKey, optionsKey)
    val heightIndex = streamingHeightIndex(heightIndexes, profile)
    appendStableHeightEstimates(heightIndex, stableNodes, source, width, density)
    val tailEstimate = estimateIncrementalTail(
        heightIndex, stableNodes, unstableNodes, source, overflowStart, profile, measuredTailHeights, width, density
    )
    val window = incrementalViewportWindow(viewportBounds, rootTop, tailEstimate.totalHeight)
    val stableRows = visibleStableRows(heightIndex, stableNodes.size, tailEstimate.stableHeight, window)
    val tailSelection = visibleIncrementalTailRows(
        unstableNodes = unstableNodes,
        tailEstimate = tailEstimate,
        overflowStart = overflowStart,
        window = window
    )
    return IncrementalMarkdownLayout(
        profile = profile,
        heightIndex = heightIndex,
        widthPx = width,
        stableHeight = tailEstimate.stableHeight,
        estimatedTailHeight = tailEstimate.estimatedTailHeight,
        visibleStableRows = stableRows,
        visibleTailRows = tailSelection.rows,
        previewVisible = tailSelection.previewVisible,
        layoutWidth = constraints.constrainWidth(width)
    )
}

private fun streamingLayoutProfile(
    constraints: Constraints,
    width: Int,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    typographyKey: Int,
    optionsKey: Int
): StreamingLayoutProfile = StreamingLayoutProfile(
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

private fun streamingHeightIndex(
    heightIndexes: LinkedHashMap<StreamingLayoutProfile, MarkdownBlockHeightIndex>,
    profile: StreamingLayoutProfile
): MarkdownBlockHeightIndex = heightIndexes.getOrPut(profile) {
    if (heightIndexes.size >= MAX_STREAMING_LAYOUT_PROFILES) heightIndexes.clear()
    MarkdownBlockHeightIndex(intArrayOf())
}

private fun appendStableHeightEstimates(
    heightIndex: MarkdownBlockHeightIndex,
    stableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    width: Int,
    density: androidx.compose.ui.unit.Density
) {
    val spacingPx = with(density) { STREAMING_BLOCK_SPACING.roundToPx() }
    while (heightIndex.size < stableNodes.size) {
        val node = stableNodes[heightIndex.size]
        val start = node.startOffset.coerceIn(0, source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val end = node.endOffset.coerceIn(start, source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        heightIndex.append(
            estimateMarkdownBlockHeight(
                sourceLength = end - start,
                typeName = node.type.name,
                widthPx = width,
                density = density.density,
                spacingPx = spacingPx,
                sourceText = source.slice(start, end)
            )
        )
    }
}

private fun estimateIncrementalTail(
    heightIndex: MarkdownBlockHeightIndex,
    stableNodes: List<ASTNode>,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    overflowStart: Int?,
    profile: StreamingLayoutProfile,
    measuredTailHeights: Map<TailHeightKey, Int>,
    width: Int,
    density: androidx.compose.ui.unit.Density
): IncrementalTailEstimate {
    val spacingPx = with(density) { STREAMING_BLOCK_SPACING.roundToPx() }
    val tailCount = if (overflowStart == null) unstableNodes.size else 0
    val tailHeights = IntArray(tailCount) { index ->
        val node = unstableNodes[index]
        measuredTailHeights[TailHeightKey(profile, node)] ?: run {
            val length = source.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val start = node.startOffset.coerceIn(0, length)
            val end = node.endOffset.coerceIn(start, length)
            estimateMarkdownBlockHeight(
                sourceLength = end - start,
                typeName = node.type.name,
                widthPx = width,
                density = density.density,
                spacingPx = spacingPx,
                sourceText = source.slice(start, end)
            )
        }
    }
    val previewLength = overflowStart?.let {
        (source.length - it).coerceIn(0L, MAX_MARKDOWN_RENDER_CHARS.toLong()).toInt()
    } ?: 0
    val overflowHeight = if (overflowStart == null) 0 else estimateMarkdownBlockHeight(
        sourceLength = previewLength,
        typeName = "PARAGRAPH",
        widthPx = width,
        density = density.density,
        spacingPx = spacingPx
    )
    val stableHeight = heightIndex.totalHeight.coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX)
    val estimatedTailHeight = (tailHeights.sum() + overflowHeight)
        .coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX - stableHeight)
    return IncrementalTailEstimate(
        tailCount = tailCount,
        tailHeights = tailHeights,
        overflowHeight = overflowHeight,
        stableHeight = stableHeight,
        estimatedTailHeight = estimatedTailHeight,
        totalHeight = (stableHeight + estimatedTailHeight).coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX)
    )
}

private fun incrementalViewportWindow(
    viewportBounds: androidx.compose.ui.geometry.Rect?,
    rootTop: Float,
    totalHeight: Int
): IncrementalViewportWindow {
    val visibleStart = if (viewportBounds == null || rootTop.isNaN()) 0
    else (viewportBounds.top - rootTop).toInt()
    val visibleEnd = if (viewportBounds == null || rootTop.isNaN()) INITIAL_INCREMENTAL_VIEWPORT_PX
    else (viewportBounds.bottom - rootTop).toInt()
    val viewportHeight = if (viewportBounds == null) INITIAL_INCREMENTAL_VIEWPORT_PX
    else (viewportBounds.bottom - viewportBounds.top).toInt().coerceAtLeast(1)
    val overscan = (viewportHeight * STREAMING_OVERSCAN_FRACTION).toInt().coerceAtLeast(1)
    val startOffset = (visibleStart - overscan).coerceAtLeast(0)
    val endOffset = (visibleEnd + overscan).coerceAtMost(totalHeight)
    return IncrementalViewportWindow(
        visibleStart = visibleStart,
        visibleEnd = visibleEnd,
        hasViewport = viewportBounds != null,
        overscan = overscan,
        startOffset = startOffset,
        endOffset = endOffset,
        outsideBelow = viewportBounds != null && rootTop > viewportBounds.bottom
    )
}

private fun visibleStableRows(
    heightIndex: MarkdownBlockHeightIndex,
    rowCount: Int,
    stableHeight: Int,
    window: IncrementalViewportWindow
): List<Int> {
    val first = if (window.outsideBelow) rowCount else heightIndex.blockAt(window.startOffset)
    val stableEndOffset = window.endOffset.coerceAtMost(stableHeight)
    val rows = ArrayList<Int>()
    var index = first
    while (index < rowCount && heightIndex.topOf(index) < stableEndOffset) {
        rows += index
        index++
    }
    return rows
}

private fun visibleIncrementalTailRows(
    unstableNodes: List<ASTNode>,
    tailEstimate: IncrementalTailEstimate,
    overflowStart: Int?,
    window: IncrementalViewportWindow
): IncrementalTailSelection {
    val tailStartY = tailEstimate.stableHeight
    val hasTail = tailEstimate.tailCount + (if (overflowStart == null) 0 else 1) > 0
    val tailVisible = hasTail && !window.outsideBelow && (
        !window.hasViewport ||
            (window.visibleEnd + window.overscan > tailStartY &&
                // The overflow block's real height is only known once measured, so the estimate must not cull it.
                (overflowStart != null || window.visibleStart - window.overscan < tailEstimate.totalHeight))
        )
    if (!tailVisible) return IncrementalTailSelection(emptyList(), false)

    val rows = ArrayList<IncrementalTailRow>()
    var top = tailStartY
    unstableNodes.take(tailEstimate.tailCount).forEachIndexed { index, _ ->
        val estimatedHeight = tailEstimate.tailHeights[index]
        if (top + estimatedHeight >= window.startOffset && top < window.endOffset) {
            rows += IncrementalTailRow(index, top)
        }
        top += estimatedHeight
    }
    val previewVisible = overflowStart != null && tailEstimate.overflowHeight > 0
    return IncrementalTailSelection(rows, previewVisible)
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureIncrementalStableBlocks(
    context: IncrementalMarkdownLayout,
    stableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    components: MarkdownComponents,
    measureRevision: () -> Unit
): List<Pair<Int, androidx.compose.ui.layout.Placeable>> = context.visibleStableRows.map { index ->
    measureIncrementalStableBlock(index, stableNodes[index], context, source, components, measureRevision)
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureIncrementalStableBlock(
    index: Int,
    node: ASTNode,
    context: IncrementalMarkdownLayout,
    source: StreamTextSnapshot,
    components: MarkdownComponents,
    measureRevision: () -> Unit
): Pair<Int, androidx.compose.ui.layout.Placeable> {
    val placeable = subcompose(incrementalBlockSlot("stable", index, node)) {
        Box(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                if (context.heightIndex.update(index, size.height)) measureRevision()
            }
        ) {
            TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
            StreamingMarkdownSourceBlock(node, source, stable = true, components = components)
        }
    }.single().measure(unboundedIncrementalRowConstraints(context.widthPx))
    return index to placeable
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureIncrementalTailBlocks(
    context: IncrementalMarkdownLayout,
    unstableNodes: List<ASTNode>,
    source: StreamTextSnapshot,
    components: MarkdownComponents,
    measuredTailHeights: LinkedHashMap<TailHeightKey, Int>,
    measureRevision: () -> Unit
): List<Pair<Int, androidx.compose.ui.layout.Placeable>> = context.visibleTailRows.map { row ->
    val node = unstableNodes[row.index]
    val key = TailHeightKey(context.profile, node)
    val placeable = subcompose(incrementalBlockSlot("tail", row.index, node)) {
        Box(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                updateMeasuredTailHeight(key, size.height, measuredTailHeights, measureRevision)
            }
        ) {
            TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
            StreamingMarkdownSourceBlock(node, source, stable = false, components = components)
        }
    }.single().measure(unboundedIncrementalRowConstraints(context.widthPx))
    row.top to placeable
}

private fun updateMeasuredTailHeight(
    key: TailHeightKey,
    height: Int,
    measuredTailHeights: LinkedHashMap<TailHeightKey, Int>,
    measureRevision: () -> Unit
) {
    if (height <= 0 || measuredTailHeights[key] == height) return
    measuredTailHeights[key] = height
    while (measuredTailHeights.size > MAX_REMEMBERED_TAIL_HEIGHTS) {
        val iterator = measuredTailHeights.entries.iterator()
        if (!iterator.hasNext()) break
        iterator.next()
        iterator.remove()
    }
    measureRevision()
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureIncrementalOverflowPreview(
    context: IncrementalMarkdownLayout,
    source: StreamTextSnapshot,
    overflowStart: Int
): Pair<Int, androidx.compose.ui.layout.Placeable> {
    val placeable = subcompose(IncrementalTailPreviewSlot) {
        Box(Modifier.fillMaxWidth()) {
            TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
            IncrementalTailPreview(source, overflowStart)
        }
    }.single().measure(unboundedIncrementalRowConstraints(context.widthPx))
    return context.stableHeight to placeable
}

private fun unboundedIncrementalRowConstraints(width: Int): Constraints = Constraints(
    minWidth = 0,
    maxWidth = width,
    minHeight = 0,
    maxHeight = Constraints.Infinity
)

private fun incrementalLayoutHeight(
    constraints: Constraints,
    context: IncrementalMarkdownLayout,
    measuredTail: List<Pair<Int, androidx.compose.ui.layout.Placeable>>,
    measuredPreview: Pair<Int, androidx.compose.ui.layout.Placeable>?
): Int {
    val actualTailHeight = maxOf(
        context.estimatedTailHeight,
        measuredTail.maxOfOrNull { (top, placeable) ->
            (top - context.stableHeight + placeable.height).coerceAtLeast(0)
        } ?: 0,
        measuredPreview?.second?.height ?: 0
    )
    return constraints.constrainHeight(
        (context.stableHeight + actualTailHeight).coerceAtMost(MAX_INCREMENTAL_LAYOUT_HEIGHT_PX)
    )
}

private fun androidx.compose.ui.layout.Placeable.PlacementScope.placeIncrementalBlocks(
    context: IncrementalMarkdownLayout,
    measuredStable: List<Pair<Int, androidx.compose.ui.layout.Placeable>>,
    measuredTail: List<Pair<Int, androidx.compose.ui.layout.Placeable>>,
    measuredPreview: Pair<Int, androidx.compose.ui.layout.Placeable>?,
    layoutHeight: Int
) {
    measuredStable.forEach { (index, placeable) ->
        placeable.place(0, context.heightIndex.topOf(index).coerceAtMost(layoutHeight))
    }
    measuredTail.forEach { (top, placeable) -> placeable.place(0, top.coerceAtMost(layoutHeight)) }
    measuredPreview?.let { (top, placeable) -> placeable.place(0, top.coerceAtMost(layoutHeight)) }
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
    val start = overflowStart.coerceIn(0, end)
    val text = remember(source.latestSegment, source.length, overflowStart) { source.slice(start, end) }
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(STREAMING_BLOCK_SPACING))
        OverflowPlainText(text)
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

internal fun incrementalBlockSlot(kind: String, index: Int, node: ASTNode): String =
    // The unstable tail grows on every stream update. Do not include endOffset here: doing so
    // recreates its composition subtree for every token and restarts the text fade for the whole block.
    "incremental-$kind:$index:${node.type.name}:${node.startOffset}"

private const val SMALL_INCREMENTAL_DOCUMENT_BLOCK_LIMIT = 12
private const val MAX_STREAMING_LAYOUT_PROFILES = 2
private const val MAX_REMEMBERED_TAIL_HEIGHTS = 32
private const val STREAMING_OVERSCAN_FRACTION = 1f
private const val INITIAL_INCREMENTAL_VIEWPORT_PX = 1_200
private const val MAX_INCREMENTAL_LAYOUT_HEIGHT_PX = 16_000_000
private val STREAMING_BLOCK_SPACING = 8.dp
private const val IncrementalTailPreviewSlot = "incremental-tail-preview"
