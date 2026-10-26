package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalOrderedListHandler
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import kotlinx.collections.immutable.persistentMapOf
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.ui.MarkdownBlockHeightCache
import org.starfall.generative.features.home.chat.ui.MarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackMarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackVirtualizedLayout

/** Virtualizes list items with the same outer chat viewport; no nested vertical LazyColumn is created. */
@Composable
internal fun VirtualizedMarkdownList(
    model: MarkdownComponentModel,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    ordered: Boolean,
    typographyKey: Int,
    optionsKey: Int
) {
    val content = model.content
    val node = model.node
    val depth = model.listDepth
    val items = remember(node) { node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM } }
    val viewport = LocalMarkdownViewportState.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val padding = LocalMarkdownPadding.current
    val itemPadding = MarkdownListItemPadding(padding.listItemTop, padding.listItemBottom)
    val rootTop = remember { mutableFloatStateOf(Float.NaN) }
    val measureRevision = remember { mutableIntStateOf(0) }
    TrackVirtualizedLayout(measureRevision, rootTop)
    val indexes = remember(node, contentFingerprint, depth, ordered, typographyKey, optionsKey) {
        LinkedHashMap<ListLayoutProfile, MarkdownBlockHeightIndex>()
    }
    val initialNumber = remember(node, content) { initialMarkdownListNumber(node, content) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = padding.listIndent * depth, top = padding.list, bottom = padding.list)
    ) {
        SubcomposeLayout(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val top = coordinates.boundsInRoot().top
                    if (rootTop.floatValue.isNaN() || kotlin.math.abs(rootTop.floatValue - top) >= 0.5f) {
                        rootTop.floatValue = top
                    }
                }
        ) { constraints ->
            @Suppress("UNUSED_VARIABLE") val observedRevision = measureRevision.intValue
            val context = prepareMarkdownListLayout(
                constraints = constraints,
                items = items,
                content = content,
                identity = identity,
                contentFingerprint = contentFingerprint,
                viewportBounds = viewport?.bounds,
                rootTop = rootTop.floatValue,
                density = density,
                direction = layoutDirection,
                itemPadding = itemPadding,
                typographyKey = typographyKey,
                optionsKey = optionsKey,
                ordered = ordered,
                depth = depth,
                indexes = indexes
            )
            val measured = measureVisibleMarkdownListItems(
                context = context,
                items = items,
                content = content,
                identity = identity,
                contentFingerprint = contentFingerprint,
                ordered = ordered,
                initialNumber = initialNumber,
                depth = depth,
                itemPadding = itemPadding,
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
}

private fun initialMarkdownListNumber(node: ASTNode, content: String): Int =
    node.findChildOfType(MarkdownElementTypes.LIST_ITEM)
        ?.getUnescapedTextInNode(content)
        ?.takeWhile(Char::isDigit)
        ?.toIntOrNull() ?: 1

private data class MarkdownListItemPadding(val top: Dp, val bottom: Dp)

private data class MarkdownListViewportLayout(
    val profile: ListLayoutProfile,
    val heightIndex: MarkdownBlockHeightIndex,
    val layoutWidth: Int,
    val visibleRows: List<Int>
)

private fun prepareMarkdownListLayout(
    constraints: Constraints,
    items: List<ASTNode>,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    viewportBounds: androidx.compose.ui.geometry.Rect?,
    rootTop: Float,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    itemPadding: MarkdownListItemPadding,
    typographyKey: Int,
    optionsKey: Int,
    ordered: Boolean,
    depth: Int,
    indexes: LinkedHashMap<ListLayoutProfile, MarkdownBlockHeightIndex>
): MarkdownListViewportLayout {
    val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
    val profile = ListLayoutProfile(
        widthPx = width,
        minWidthPx = constraints.minWidth,
        maxWidthPx = constraints.maxWidth,
        densityMilli = (density.density * 1_000f).toInt(),
        fontScaleMilli = (density.fontScale * 1_000f).toInt(),
        direction = direction,
        typographyKey = typographyKey,
        optionsKey = optionsKey,
        ordered = ordered,
        depth = depth
    )
    val heightIndex = indexes.getOrPut(profile) {
        createMarkdownListHeightIndex(items, content, identity, contentFingerprint, profile, density, itemPadding)
            .also { if (indexes.size >= MAX_LAYOUT_PROFILES_PER_MESSAGE) indexes.clear() }
    }
    val totalHeight = heightIndex.totalHeight.coerceAtMost(MAX_LAYOUT_HEIGHT_PX)
    return MarkdownListViewportLayout(
        profile = profile,
        heightIndex = heightIndex,
        layoutWidth = constraints.constrainWidth(width),
        visibleRows = visibleMarkdownListRows(heightIndex, items.size, totalHeight, viewportBounds, rootTop)
    )
}

private fun createMarkdownListHeightIndex(
    items: List<ASTNode>,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    profile: ListLayoutProfile,
    density: androidx.compose.ui.unit.Density,
    itemPadding: MarkdownListItemPadding
): MarkdownBlockHeightIndex {
    val topPaddingPx = with(density) { itemPadding.top.roundToPx() }
    val bottomPaddingPx = with(density) { itemPadding.bottom.roundToPx() }
    val heights = IntArray(items.size) { index ->
        val child = items[index]
        val key = listItemCacheKey(identity, contentFingerprint, profile, index, child)
        MarkdownBlockHeightCache.get(key) ?: estimateListItemHeight(
            child = child,
            sourceText = content.substring(
                child.startOffset.coerceIn(0, content.length),
                child.endOffset.coerceIn(child.startOffset.coerceIn(0, content.length), content.length)
            ),
            widthPx = profile.widthPx,
            density = density.density,
            topPaddingPx = topPaddingPx,
            bottomPaddingPx = bottomPaddingPx
        )
    }
    return MarkdownBlockHeightIndex(heights)
}

private fun visibleMarkdownListRows(
    heightIndex: MarkdownBlockHeightIndex,
    rowCount: Int,
    totalHeight: Int,
    viewportBounds: androidx.compose.ui.geometry.Rect?,
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

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureVisibleMarkdownListItems(
    context: MarkdownListViewportLayout,
    items: List<ASTNode>,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    ordered: Boolean,
    initialNumber: Int,
    depth: Int,
    itemPadding: MarkdownListItemPadding,
    measureRevision: () -> Unit
): List<Pair<Int, androidx.compose.ui.layout.Placeable>> = context.visibleRows.map { itemIndex ->
    measureMarkdownListItem(
        itemIndex = itemIndex,
        child = items[itemIndex],
        context = context,
        content = content,
        identity = identity,
        contentFingerprint = contentFingerprint,
        ordered = ordered,
        initialNumber = initialNumber,
        depth = depth,
        itemPadding = itemPadding,
        measureRevision = measureRevision
    )
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureMarkdownListItem(
    itemIndex: Int,
    child: ASTNode,
    context: MarkdownListViewportLayout,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    ordered: Boolean,
    initialNumber: Int,
    depth: Int,
    itemPadding: MarkdownListItemPadding,
    measureRevision: () -> Unit
): Pair<Int, androidx.compose.ui.layout.Placeable> {
    val cacheKey = listItemCacheKey(identity, contentFingerprint, context.profile, itemIndex, child)
    val placeable = subcompose("list-item:${itemSlot(itemIndex, child)}") {
        RenderVirtualizedMarkdownListItem(
            child = child,
            content = content,
            ordered = ordered,
            initialNumber = initialNumber,
            itemIndex = itemIndex,
            depth = depth,
            itemPadding = itemPadding,
            onSizeMeasured = { height ->
                if (context.heightIndex.update(itemIndex, height)) {
                    MarkdownBlockHeightCache.put(cacheKey, height)
                    measureRevision()
                }
            }
        )
    }.single().measure(Constraints(
        minWidth = 0,
        maxWidth = context.profile.widthPx,
        minHeight = 0,
        maxHeight = Constraints.Infinity
    ))
    return itemIndex to placeable
}

@Composable
private fun RenderVirtualizedMarkdownListItem(
    child: ASTNode,
    content: String,
    ordered: Boolean,
    initialNumber: Int,
    itemIndex: Int,
    depth: Int,
    itemPadding: MarkdownListItemPadding,
    onSizeMeasured: (Int) -> Unit
) {
    val components = LocalMarkdownComponents.current
    val typography = LocalMarkdownTypography.current
    TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.LIST_ITEM)
    Row(
        modifier = Modifier
            .semantics { isTraversalGroup = true }
            .fillMaxWidth()
            .padding(top = itemPadding.top, bottom = itemPadding.bottom)
            .onSizeChanged { onSizeMeasured(it.height) }
    ) {
        Box {
            RenderMarkdownListMarker(child, content, ordered, initialNumber, itemIndex, depth, typography, components)
        }
        Column {
            RenderMarkdownListChildren(child, content, depth, typography, components)
        }
    }
}

@Composable
private fun RenderMarkdownListMarker(
    child: ASTNode,
    content: String,
    ordered: Boolean,
    initialNumber: Int,
    itemIndex: Int,
    depth: Int,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    components: com.mikepenz.markdown.compose.components.MarkdownComponents
) {
    val checkbox = child.children.getOrNull(1)?.takeIf { it.type == GFMTokenTypes.CHECK_BOX }
    if (checkbox != null) {
        components.checkbox.invoke(
            MarkdownComponentModel(
                content = content,
                node = checkbox,
                typography = typography,
                extra = persistentMapOf(MARKDOWN_LIST_DEPTH_KEY to depth + 1)
            )
        )
        return
    }
    val indicator = ResolveMarkdownListIndicator(child, content, ordered, initialNumber, itemIndex, depth)
    Text(text = indicator, style = if (ordered) typography.ordered else typography.bullet)
}

@Composable
private fun ResolveMarkdownListIndicator(
    child: ASTNode,
    content: String,
    ordered: Boolean,
    initialNumber: Int,
    itemIndex: Int,
    depth: Int
): String {
    return if (ordered) {
        LocalOrderedListHandler.current.transform(
            type = MarkdownTokenTypes.LIST_NUMBER,
            bullet = child.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)?.getUnescapedTextInNode(content),
            index = itemIndex,
            listNumber = initialNumber,
            depth = depth
        )
    } else {
        LocalBulletListHandler.current.transform(
            type = MarkdownTokenTypes.LIST_BULLET,
            bullet = child.findChildOfType(MarkdownTokenTypes.LIST_BULLET)?.getUnescapedTextInNode(content),
            index = itemIndex,
            listNumber = initialNumber,
            depth = depth
        )
    }
}

@Composable
private fun RenderMarkdownListChildren(
    child: ASTNode,
    content: String,
    depth: Int,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    components: com.mikepenz.markdown.compose.components.MarkdownComponents
) {
    child.children.forEach { nestedChild ->
        RenderMarkdownListChild(nestedChild, content, depth, typography, components)
    }
}

@Composable
private fun RenderMarkdownListChild(
    child: ASTNode,
    content: String,
    depth: Int,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    components: com.mikepenz.markdown.compose.components.MarkdownComponents
) {
    when (child.type) {
        MarkdownElementTypes.ORDERED_LIST -> components.orderedList.invoke(
            nestedMarkdownListModel(child, content, depth, typography)
        )
        MarkdownElementTypes.UNORDERED_LIST -> components.unorderedList.invoke(
            nestedMarkdownListModel(child, content, depth, typography)
        )
        else -> MarkdownBlockElement(
            node = child,
            components = components,
            content = content,
            includeSpacer = false
        )
    }
}

private fun nestedMarkdownListModel(
    child: ASTNode,
    content: String,
    depth: Int,
    typography: com.mikepenz.markdown.model.MarkdownTypography
): MarkdownComponentModel = MarkdownComponentModel(
    content = content,
    node = child,
    typography = typography,
    extra = persistentMapOf(MARKDOWN_LIST_DEPTH_KEY to depth + 1)
)

private data class ListLayoutProfile(
    val widthPx: Int,
    val minWidthPx: Int,
    val maxWidthPx: Int,
    val densityMilli: Int,
    val fontScaleMilli: Int,
    val direction: LayoutDirection,
    val typographyKey: Int,
    val optionsKey: Int,
    val ordered: Boolean,
    val depth: Int
)

private fun estimateListItemHeight(
    child: ASTNode,
    sourceText: String,
    widthPx: Int,
    density: Float,
    topPaddingPx: Int,
    bottomPaddingPx: Int
): Int {
    val sourceLength = (child.endOffset - child.startOffset).coerceAtLeast(0)
    val markerWidth = (LIST_MARKER_WIDTH_DP * density).toInt()
    val characterWidth = (BODY_CHARACTER_WIDTH_DP * density).coerceAtLeast(1f)
    val charsPerLine = ((widthPx - markerWidth).coerceAtLeast(1) / characterWidth).toInt().coerceAtLeast(1)
    val lineCount = estimateWrappedMarkdownLineCount(sourceText, charsPerLine)
    return (lineCount * (BODY_LINE_HEIGHT_DP * density).toInt() + topPaddingPx + bottomPaddingPx).coerceAtLeast(1)
}

private fun listItemCacheKey(
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    profile: ListLayoutProfile,
    index: Int,
    node: ASTNode
): String = buildString(256) {
    append("list|").append(identity?.conversationId.orEmpty()).append('|')
    append(identity?.messageId.orEmpty()).append('|').append(identity?.blockId.orEmpty()).append('|')
    append(contentFingerprint).append('|').append(index).append(':')
    append(node.startOffset).append(':').append(node.endOffset).append('|')
    append(profile.widthPx).append(':').append(profile.minWidthPx).append(':').append(profile.maxWidthPx).append(':')
    append(profile.densityMilli).append(':').append(profile.fontScaleMilli).append(':')
    append(profile.direction.ordinal).append(':').append(profile.typographyKey).append(':')
    append(profile.optionsKey).append(':').append(profile.ordered).append(':').append(profile.depth)
}

private fun itemSlot(index: Int, node: ASTNode): String = "${node.type.name}:$index:${node.startOffset}:${node.endOffset}"

private const val INITIAL_VIEWPORT_ESTIMATE_PX = 1_200
private const val OVERSCAN_FRACTION = 1f
private const val MAX_LAYOUT_PROFILES_PER_MESSAGE = 2
private const val MAX_LAYOUT_HEIGHT_PX = 16_000_000
private const val BODY_CHARACTER_WIDTH_DP = 7.5f
private const val BODY_LINE_HEIGHT_DP = 24f
private const val LIST_MARKER_WIDTH_DP = 42f
private const val MARKDOWN_LIST_DEPTH_KEY = "markdown_list_depth"
