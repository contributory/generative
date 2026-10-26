package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.model.State
import org.intellij.markdown.ast.ASTNode
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.text.toComposeFontFamily
import androidx.compose.ui.unit.sp

/** Keeps ordinary Markdown nodes on the library path while bounding pathological raw HTML blocks. */
@Composable
internal fun MarkdownBlockElement(
    node: ASTNode,
    components: MarkdownComponents,
    content: String,
    includeSpacer: Boolean = true
) {
    val sourceLength = (node.endOffset - node.startOffset).coerceAtLeast(0)
    if (node.type.name != HTML_BLOCK_TYPE || sourceLength <= LARGE_HTML_BLOCK_EAGER_LIMIT) {
        MarkdownElement(node, components, content, includeSpacer)
        return
    }

    if (includeSpacer) Spacer(Modifier.height(LocalMarkdownPadding.current.block))
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    val messageFont = preferences.messageFontFamily.toComposeFontFamily()
    LargeMarkdownParagraph(
        source = content,
        startOffset = node.startOffset,
        endOffset = node.endOffset,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = messageFont,
            fontSize = bodySize,
            lineHeight = bodySize * 1.375f,
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** Same top-level order and spacing as MarkdownSuccess, with a bounded path for oversized HTML. */
@Composable
internal fun MarkdownSuccessWithBoundedHtml(
    state: State.Success,
    components: MarkdownComponents,
    modifier: Modifier
) {
    Column(modifier) {
        state.node.children.forEach { node ->
            MarkdownBlockElement(node, components, state.content)
        }
    }
}

private const val HTML_BLOCK_TYPE = "HTML_BLOCK"
private const val LARGE_HTML_BLOCK_EAGER_LIMIT = 20_000
