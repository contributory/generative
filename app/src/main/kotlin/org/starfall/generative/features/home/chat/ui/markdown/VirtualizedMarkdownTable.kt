package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.features.home.chat.ui.MarkdownBlockHeightCache
import org.starfall.generative.features.home.chat.ui.MarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackMarkdownVirtualizedScope
import org.starfall.generative.features.home.chat.ui.TrackVirtualizedLayout
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import org.starfall.generative.features.home.chat.logic.markdown.MathPiece
import org.starfall.generative.features.home.chat.logic.markdown.MathSplitter

@Composable
internal fun RenderVirtualizedMarkdownTable(
    content: String,
    rows: List<TableRow>,
    identity: MarkdownRenderCacheIdentity?,
    preferencesKey: Int,
    typographyKey: Int,
    settings: AnnotatorSettings,
    baseStyle: TextStyle,
    headerStyle: TextStyle
) {
    val header = remember(rows) { rows.firstOrNull { it.header } }
    val bodyRows = remember(rows) { rows.filterNot { it.header } }
    val columnCount = remember(rows) { rows.maxOfOrNull { it.cells.size } ?: 0 }
    if (columnCount == 0) return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val maxCellContentPx = with(density) { (MAX_TABLE_COLUMN_WIDTH - TABLE_CELL_HORIZONTAL_PADDING * 2).toPx() }
    val columnWidths = remember(
        rows, content, preferencesKey, typographyKey, baseStyle, headerStyle,
        density.density, density.fontScale
    ) {
        estimateTableColumnWidths(
            rows = rows,
            content = content,
            columnCount = columnCount,
            maxContentWidthPx = maxCellContentPx,
            density = density.density,
            measurer = measurer,
            baseStyle = baseStyle,
            headerStyle = headerStyle
        )
    }
    val tableWidth = columnWidths.fold(0.dp) { width, columnWidth -> width + columnWidth }
    val providedFingerprint = LocalMarkdownContentFingerprint.current
    val contentFingerprint = remember(content, providedFingerprint) {
        providedFingerprint ?: markdownContentFingerprint(content)
    }
    RenderVirtualizedMarkdownTableFrame(
        content = content,
        header = header,
        bodyRows = bodyRows,
        columnWidths = columnWidths,
        tableWidth = tableWidth,
        identity = identity,
        contentFingerprint = contentFingerprint,
        preferencesKey = preferencesKey,
        typographyKey = typographyKey,
        settings = settings,
        baseStyle = baseStyle,
        headerStyle = headerStyle
    )
}

@Composable
private fun RenderVirtualizedMarkdownTableFrame(
    content: String,
    header: TableRow?,
    bodyRows: List<TableRow>,
    columnWidths: List<Dp>,
    tableWidth: Dp,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    preferencesKey: Int,
    typographyKey: Int,
    settings: AnnotatorSettings,
    baseStyle: TextStyle,
    headerStyle: TextStyle
) {
    val border = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val headerBackground = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val viewport = LocalMarkdownViewportState.current
    val rootTop = remember { mutableFloatStateOf(Float.NaN) }
    val measureRevision = remember { mutableIntStateOf(0) }
    TrackVirtualizedLayout(measureRevision, rootTop)
    val indexes = remember(contentFingerprint, bodyRows, columnWidths, typographyKey, preferencesKey) {
        LinkedHashMap<TableLayoutProfile, MarkdownBlockHeightIndex>()
    }

    GatedSelectionContainer {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
            Column(Modifier.clip(shape).border(1.dp, border, shape)) {
                header?.let { row ->
                    MarkdownTableRowContent(
                        content = content,
                        row = row,
                        rowIndex = 0,
                        columnWidths = columnWidths,
                        settings = settings,
                        baseStyle = baseStyle,
                        headerStyle = headerStyle,
                        headerBackground = headerBackground
                    )
                }
                SubcomposeLayout(
                    modifier = Modifier
                        .width(tableWidth)
                        .onGloballyPositioned { coordinates ->
                            val top = coordinates.boundsInRoot().top
                            if (rootTop.floatValue.isNaN() || kotlin.math.abs(rootTop.floatValue - top) >= 0.5f) {
                                rootTop.floatValue = top
                            }
                        }
                ) { constraints ->
                    @Suppress("UNUSED_VARIABLE") val observedRevision = measureRevision.intValue
                    val context = prepareMarkdownTableLayout(
                        constraints = constraints,
                        bodyRows = bodyRows,
                        columnWidths = columnWidths,
                        identity = identity,
                        contentFingerprint = contentFingerprint,
                        viewportBounds = viewport?.bounds,
                        rootTop = rootTop.floatValue,
                        density = density,
                        direction = layoutDirection,
                        typographyKey = typographyKey,
                        preferencesKey = preferencesKey,
                        indexes = indexes
                    )
                    val measured = measureVisibleMarkdownTableRows(
                        context = context,
                        bodyRows = bodyRows,
                        content = content,
                        identity = identity,
                        contentFingerprint = contentFingerprint,
                        columnWidths = columnWidths,
                        settings = settings,
                        baseStyle = baseStyle,
                        headerStyle = headerStyle,
                        headerBackground = headerBackground,
                        measureRevision = { measureRevision.intValue++ }
                    )
                    val layoutHeight = constraints.constrainHeight(
                        context.heightIndex.totalHeight.coerceAtMost(MAX_TABLE_LAYOUT_HEIGHT_PX)
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
    }
}

private data class MarkdownTableViewportLayout(
    val profile: TableLayoutProfile,
    val heightIndex: MarkdownBlockHeightIndex,
    val layoutWidth: Int,
    val visibleRows: List<Int>
)

private fun prepareMarkdownTableLayout(
    constraints: Constraints,
    bodyRows: List<TableRow>,
    columnWidths: List<Dp>,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    viewportBounds: androidx.compose.ui.geometry.Rect?,
    rootTop: Float,
    density: androidx.compose.ui.unit.Density,
    direction: LayoutDirection,
    typographyKey: Int,
    preferencesKey: Int,
    indexes: LinkedHashMap<TableLayoutProfile, MarkdownBlockHeightIndex>
): MarkdownTableViewportLayout {
    val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
    val profile = TableLayoutProfile(
        widthPx = width,
        columnWidthsHash = columnWidths.hashCode(),
        minWidthPx = constraints.minWidth,
        maxWidthPx = constraints.maxWidth,
        densityMilli = (density.density * 1_000f).toInt(),
        fontScaleMilli = (density.fontScale * 1_000f).toInt(),
        direction = direction,
        typographyKey = typographyKey,
        preferencesKey = preferencesKey
    )
    val heightIndex = indexes.getOrPut(profile) {
        createMarkdownTableHeightIndex(bodyRows, columnWidths, identity, contentFingerprint, profile, density)
            .also { if (indexes.size >= MAX_TABLE_LAYOUT_PROFILES) indexes.clear() }
    }
    val totalHeight = heightIndex.totalHeight.coerceAtMost(MAX_TABLE_LAYOUT_HEIGHT_PX)
    return MarkdownTableViewportLayout(
        profile = profile,
        heightIndex = heightIndex,
        layoutWidth = constraints.constrainWidth(width),
        visibleRows = visibleMarkdownTableRows(heightIndex, bodyRows.size, totalHeight, viewportBounds, rootTop)
    )
}

private fun createMarkdownTableHeightIndex(
    bodyRows: List<TableRow>,
    columnWidths: List<Dp>,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    profile: TableLayoutProfile,
    density: androidx.compose.ui.unit.Density
): MarkdownBlockHeightIndex {
    val cellVerticalPaddingPx = with(density) { (TABLE_CELL_VERTICAL_PADDING * 2).roundToPx() }
    val heights = IntArray(bodyRows.size) { index ->
        val row = bodyRows[index]
        val key = tableRowCacheKey(identity, contentFingerprint, profile, index, row)
        MarkdownBlockHeightCache.get(key) ?: estimateTableRowHeight(
            row = row,
            columnWidths = columnWidths,
            density = density.density,
            cellVerticalPaddingPx = cellVerticalPaddingPx
        )
    }
    return MarkdownBlockHeightIndex(heights)
}

private fun visibleMarkdownTableRows(
    heightIndex: MarkdownBlockHeightIndex,
    rowCount: Int,
    totalHeight: Int,
    viewportBounds: androidx.compose.ui.geometry.Rect?,
    rootTop: Float
): List<Int> {
    val visibleStart = if (viewportBounds == null || rootTop.isNaN()) 0
    else (viewportBounds.top - rootTop).toInt()
    val visibleEnd = if (viewportBounds == null || rootTop.isNaN()) INITIAL_TABLE_VIEWPORT_PX
    else (viewportBounds.bottom - rootTop).toInt()
    val viewportHeight = if (viewportBounds == null) INITIAL_TABLE_VIEWPORT_PX
    else (viewportBounds.bottom - viewportBounds.top).toInt().coerceAtLeast(1)
    val overscan = (viewportHeight * TABLE_OVERSCAN_FRACTION).toInt().coerceAtLeast(1)
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

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureVisibleMarkdownTableRows(
    context: MarkdownTableViewportLayout,
    bodyRows: List<TableRow>,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    columnWidths: List<Dp>,
    settings: AnnotatorSettings,
    baseStyle: TextStyle,
    headerStyle: TextStyle,
    headerBackground: androidx.compose.ui.graphics.Color,
    measureRevision: () -> Unit
): List<Pair<Int, androidx.compose.ui.layout.Placeable>> = context.visibleRows.map { bodyIndex ->
    measureMarkdownTableRow(
        bodyIndex = bodyIndex,
        row = bodyRows[bodyIndex],
        context = context,
        content = content,
        identity = identity,
        contentFingerprint = contentFingerprint,
        columnWidths = columnWidths,
        settings = settings,
        baseStyle = baseStyle,
        headerStyle = headerStyle,
        headerBackground = headerBackground,
        measureRevision = measureRevision
    )
}

private fun androidx.compose.ui.layout.SubcomposeMeasureScope.measureMarkdownTableRow(
    bodyIndex: Int,
    row: TableRow,
    context: MarkdownTableViewportLayout,
    content: String,
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    columnWidths: List<Dp>,
    settings: AnnotatorSettings,
    baseStyle: TextStyle,
    headerStyle: TextStyle,
    headerBackground: androidx.compose.ui.graphics.Color,
    measureRevision: () -> Unit
): Pair<Int, androidx.compose.ui.layout.Placeable> {
    val cacheKey = tableRowCacheKey(identity, contentFingerprint, context.profile, bodyIndex, row)
    val rowIndex = bodyIndex + 1
    val placeable = subcompose("table-row:${tableRowSlot(bodyIndex, row)}") {
        TrackMarkdownVirtualizedScope(MarkdownVirtualizedScope.TABLE_ROW)
        Box(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                if (context.heightIndex.update(rowIndex - 1, size.height)) {
                    MarkdownBlockHeightCache.put(cacheKey, size.height)
                    measureRevision()
                }
            }
        ) {
            MarkdownTableRowContent(
                content = content,
                row = row,
                rowIndex = rowIndex,
                columnWidths = columnWidths,
                settings = settings,
                baseStyle = baseStyle,
                headerStyle = headerStyle,
                headerBackground = headerBackground
            )
        }
    }.single().measure(Constraints(
        minWidth = context.profile.widthPx,
        maxWidth = context.profile.widthPx,
        minHeight = 0,
        maxHeight = Constraints.Infinity
    ))
    return bodyIndex to placeable
}

@Composable
private fun MarkdownTableRowContent(
    content: String,
    row: TableRow,
    rowIndex: Int,
    columnWidths: List<Dp>,
    settings: AnnotatorSettings,
    baseStyle: TextStyle,
    headerStyle: TextStyle,
    headerBackground: androidx.compose.ui.graphics.Color
) {
    val preferences = LocalCodeRenderingPreferences.current
    val cells = remember(row, content, preferences.latexMode, settings) {
        row.cells.map { cell ->
            val source = cell.getTextInNode(content).toString().trim()
            val mathPieces = MathSplitter.split(source, preferences.latexMode)
                .takeIf { pieces -> pieces.any { it is MathPiece.Math } }
            TableCellContent(
                annotated = if (mathPieces == null) {
                    buildAnnotatedString { buildMarkdownAnnotatedString(content, cell, settings) }.trimmed()
                } else null,
                mathPieces = mathPieces
            )
        }
    }
    val columnCount = columnWidths.size
    Row(Modifier.width(columnWidths.fold(0.dp) { width, columnWidth -> width + columnWidth }).height(IntrinsicSize.Min)) {
        for (column in 0 until columnCount) {
            Box(
                Modifier.width(columnWidths[column]).fillMaxHeight()
                    .then(if (row.header) Modifier.background(headerBackground) else Modifier)
                    .border(0.5.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = TABLE_CELL_HORIZONTAL_PADDING, vertical = TABLE_CELL_VERTICAL_PADDING)
            ) {
                val cell = cells.getOrNull(column)
                if (cell != null) {
                    val style = if (row.header) headerStyle else baseStyle
                    if (cell.mathPieces != null) {
                        RenderMathParagraph(
                            pieces = cell.mathPieces,
                            style = style,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        cell.annotated?.let { text ->
                            MarkdownClickableText(text = text, style = style, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

private fun estimateTableColumnWidths(
    rows: List<TableRow>,
    content: String,
    columnCount: Int,
    maxContentWidthPx: Float,
    density: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    baseStyle: TextStyle,
    headerStyle: TextStyle
): List<Dp> {
    val maxSourceLength = IntArray(columnCount)
    rows.forEach { row ->
        row.cells.forEachIndexed { column, cell ->
            if (column < columnCount) {
                maxSourceLength[column] = maxOf(maxSourceLength[column], (cell.endOffset - cell.startOffset).coerceAtLeast(0))
            }
        }
    }
    val sampleNaturalWidths = FloatArray(columnCount)
    rows.take(TABLE_WIDTH_SAMPLE_ROWS).forEach { row ->
        row.cells.forEachIndexed { column, cell ->
            if (column < columnCount) {
                val style = if (row.header) headerStyle else baseStyle
                val source = cell.getTextInNode(content).toString().trim()
                val width = measurer.measure(AnnotatedString(source), style, softWrap = false).size.width.toFloat()
                sampleNaturalWidths[column] = maxOf(sampleNaturalWidths[column], width)
            }
        }
    }
    return (0 until columnCount).map { column ->
        val roughWidth = maxSourceLength[column] * TABLE_ESTIMATED_CHAR_WIDTH_DP * density
        val natural = maxOf(sampleNaturalWidths[column], roughWidth.coerceAtMost(maxContentWidthPx))
        Dp(natural.coerceIn(24f, maxContentWidthPx) / density) + TABLE_CELL_HORIZONTAL_PADDING * 2 + 1.dp
    }
}

private fun estimateTableRowHeight(
    row: TableRow,
    columnWidths: List<Dp>,
    density: Float,
    cellVerticalPaddingPx: Int
): Int {
    val lineHeightPx = (TABLE_ESTIMATED_LINE_HEIGHT_DP * density).toInt().coerceAtLeast(1)
    val maxLines = row.cells.mapIndexed { index, cell ->
        val widthPx = (columnWidths.getOrNull(index)?.value ?: TABLE_ESTIMATED_CHAR_WIDTH_DP)
            .times(density).toInt()
        val textWidthPx = (widthPx - (TABLE_CELL_HORIZONTAL_PADDING.value * 2 * density).toInt()).coerceAtLeast(1)
        val charsPerLine = (textWidthPx / (TABLE_ESTIMATED_CHAR_WIDTH_DP * density).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val length = (cell.endOffset - cell.startOffset).coerceAtLeast(0)
        ((length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    }.maxOrNull() ?: 1
    return (maxLines * lineHeightPx + cellVerticalPaddingPx + 1).coerceAtLeast(1)
}

private data class TableLayoutProfile(
    val widthPx: Int,
    val columnWidthsHash: Int,
    val minWidthPx: Int,
    val maxWidthPx: Int,
    val densityMilli: Int,
    val fontScaleMilli: Int,
    val direction: LayoutDirection,
    val typographyKey: Int,
    val preferencesKey: Int
)

private fun tableRowCacheKey(
    identity: MarkdownRenderCacheIdentity?,
    contentFingerprint: String,
    profile: TableLayoutProfile,
    index: Int,
    row: TableRow
): String = buildString(256) {
    append("table|").append(identity?.conversationId.orEmpty()).append('|')
    append(identity?.messageId.orEmpty()).append('|').append(identity?.blockId.orEmpty()).append('|')
    append(contentFingerprint).append('|').append(index).append(':')
    append(profile.widthPx).append(':').append(profile.columnWidthsHash).append(':')
    append(profile.minWidthPx).append(':').append(profile.maxWidthPx).append(':')
    append(profile.densityMilli).append(':').append(profile.fontScaleMilli).append(':')
    append(profile.direction.ordinal).append(':').append(profile.typographyKey).append(':')
    append(profile.preferencesKey).append(':').append(row.cells.firstOrNull()?.startOffset ?: 0).append(':')
    append(row.cells.lastOrNull()?.endOffset ?: 0)
}

private fun tableRowSlot(index: Int, row: TableRow): String =
    "$index:${row.cells.firstOrNull()?.startOffset ?: 0}:${row.cells.lastOrNull()?.endOffset ?: 0}"

private const val MAX_TABLE_LAYOUT_HEIGHT_PX = 16_000_000
private const val MAX_TABLE_LAYOUT_PROFILES = 2
private const val INITIAL_TABLE_VIEWPORT_PX = 1_200
private const val TABLE_OVERSCAN_FRACTION = 0.5f
private const val TABLE_WIDTH_SAMPLE_ROWS = 24
private const val TABLE_ESTIMATED_CHAR_WIDTH_DP = 7.5f
private const val TABLE_ESTIMATED_LINE_HEIGHT_DP = 24f
private val MAX_TABLE_COLUMN_WIDTH = 260.dp
private val TABLE_CELL_HORIZONTAL_PADDING = 10.dp
private val TABLE_CELL_VERTICAL_PADDING = 8.dp
