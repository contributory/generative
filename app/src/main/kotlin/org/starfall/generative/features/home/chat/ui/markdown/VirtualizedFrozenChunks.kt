package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mikepenz.markdown.model.State
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.ui.MarkdownBlockHeightCache
import org.starfall.generative.features.home.chat.ui.MarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackMarkdownVirtualizedScope
import org.starfall.generative.shared.ui.text.LocalStreamingTextFade

/** Frozen chunks share the chat viewport; the growing tail is the final row, never a nested LazyColumn. */
@Composable
internal fun VirtualizedFrozenChunks(
    chunks: List<FrozenMarkdownChunk>,
    tail: String,
    tailIsPreview: Boolean,
    tailSeed: State?,
    identity: MarkdownRenderCacheIdentity?,
    modifier: Modifier = Modifier,
    typographyKey: Int,
    optionsKey: Int
) {
    val viewport = LocalMarkdownViewportState.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val rootTop = remember(identity) { mutableFloatStateOf(Float.NaN) }
    val measureRevision = remember(identity) { mutableIntStateOf(0) }
    val heightIndexes = remember(identity) { LinkedHashMap<FrozenLayoutProfile, FrozenChunkHeightState>() }
    val tailActive = tail.isNotBlank()

    if (chunks.size <= SMALL_FROZEN_CHUNK_LIMIT) {
        EagerFrozenMarkdownChunks(modifier, chunks, tail, tailIsPreview, tailSeed, identity, tailActive)
        return
    }
    SubcomposeLayout(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val top = coordinates.boundsInRoot().top
            if (rootTop.floatValue.isNaN() || kotlin.math.abs(rootTop.floatValue - top) >= 0.5f) {
                rootTop.floatValue = top
            }
        }
    ) { constraints ->
        @Suppress("UNUSED_VARIABLE") val observedRevision = measureRevision.intValue
        val context = prepareFrozenLayout(
            constraints = constraints,
            chunks = chunks,
            tailActive = tailActive,
            tailLength = tail.length,
            identity = identity,
            viewportBounds = viewport?.bounds,
            rootTop = rootTop.floatValue,
            density = density,
            direction = direction,
            typographyKey = typographyKey,
            optionsKey = optionsKey,
            heightIndexes = heightIndexes
        )
        val measured = measureFrozenRows(
            context = context,
            chunks = chunks,
            tail = tail,
            tailIsPreview = tailIsPreview,
            tailSeed = tailSeed,
            identity = identity,
            measureRevision = { measureRevision.intValue++ }
        )
        layout(context.layoutWidth, context.layoutHeight) {
            measured.forEach { (rowIndex, placeable) ->
                placeable.place(0, context.heightIndex.topOf(rowIndex).coerceAtMost(context.layoutHeight))
            }
        }
    }
}

@Composable
private fun EagerFrozenMarkdownChunks(
    modifier: Modifier,
    chunks: List<FrozenMarkdownChunk>,
    tail: String,
    tailIsPreview: Boolean,
    tailSeed: State?,
    identity: MarkdownRenderCacheIdentity?,
    tailActive: Boolean
) {
    Column(modifier.fillMaxWidth()) {
        chunks.forEachIndexed { index, chunk ->
            androidx.compose.runtime.key(frozenChunkSlot(index, chunk)) { FrozenMarkdownChunkRow(chunk) }
        }
        if (tailActive) {
            androidx.compose.runtime.key("frozen-tail") {
                if (tailIsPreview) FrozenStreamingTailPreview(tail)
                else MarkdownRenderer(
                    content = tail,
                    isStreaming = true,
                    initialParse = tailSeed,
                    cacheIdentity = identity?.forBlock("chunk-tail-${chunks.size}")
                )
            }
        }
    }
}

private data class FrozenViewportLayout(
    val profile: FrozenLayoutProfile,
    val heightIndex: MarkdownBlockHeightIndex,
    val widthPx: Int,
    val layoutWidth: Int,
    val layoutHeight: Int,
    val firstRow: Int,
    val rowCount: Int,
    val endOffset: Int
)

private fun prepareFrozenLayout(
    constraints: Constraints,
    chunks: List<FrozenMarkdownChunk>,
    tailActive: Boolean,
    tailLength: Int,
    identity: MarkdownRenderCacheIdentity?,
    viewportBounds: Rect?,
    rootTop: Float,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    typographyKey: Int,
    optionsKey: Int,
    heightIndexes: LinkedHashMap<FrozenLayoutProfile, FrozenChunkHeightState>
): FrozenViewportLayout {
    val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
    val profile = FrozenLayoutProfile(
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
    val heightState = heightIndexes.getOrPut(profile) {
        if (heightIndexes.size >= MAX_FROZEN_LAYOUT_PROFILES) heightIndexes.clear()
        FrozenChunkHeightState()
    }
    heightState.synchronize(
        chunks = chunks,
        tailActive = tailActive,
        tailLength = tailLength,
        identity = identity,
        profile = profile,
        widthPx = width,
        density = density.density,
        spacingPx = with(density) { FROZEN_CHUNK_SPACING.roundToPx() }
    )
    val heightIndex = heightState.index
    val totalHeight = heightIndex.totalHeight.coerceAtMost(MAX_FROZEN_LAYOUT_HEIGHT_PX)
    val viewportHeight = viewportBounds?.let { (it.bottom - it.top).toInt().coerceAtLeast(1) }
        ?: INITIAL_FROZEN_VIEWPORT_PX
    val visibleStart = if (viewportBounds == null || rootTop.isNaN()) 0 else (viewportBounds.top - rootTop).toInt()
    val visibleEnd = if (viewportBounds == null || rootTop.isNaN()) INITIAL_FROZEN_VIEWPORT_PX
    else (viewportBounds.bottom - rootTop).toInt()
    val overscan = (viewportHeight * FROZEN_OVERSCAN_FRACTION).toInt().coerceAtLeast(1)
    val startOffset = (visibleStart - overscan).coerceAtLeast(0)
    val endOffset = (visibleEnd + overscan).coerceAtMost(totalHeight)
    val outsideBelow = viewportBounds != null && rootTop > viewportBounds.bottom
    val firstRow = if (outsideBelow) heightIndex.size else heightIndex.blockAt(startOffset)
    return FrozenViewportLayout(
        profile = profile,
        heightIndex = heightIndex,
        widthPx = width,
        layoutWidth = constraints.constrainWidth(width),
        layoutHeight = constraints.constrainHeight(totalHeight),
        firstRow = firstRow,
        rowCount = chunks.size + if (tailActive) 1 else 0,
        endOffset = endOffset
    )
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureFrozenRows(
    context: FrozenViewportLayout,
    chunks: List<FrozenMarkdownChunk>,
    tail: String,
    tailIsPreview: Boolean,
    tailSeed: State?,
    identity: MarkdownRenderCacheIdentity?,
    measureRevision: () -> Unit
): List<Pair<Int, androidx.compose.ui.layout.Placeable>> {
    val measured = ArrayList<Pair<Int, androidx.compose.ui.layout.Placeable>>()
    var index = context.firstRow
    while (index < context.rowCount && context.heightIndex.topOf(index) < context.endOffset) {
        val placeable = if (index < chunks.size) {
            measureFrozenChunk(index, chunks[index], context, identity, measureRevision)
        } else {
            measureFrozenTail(index, chunks.size, tail, tailIsPreview, tailSeed, identity, context, measureRevision)
        }
        measured += index to placeable
        index++
    }
    return measured
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureFrozenChunk(
    index: Int,
    chunk: FrozenMarkdownChunk,
    context: FrozenViewportLayout,
    identity: MarkdownRenderCacheIdentity?,
    measureRevision: () -> Unit
): androidx.compose.ui.layout.Placeable {
    val cacheKey = frozenChunkHeightCacheKey(identity, chunk, context.profile, index)
    return subcompose(frozenChunkSlot(index, chunk)) {
        Box(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                if (context.heightIndex.update(index, size.height)) {
                    cacheKey?.let { MarkdownBlockHeightCache.put(it, size.height) }
                    measureRevision()
                }
            }
        ) {
            TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TOP_LEVEL_BLOCK)
            FrozenMarkdownChunkRow(chunk)
        }
    }.single().measure(unboundedRowConstraints(context.widthPx))
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureFrozenTail(
    tailIndex: Int,
    chunkCount: Int,
    tail: String,
    tailIsPreview: Boolean,
    tailSeed: State?,
    identity: MarkdownRenderCacheIdentity?,
    context: FrozenViewportLayout,
    measureRevision: () -> Unit
): androidx.compose.ui.layout.Placeable = subcompose(FROZEN_TAIL_SLOT) {
    Box(
        Modifier.fillMaxWidth().onSizeChanged { size ->
            if (context.heightIndex.update(tailIndex, size.height)) measureRevision()
        }
    ) {
        if (tailIsPreview) FrozenStreamingTailPreview(tail)
        else MarkdownRenderer(
            content = tail,
            isStreaming = true,
            initialParse = tailSeed,
            cacheIdentity = identity?.forBlock("chunk-tail-$chunkCount")
        )
    }
}.single().measure(unboundedRowConstraints(context.widthPx))

private fun unboundedRowConstraints(width: Int): Constraints = Constraints(
    minWidth = 0,
    maxWidth = width,
    minHeight = 0,
    maxHeight = Constraints.Infinity
)

@Composable
private fun FrozenStreamingTailPreview(text: String) {
    CompositionLocalProvider(LocalStreamingTextFade provides false) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            maxLines = 16
        )
    }
}

@Composable
private fun FrozenMarkdownChunkRow(chunk: FrozenMarkdownChunk) {
    CompositionLocalProvider(
        LocalStreamingTextFade provides false,
        LocalMarkdownContentFingerprint provides chunk.fingerprint
    ) {
        MarkdownRenderer(
            content = chunk.text,
            isStreaming = false,
            initialParse = chunk.parsed,
            cacheIdentity = chunk.cacheIdentity,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Shared data type with the chunker; its stable text fingerprint is computed off Main at freeze time. */
internal class FrozenMarkdownChunk(
    val text: String,
    val parsed: State,
    val cacheIdentity: MarkdownRenderCacheIdentity?,
    val fingerprint: String
)

private class FrozenChunkHeightState {
    var index = MarkdownBlockHeightIndex(intArrayOf())
        private set
    private var indexedChunks = 0
    private var tailPresent = false
    private var tailLength = -1

    fun synchronize(
        chunks: List<FrozenMarkdownChunk>,
        tailActive: Boolean,
        tailLength: Int,
        identity: MarkdownRenderCacheIdentity?,
        profile: FrozenLayoutProfile,
        widthPx: Int,
        density: Float,
        spacingPx: Int
    ) {
        if (chunks.size < indexedChunks) reset()
        while (indexedChunks < chunks.size) {
            val chunkIndex = indexedChunks
            val chunk = chunks[chunkIndex]
            val estimated = frozenChunkHeight(chunk, widthPx, density, spacingPx)
            val key = frozenChunkHeightCacheKey(identity, chunk, profile, chunkIndex)
            val height = key?.let(MarkdownBlockHeightCache::get) ?: estimated
            if (tailPresent && index.size == chunkIndex + 1) {
                index.update(chunkIndex, height)
                tailPresent = false
            } else {
                index.append(height)
            }
            indexedChunks++
        }

        if (tailActive) {
            val height = estimatedTailHeight(tailLength, widthPx, density, spacingPx)
            if (tailPresent && this.tailLength == tailLength) {
                // Keep an exact measured tail height while its text length has not changed.
            } else if (tailPresent) {
                index.update(chunks.size, height)
            } else {
                index.append(height)
                tailPresent = true
            }
            this.tailLength = tailLength
        } else if (tailPresent) {
            index.removeLast()
            tailPresent = false
            this.tailLength = -1
        }
    }

    private fun reset() {
        index = MarkdownBlockHeightIndex(intArrayOf())
        indexedChunks = 0
        tailPresent = false
        tailLength = -1
    }
}

private fun frozenChunkHeight(
    chunk: FrozenMarkdownChunk,
    widthPx: Int,
    density: Float,
    spacingPx: Int
): Int {
    val nodes = (chunk.parsed as? State.Success)?.node?.children.orEmpty()
    if (nodes.isEmpty()) return estimatedTailHeight(chunk.text.length, widthPx, density, spacingPx)
    return nodes.sumOf { node ->
        estimateMarkdownBlockHeight(
            sourceLength = (node.endOffset - node.startOffset).coerceAtLeast(0),
            typeName = node.type.name,
            widthPx = widthPx,
            density = density,
            spacingPx = spacingPx
        )
    }.coerceAtLeast(1)
}

private fun estimatedTailHeight(length: Int, widthPx: Int, density: Float, spacingPx: Int): Int =
    estimateMarkdownBlockHeight(length, "PARAGRAPH", widthPx, density, spacingPx)

private data class FrozenLayoutProfile(
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

private fun frozenChunkSlot(index: Int, chunk: FrozenMarkdownChunk): String =
    "frozen-chunk:$index:${chunk.fingerprint}"

private fun frozenChunkHeightCacheKey(
    identity: MarkdownRenderCacheIdentity?,
    chunk: FrozenMarkdownChunk,
    profile: FrozenLayoutProfile,
    index: Int
): String? {
    identity ?: return null
    return buildString(192) {
        append(identity.conversationId).append('|').append(identity.messageId).append('|')
        append(identity.blockId).append("|chunk|").append(index).append('|').append(chunk.fingerprint).append('|')
        append(profile.widthPx).append(':').append(profile.minWidthPx).append(':').append(profile.maxWidthPx).append(':')
        append(profile.minHeightPx).append(':').append(profile.maxHeightPx).append(':')
        append(profile.densityMilli).append(':').append(profile.fontScaleMilli).append(':')
        append(profile.direction.ordinal).append(':').append(profile.typographyKey).append(':').append(profile.optionsKey)
    }
}

private const val SMALL_FROZEN_CHUNK_LIMIT = 12
private const val MAX_FROZEN_LAYOUT_PROFILES = 2
private const val FROZEN_OVERSCAN_FRACTION = 0.5f
private const val INITIAL_FROZEN_VIEWPORT_PX = 1_200
private const val MAX_FROZEN_LAYOUT_HEIGHT_PX = 16_000_000
private val FROZEN_CHUNK_SPACING = 8.dp
private const val FROZEN_TAIL_SLOT = "frozen-chunk-tail"
