package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.model.State
import org.intellij.markdown.ast.ASTNode
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.ui.MarkdownBlockHeightCache
import org.starfall.generative.features.home.chat.ui.MarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackMarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackVirtualizedLayout

/** Shared viewport bounds let a Markdown message virtualize its own blocks without nesting a LazyColumn. */
@Stable
internal class MarkdownViewportState {
    var bounds by mutableStateOf<Rect?>(null)
        private set

    fun update(bounds: Rect) {
        if (this.bounds != bounds) this.bounds = bounds
    }
}

internal val LocalMarkdownViewportState = staticCompositionLocalOf<MarkdownViewportState?> { null }
internal val LocalMarkdownContentFingerprint = staticCompositionLocalOf<String?> { null }

/**
 * Renders only AST blocks near the chat viewport. A Fenwick tree keeps prefix positions and visible
 * range lookup logarithmic as measured heights replace conservative estimates. Each child is a
 * regular MarkdownElement, so the library's components, reference-link handler, and styling remain in
 * force. This is a subcomposition layout rather than a nested vertical lazy list, because chat already
 * owns the vertical scroll container.
 */
@Composable
internal fun VirtualizedMarkdownBlocks(
    state: State.Success,
    components: MarkdownComponents,
    modifier: Modifier,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    typographyKey: Int,
    optionsKey: Int
) {
    val content = state.content
    val nodes = state.node.children
    val viewport = LocalMarkdownViewportState.current
    val density = LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val measuredRootTop = remember { mutableFloatStateOf(Float.NaN) }
    val measureRevision = remember { mutableIntStateOf(0) }
    TrackVirtualizedLayout(measureRevision, measuredRootTop)
    val indexes = remember(state.node, contentFingerprint, typographyKey, optionsKey) {
        LinkedHashMap<LayoutProfile, MarkdownBlockHeightIndex>()
    }

    SubcomposeLayout(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val top = coordinates.boundsInRoot().top
            if (measuredRootTop.floatValue.isNaN() || kotlin.math.abs(measuredRootTop.floatValue - top) >= 0.5f) {
                measuredRootTop.floatValue = top
            }
        }
    ) { constraints ->
        if (nodes.size <= SMALL_DOCUMENT_BLOCK_LIMIT) {
            val placeable = measureEagerMarkdownBlocks(nodes, components, content, constraints)
            return@SubcomposeLayout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        @Suppress("UNUSED_VARIABLE") val observedRevision = measureRevision.intValue
        val context = prepareMarkdownBlockLayout(
            constraints = constraints,
            nodes = nodes,
            content = content,
            identity = identity,
            contentFingerprint = contentFingerprint,
            viewportBounds = viewport?.bounds,
            rootTop = measuredRootTop.floatValue,
            density = density,
            direction = layoutDirection,
            typographyKey = typographyKey,
            optionsKey = optionsKey,
            indexes = indexes
        )
        val measured = measureVisibleMarkdownBlocks(
            context = context,
            nodes = nodes,
            components = components,
            content = content,
            identity = identity,
            contentFingerprint = contentFingerprint,
            measureRevision = { measureRevision.intValue++ }
        )
        val layoutHeight = constraints.constrainHeight(
            context.heightIndex.totalHeight.coerceAtMost(MAX_LAYOUT_HEIGHT_PX)
        )
        layout(context.layoutWidth, layoutHeight) {
            var y = measured.firstOrNull()?.first?.let(context.heightIndex::topOf) ?: 0
            measured.forEach { (_, placeable) ->
                placeable.place(0, y.coerceAtMost(layoutHeight))
                y = (y + placeable.height).coerceAtMost(layoutHeight)
            }
        }
    }
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureEagerMarkdownBlocks(
    nodes: List<ASTNode>,
    components: MarkdownComponents,
    content: String,
    constraints: Constraints
): androidx.compose.ui.layout.Placeable = subcompose(EagerBlocksSlot) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
        nodes.forEachIndexed { index, node ->
            androidx.compose.runtime.key(blockSlot(index, node)) {
                MarkdownBlockElement(node, components, content, includeSpacer = true)
            }
        }
    }
}.single().measure(constraints)

private data class MarkdownBlockViewportLayout(
    val profile: LayoutProfile,
    val heightIndex: MarkdownBlockHeightIndex,
    val layoutWidth: Int,
    val visibleRows: List<Int>
)

private fun prepareMarkdownBlockLayout(
    constraints: Constraints,
    nodes: List<ASTNode>,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    viewportBounds: Rect?,
    rootTop: Float,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    typographyKey: Int,
    optionsKey: Int,
    indexes: LinkedHashMap<LayoutProfile, MarkdownBlockHeightIndex>
): MarkdownBlockViewportLayout {
    val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
    val profile = markdownLayoutProfile(constraints, width, density, direction, typographyKey, optionsKey)
    val heightIndex = indexes.getOrPut(profile) {
        createMarkdownBlockHeightIndex(nodes, content, identity, contentFingerprint, profile, width, density)
            .also { if (indexes.size >= MAX_LAYOUT_PROFILES_PER_MESSAGE) indexes.clear() }
    }
    val totalHeight = heightIndex.totalHeight.coerceAtMost(MAX_LAYOUT_HEIGHT_PX)
    val visibleRows = visibleMarkdownBlockRows(heightIndex, nodes.size, totalHeight, viewportBounds, rootTop)
    return MarkdownBlockViewportLayout(
        profile = profile,
        heightIndex = heightIndex,
        layoutWidth = constraints.constrainWidth(width),
        visibleRows = visibleRows
    )
}

private fun markdownLayoutProfile(
    constraints: Constraints,
    width: Int,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    typographyKey: Int,
    optionsKey: Int
): LayoutProfile = LayoutProfile(
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

private fun createMarkdownBlockHeightIndex(
    nodes: List<ASTNode>,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    profile: LayoutProfile,
    width: Int,
    density: androidx.compose.ui.unit.Density
): MarkdownBlockHeightIndex {
    val spacingPx = with(density) { BLOCK_SPACING.roundToPx() }
    val heights = IntArray(nodes.size) { index ->
        val node = nodes[index]
        val key = blockHeightCacheKey(identity, contentFingerprint, profile, index, node)
        MarkdownBlockHeightCache.get(key)
            ?: estimateBlockHeight(node, content, width, density.density, spacingPx)
    }
    return MarkdownBlockHeightIndex(heights)
}

private fun visibleMarkdownBlockRows(
    heightIndex: MarkdownBlockHeightIndex,
    rowCount: Int,
    totalHeight: Int,
    viewportBounds: Rect?,
    rootTop: Float
): List<Int> {
    val visibleStart = if (viewportBounds == null || rootTop.isNaN()) 0
    else (viewportBounds.top - rootTop).toInt()
    val visibleEnd = if (viewportBounds == null || rootTop.isNaN()) INITIAL_VIEWPORT_ESTIMATE_PX
    else (viewportBounds.bottom - rootTop).toInt()
    val viewportHeight = if (viewportBounds == null) INITIAL_VIEWPORT_ESTIMATE_PX
    else (viewportBounds.bottom - viewportBounds.top).toInt().coerceAtLeast(1)
    val overscan = (viewportHeight * OVERSCAN_FRACTION).toInt().coerceAtLeast(1)
    val startOffset = (visibleStart - overscan).coerceAtLeast(0)
    val endOffset = (visibleEnd + overscan).coerceAtMost(totalHeight)
    val first = if (viewportBounds != null && rootTop > viewportBounds.bottom) rowCount
    else heightIndex.blockAt(startOffset)
    val rows = ArrayList<Int>()
    var index = first
    while (index < rowCount && heightIndex.topOf(index) < endOffset) {
        rows += index
        index++
    }
    return rows
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureVisibleMarkdownBlocks(
    context: MarkdownBlockViewportLayout,
    nodes: List<ASTNode>,
    components: MarkdownComponents,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    measureRevision: () -> Unit
): List<Pair<Int, androidx.compose.ui.layout.Placeable>> = context.visibleRows.map { blockIndex ->
    measureMarkdownBlock(
        blockIndex = blockIndex,
        node = nodes[blockIndex],
        context = context,
        components = components,
        content = content,
        identity = identity,
        contentFingerprint = contentFingerprint,
        measureRevision = measureRevision
    )
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureMarkdownBlock(
    blockIndex: Int,
    node: ASTNode,
    context: MarkdownBlockViewportLayout,
    components: MarkdownComponents,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    measureRevision: () -> Unit
): Pair<Int, androidx.compose.ui.layout.Placeable> {
    val cacheKey = blockHeightCacheKey(identity, contentFingerprint, context.profile, blockIndex, node)
    val placeable = subcompose(blockSlot(blockIndex, node)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (size.height > 0 && context.heightIndex.update(blockIndex, size.height)) {
                        MarkdownBlockHeightCache.put(cacheKey, size.height)
                        measureRevision()
                    }
                }
        ) {
            TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
            MarkdownBlockElement(node, components, content, includeSpacer = true)
        }
    }.single().measure(Constraints(
        minWidth = 0,
        maxWidth = context.profile.widthPx,
        minHeight = 0,
        maxHeight = Constraints.Infinity
    ))
    return blockIndex to placeable
}

private data class LayoutProfile(
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

/** Mutable prefix-sum index. Updating a row and finding a row at an offset are both O(log n). */
internal class MarkdownBlockHeightIndex(initialHeights: IntArray) {
    private var heights = IntArray(nextCapacity(initialHeights.size))
    private var tree = IntArray(heights.size + 1)
    private var count = initialHeights.size

    init {
        initialHeights.forEachIndexed { index, height -> heights[index] = height.coerceAtLeast(1) }
        rebuildTree()
    }

    val size: Int get() = count
    val totalHeight: Int get() = prefixHeight(count)

    fun topOf(index: Int): Int = prefixHeight(index.coerceIn(0, count))

    /** First row whose bottom is after [offsetPx], or [size] when beyond the document. */
    fun blockAt(offsetPx: Int): Int {
        if (count == 0) return 0
        val target = offsetPx.coerceAtLeast(0)
        var index = 0
        var sum = 0
        var bit = Integer.highestOneBit(count)
        while (bit != 0) {
            val next = index + bit
            if (next <= count && sum + tree[next] <= target) {
                index = next
                sum += tree[next]
            }
            bit = bit ushr 1
        }
        return index.coerceAtMost(count)
    }

    /** Appends an estimated row in amortized O(log n), growing the index geometrically. */
    fun append(heightPx: Int) {
        if (count == heights.size) grow()
        val height = heightPx.coerceAtLeast(1)
        heights[count] = height
        count++
        addToTree(count, height)
    }

    /** Removes the final row without rebuilding the prefix tree. */
    fun removeLast(): Boolean {
        if (count == 0) return false
        val index = count
        addToTree(index, -heights[index - 1])
        count--
        heights[count] = 0
        return true
    }

    /** Returns true when a positive replacement changed the indexed height. */
    fun update(index: Int, heightPx: Int): Boolean {
        if (index !in 0 until count || heightPx <= 0 || heights[index] == heightPx) return false
        val delta = heightPx - heights[index]
        heights[index] = heightPx
        addToTree(index + 1, delta)
        return true
    }

    private fun addToTree(start: Int, delta: Int) {
        var cursor = start
        while (cursor < tree.size) {
            tree[cursor] += delta
            cursor += cursor and -cursor
        }
    }

    private fun grow() {
        val newCapacity = (heights.size * 2).coerceAtLeast(MIN_CAPACITY)
        heights = heights.copyOf(newCapacity)
        tree = IntArray(newCapacity + 1)
        rebuildTree()
    }

    private fun rebuildTree() {
        for (i in 1..count) {
            tree[i] += heights[i - 1]
            val parent = i + (i and -i)
            if (parent <= heights.size) tree[parent] += tree[i]
        }
    }

    private fun prefixHeight(count: Int): Int {
        var cursor = count.coerceIn(0, this.count)
        var sum = 0
        while (cursor > 0) {
            sum += tree[cursor]
            cursor -= cursor and -cursor
        }
        return sum
    }

    private companion object {
        const val MIN_CAPACITY = 4

        fun nextCapacity(size: Int): Int {
            if (size == 0) return 0
            var capacity = MIN_CAPACITY
            while (capacity < size) capacity *= 2
            return capacity
        }
    }
}

private fun estimateBlockHeight(node: ASTNode, content: String, widthPx: Int, density: Float, spacingPx: Int): Int {
    val start = node.startOffset.coerceIn(0, content.length)
    val end = node.endOffset.coerceIn(start, content.length)
    return estimateMarkdownBlockHeight(
        sourceLength = end - start,
        typeName = node.type.name,
        widthPx = widthPx,
        density = density,
        spacingPx = spacingPx
    )
}

internal fun estimateMarkdownBlockHeight(
    sourceLength: Int,
    typeName: String,
    widthPx: Int,
    density: Float,
    spacingPx: Int
): Int {
    val codeFence = typeName == "CODE_FENCE" || typeName == "CODE_BLOCK"
    if (codeFence && sourceLength > LARGE_CODE_BLOCK_CHARS) return (LARGE_CODE_BLOCK_HEIGHT_DP * density).toInt()

    val averageCharacterWidth = (if (codeFence) CODE_CHARACTER_WIDTH_DP else BODY_CHARACTER_WIDTH_DP) * density
    val charsPerLine = (widthPx / averageCharacterWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    val safeLength = sourceLength.coerceAtLeast(0)
    val lineCount = (safeLength + charsPerLine - 1) / charsPerLine
    val lineHeightPx = (BODY_LINE_HEIGHT_DP * density).toInt().coerceAtLeast(1)
    return (lineCount.coerceAtLeast(1) * lineHeightPx + spacingPx).coerceAtLeast(spacingPx + lineHeightPx)
}

private fun blockHeightCacheKey(
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    profile: LayoutProfile,
    index: Int,
    node: ASTNode
): String = buildString(256) {
    append(identity?.conversationId.orEmpty()).append('|')
    append(identity?.messageId.orEmpty()).append('|')
    append(identity?.blockId.orEmpty()).append('|')
    append(contentFingerprint).append('|')
    append(index).append(':').append(node.type.name).append(':')
    append(node.startOffset).append(':').append(node.endOffset).append('|')
    append(profile.widthPx).append(':').append(profile.minWidthPx).append(':').append(profile.maxWidthPx).append(':')
    append(profile.minHeightPx).append(':').append(profile.maxHeightPx).append(':')
    append(profile.densityMilli).append(':').append(profile.fontScaleMilli).append(':')
    append(profile.direction.ordinal).append(':').append(profile.typographyKey).append(':').append(profile.optionsKey)
}

private fun blockSlot(index: Int, node: ASTNode): String =
    "$index:${node.type.name}:${node.startOffset}:${node.endOffset}"

internal fun markdownContentFingerprint(content: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 15])
        }
    }
}

private const val SMALL_DOCUMENT_BLOCK_LIMIT = 12
private const val MAX_LAYOUT_PROFILES_PER_MESSAGE = 2
private const val OVERSCAN_FRACTION = 0.5f
private const val INITIAL_VIEWPORT_ESTIMATE_PX = 1_200
private const val MAX_LAYOUT_HEIGHT_PX = 16_000_000
private const val BODY_CHARACTER_WIDTH_DP = 7.5f
private const val CODE_CHARACTER_WIDTH_DP = 8f
private const val BODY_LINE_HEIGHT_DP = 24f
private val BLOCK_SPACING = 8.dp
private const val LARGE_CODE_BLOCK_CHARS = 16_000
private const val LARGE_CODE_BLOCK_HEIGHT_DP = 220f
private const val EagerBlocksSlot = "markdown-eager-blocks"
