package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalOrderedListHandler
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
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
    val components = LocalMarkdownComponents.current
    val markdownTypography = LocalMarkdownTypography.current
    val orderedHandler = LocalOrderedListHandler.current
    val bulletHandler = LocalBulletListHandler.current
    val rootTop = remember { mutableFloatStateOf(Float.NaN) }
    val measureRevision = remember { mutableIntStateOf(0) }
    val indexes = remember(node, contentFingerprint, depth, ordered, typographyKey, optionsKey) {
        LinkedHashMap<ListLayoutProfile, MarkdownBlockHeightIndex>()
    }
    val initialNumber = remember(node, content) {
        node.findChildOfType(MarkdownElementTypes.LIST_ITEM)
            ?.getUnescapedTextInNode(content)
            ?.takeWhile(Char::isDigit)
            ?.toIntOrNull() ?: 1
    }

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
            val viewportBounds = viewport?.bounds
            val top = rootTop.floatValue
            val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
            val profile = ListLayoutProfile(
                widthPx = width,
                minWidthPx = constraints.minWidth,
                maxWidthPx = constraints.maxWidth,
                densityMilli = (density.density * 1_000f).toInt(),
                fontScaleMilli = (density.fontScale * 1_000f).toInt(),
                direction = layoutDirection,
                typographyKey = typographyKey,
                optionsKey = optionsKey,
                ordered = ordered,
                depth = depth
            )
            val heightIndex = indexes.getOrPut(profile) {
                val topPaddingPx = with(density) { padding.listItemTop.roundToPx() }
                val bottomPaddingPx = with(density) { padding.listItemBottom.roundToPx() }
                val heights = IntArray(items.size) { index ->
                    val child = items[index]
                    val key = listItemCacheKey(identity, contentFingerprint, profile, index, child)
                    MarkdownBlockHeightCache.get(key) ?: estimateListItemHeight(
                        child = child,
                        widthPx = width,
                        density = density.density,
                        topPaddingPx = topPaddingPx,
                        bottomPaddingPx = bottomPaddingPx
                    )
                }
                if (indexes.size >= MAX_LAYOUT_PROFILES_PER_MESSAGE) indexes.clear()
                MarkdownBlockHeightIndex(heights)
            }
            val totalHeight = heightIndex.totalHeight.coerceAtMost(MAX_LAYOUT_HEIGHT_PX)
            val visibleStart = if (viewportBounds == null || top.isNaN()) 0
            else (viewportBounds.top - top).toInt()
            val visibleEnd = if (viewportBounds == null || top.isNaN()) INITIAL_VIEWPORT_ESTIMATE_PX
            else (viewportBounds.bottom - top).toInt()
            val viewportHeight = if (viewportBounds == null) INITIAL_VIEWPORT_ESTIMATE_PX
            else (viewportBounds.bottom - viewportBounds.top).toInt().coerceAtLeast(1)
            val overscan = (viewportHeight * OVERSCAN_FRACTION).toInt().coerceAtLeast(1)
            val startOffset = (visibleStart - overscan).coerceAtLeast(0)
            val endOffset = (visibleEnd + overscan).coerceAtMost(totalHeight)
            val first = if (viewportBounds != null && top > viewportBounds.bottom) items.size
            else heightIndex.blockAt(startOffset)

            val measured = ArrayList<Pair<Int, androidx.compose.ui.layout.Placeable>>()
            var index = first
            while (index < items.size && heightIndex.topOf(index) < endOffset) {
                val child = items[index]
                val cacheKey = listItemCacheKey(identity, contentFingerprint, profile, index, child)
                val itemIndex = index
                val placeable = subcompose("list-item:${itemSlot(index, child)}") {
                    TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.LIST_ITEM)
                    Row(
                        modifier = Modifier
                            .semantics { isTraversalGroup = true }
                            .fillMaxWidth()
                            .padding(top = padding.listItemTop, bottom = padding.listItemBottom)
                            .onSizeChanged { size ->
                                if (heightIndex.update(itemIndex, size.height)) {
                                    MarkdownBlockHeightCache.put(cacheKey, size.height)
                                    measureRevision.intValue += 1
                                }
                            }
                    ) {
                        Box {
                            val checkbox = child.children.getOrNull(1)
                                ?.takeIf { it.type == GFMTokenTypes.CHECK_BOX }
                            if (checkbox != null) {
                                val checkboxModel = MarkdownComponentModel(
                                    content = content,
                                    node = checkbox,
                                    typography = markdownTypography,
                                    extra = persistentMapOf(MARKDOWN_LIST_DEPTH_KEY to depth + 1)
                                )
                                components.checkbox.invoke(checkboxModel)
                            } else {
                                val indicator = when {
                                    ordered -> orderedHandler.transform(
                                        type = MarkdownTokenTypes.LIST_NUMBER,
                                        bullet = child.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)
                                            ?.getUnescapedTextInNode(content),
                                        index = itemIndex,
                                        listNumber = initialNumber,
                                        depth = depth
                                    )
                                    else -> bulletHandler.transform(
                                        type = MarkdownTokenTypes.LIST_BULLET,
                                        bullet = child.findChildOfType(MarkdownTokenTypes.LIST_BULLET)
                                            ?.getUnescapedTextInNode(content),
                                        index = itemIndex,
                                        listNumber = initialNumber,
                                        depth = depth
                                    )
                                }
                                MarkdownBasicText(
                                    text = indicator,
                                    style = if (ordered) markdownTypography.ordered else markdownTypography.bullet
                                )
                            }
                        }
                        Column {
                            child.children.forEach { nestedChild ->
                                when (nestedChild.type) {
                                    MarkdownElementTypes.ORDERED_LIST -> components.orderedList.invoke(
                                        MarkdownComponentModel(
                                            content = content,
                                            node = nestedChild,
                                            typography = markdownTypography,
                                            extra = persistentMapOf(MARKDOWN_LIST_DEPTH_KEY to depth + 1)
                                        )
                                    )
                                    MarkdownElementTypes.UNORDERED_LIST -> components.unorderedList.invoke(
                                        MarkdownComponentModel(
                                            content = content,
                                            node = nestedChild,
                                            typography = markdownTypography,
                                            extra = persistentMapOf(MARKDOWN_LIST_DEPTH_KEY to depth + 1)
                                        )
                                    )
                                    else -> MarkdownBlockElement(
                                        node = nestedChild,
                                        components = components,
                                        content = content,
                                        includeSpacer = false
                                    )
                                }
                            }
                        }
                    }
                }.single().measure(Constraints(
                    minWidth = 0,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                ))
                measured += index to placeable
                index++
            }

            val layoutWidth = constraints.constrainWidth(width)
            val layoutHeight = constraints.constrainHeight(heightIndex.totalHeight.coerceAtMost(MAX_LAYOUT_HEIGHT_PX))
            layout(layoutWidth, layoutHeight) {
                var y = measured.firstOrNull()?.first?.let(heightIndex::topOf) ?: 0
                measured.forEach { (_, placeable) ->
                    placeable.place(0, y.coerceAtMost(layoutHeight))
                    y = (y + placeable.height).coerceAtMost(layoutHeight)
                }
            }
        }
    }
}

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
    widthPx: Int,
    density: Float,
    topPaddingPx: Int,
    bottomPaddingPx: Int
): Int {
    val sourceLength = (child.endOffset - child.startOffset).coerceAtLeast(0)
    val markerWidth = (LIST_MARKER_WIDTH_DP * density).toInt()
    val characterWidth = (BODY_CHARACTER_WIDTH_DP * density).coerceAtLeast(1f)
    val charsPerLine = ((widthPx - markerWidth).coerceAtLeast(1) / characterWidth).toInt().coerceAtLeast(1)
    val lineCount = ((sourceLength + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
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
private const val OVERSCAN_FRACTION = 0.5f
private const val MAX_LAYOUT_PROFILES_PER_MESSAGE = 2
private const val MAX_LAYOUT_HEIGHT_PX = 16_000_000
private const val BODY_CHARACTER_WIDTH_DP = 7.5f
private const val BODY_LINE_HEIGHT_DP = 24f
private const val LIST_MARKER_WIDTH_DP = 42f
private const val MARKDOWN_LIST_DEPTH_KEY = "markdown_list_depth"
