package org.starfall.generative.home.chat.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.starfall.generative.home.chat.logic.markdown.MathPiece
import org.starfall.generative.home.chat.logic.markdown.MathSplitter
import org.starfall.generative.home.chat.logic.markdown.markdownHeadingContent

/** Keeps the library heading renderer for plain text and routes math headings through inline LaTeX. */
@Composable
internal fun RenderMarkdownHeading(
    model: MarkdownComponentModel,
    style: TextStyle,
    latexMode: String,
    fillMaxWidth: Boolean,
    contentChildType: IElementType = MarkdownTokenTypes.ATX_CONTENT
) {
    val text = remember(model.content, model.node, contentChildType) {
        markdownHeadingContent(model.content, model.node, contentChildType)
    }
    val mathPieces = remember(text, latexMode) {
        MathSplitter.split(text, latexMode).takeIf { pieces -> pieces.any { it is MathPiece.Math } }
    }
    if (mathPieces == null) {
        MarkdownHeader(
            content = model.content,
            node = model.node,
            style = style,
            contentChildType = contentChildType
        )
    } else {
        RenderMathParagraph(
            pieces = mathPieces,
            style = style,
            modifier = Modifier.semantics { heading() },
            fillMaxWidth = fillMaxWidth
        )
    }
}
