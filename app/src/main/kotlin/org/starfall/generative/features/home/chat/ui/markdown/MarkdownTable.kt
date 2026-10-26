package org.starfall.generative.features.home.chat.ui.markdown

import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.starfall.generative.features.home.chat.logic.markdown.MathPiece
import org.starfall.generative.features.home.chat.logic.markdown.MathSplitter
import org.starfall.generative.features.home.chat.logic.markdown.MarkdownRenderCacheIdentity
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import org.starfall.generative.shared.ui.text.toComposeFontFamily

private val MaxTableColumnWidth = 260.dp
private val TableCellHorizontalPadding = 10.dp
private const val MAX_EAGER_TABLE_ROWS = 48

internal class TableRow(val header: Boolean, val cells: List<ASTNode>)

internal data class TableCellContent(
    val annotated: AnnotatedString?,
    val mathPieces: List<MathPiece>?
)

private fun tableRows(node: ASTNode): List<TableRow> = node.children
    .filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
    .map { row -> TableRow(row.type == GFMElementTypes.HEADER, row.children.filter { it.type == GFMTokenTypes.CELL }) }

internal fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    return subSequence(start, text.indexOfLast { !it.isWhitespace() } + 1)
}

/**
 * Markdown table that never hides content: columns size to their text up to a cap, longer cells wrap,
 * and a table wider than the message scrolls horizontally.
 */
@Composable
internal fun RenderMarkdownTable(
    model: MarkdownComponentModel,
    identity: MarkdownRenderCacheIdentity? = null,
    allowVirtualization: Boolean = false,
    typographyKey: Int = model.typography.hashCode(),
    optionsKey: Int = 0
) {
    val content = model.content
    val rows = remember(model.node) { tableRows(model.node) }
    if (rows.isEmpty()) return
    val settings = annotatorSettings()
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = preferences.messageFontFamily.toComposeFontFamily(),
        fontSize = bodySize * 0.92f,
        lineHeight = bodySize * 1.3f,
        color = MaterialTheme.colorScheme.onSurface
    )
    val headerStyle = baseStyle.copy(fontWeight = FontWeight.SemiBold)
    if (allowVirtualization && rows.size > MAX_EAGER_TABLE_ROWS) {
        RenderVirtualizedMarkdownTable(
            content = content,
            rows = rows,
            identity = identity,
            preferencesKey = optionsKey,
            typographyKey = typographyKey,
            settings = settings,
            baseStyle = baseStyle,
            headerStyle = headerStyle
        )
        return
    }
    RenderEagerMarkdownTable(content, rows, preferences.latexMode, settings, baseStyle, headerStyle)
}

@Composable
private fun RenderEagerMarkdownTable(
    content: String,
    rows: List<TableRow>,
    latexMode: String,
    settings: com.mikepenz.markdown.annotator.AnnotatorSettings,
    baseStyle: androidx.compose.ui.text.TextStyle,
    headerStyle: androidx.compose.ui.text.TextStyle
) {
    val cells = remember(content, rows, latexMode, settings) {
        buildTableCellContents(content, rows, latexMode, settings)
    }
    val columnCount = cells.maxOf { it.size }
    val columnWidths = calculateTableColumnWidths(
        rows = rows,
        cells = cells,
        columnCount = columnCount,
        density = LocalDensity.current,
        measurer = rememberTextMeasurer(),
        baseStyle = baseStyle,
        headerStyle = headerStyle
    )
    EagerMarkdownTableContent(
        rows = rows,
        cells = cells,
        columnWidths = columnWidths,
        baseStyle = baseStyle,
        headerStyle = headerStyle,
        border = MaterialTheme.colorScheme.outlineVariant,
        headerBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp)
    )
}

private fun buildTableCellContents(
    content: String,
    rows: List<TableRow>,
    latexMode: String,
    settings: com.mikepenz.markdown.annotator.AnnotatorSettings
): List<List<TableCellContent>> = rows.map { row ->
    row.cells.map { cell ->
        val source = cell.getTextInNode(content).toString().trim()
        val mathPieces = MathSplitter.split(source, latexMode)
            .takeIf { pieces -> pieces.any { it is MathPiece.Math } }
        TableCellContent(
            annotated = if (mathPieces == null) {
                buildAnnotatedString { buildMarkdownAnnotatedString(content, cell, settings) }.trimmed()
            } else null,
            mathPieces = mathPieces
        )
    }
}

private fun calculateTableColumnWidths(
    rows: List<TableRow>,
    cells: List<List<TableCellContent>>,
    columnCount: Int,
    density: androidx.compose.ui.unit.Density,
    measurer: androidx.compose.ui.text.TextMeasurer,
    baseStyle: androidx.compose.ui.text.TextStyle,
    headerStyle: androidx.compose.ui.text.TextStyle
): List<androidx.compose.ui.unit.Dp> {
    val maxColumnPx = with(density) { (MaxTableColumnWidth - TableCellHorizontalPadding * 2).toPx() }
    return (0 until columnCount).map { column ->
        val natural = cells.indices.maxOf { rowIndex ->
            val cell = cells[rowIndex].getOrNull(column) ?: return@maxOf 0f
            val style = if (rows[rowIndex].header) headerStyle else baseStyle
            val text = cell.annotated?.text ?: cell.mathPieces.orEmpty().joinToString("") { piece ->
                when (piece) {
                    is MathPiece.Text -> piece.text
                    is MathPiece.Math -> piece.source
                }
            }
            measurer.measure(AnnotatedString(text), style, softWrap = false).size.width.toFloat()
        }
        with(density) { natural.coerceIn(24f, maxColumnPx).toDp() } + TableCellHorizontalPadding * 2 + 1.dp
    }
}

@Composable
private fun EagerMarkdownTableContent(
    rows: List<TableRow>,
    cells: List<List<TableCellContent>>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    baseStyle: androidx.compose.ui.text.TextStyle,
    headerStyle: androidx.compose.ui.text.TextStyle,
    border: androidx.compose.ui.graphics.Color,
    headerBackground: androidx.compose.ui.graphics.Color,
    shape: RoundedCornerShape
) {
    GatedSelectionContainer {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
            Column(Modifier.clip(shape).border(1.dp, border, shape)) {
                rows.forEachIndexed { rowIndex, row ->
                    EagerMarkdownTableRow(
                        row = row,
                        cells = cells[rowIndex],
                        columnWidths = columnWidths,
                        baseStyle = baseStyle,
                        headerStyle = headerStyle,
                        border = border,
                        headerBackground = headerBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun EagerMarkdownTableRow(
    row: TableRow,
    cells: List<TableCellContent>,
    columnWidths: List<androidx.compose.ui.unit.Dp>,
    baseStyle: androidx.compose.ui.text.TextStyle,
    headerStyle: androidx.compose.ui.text.TextStyle,
    border: androidx.compose.ui.graphics.Color,
    headerBackground: androidx.compose.ui.graphics.Color
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        columnWidths.forEachIndexed { column, width ->
            EagerMarkdownTableCell(
                cell = cells.getOrNull(column),
                width = width,
                style = if (row.header) headerStyle else baseStyle,
                border = border,
                headerBackground = headerBackground.takeIf { row.header }
            )
        }
    }
}

@Composable
private fun EagerMarkdownTableCell(
    cell: TableCellContent?,
    width: androidx.compose.ui.unit.Dp,
    style: androidx.compose.ui.text.TextStyle,
    border: androidx.compose.ui.graphics.Color,
    headerBackground: androidx.compose.ui.graphics.Color?
) {
    Box(
        Modifier.width(width).fillMaxHeight()
            .then(if (headerBackground != null) Modifier.background(headerBackground) else Modifier)
            .border(0.5.dp, border)
            .padding(horizontal = TableCellHorizontalPadding, vertical = 8.dp)
    ) {
        if (cell != null) {
            if (cell.mathPieces != null) {
                RenderMathParagraph(pieces = cell.mathPieces, style = style, modifier = Modifier.fillMaxWidth())
            } else {
                cell.annotated?.let { text ->
                    MarkdownClickableText(text = text, style = style, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
