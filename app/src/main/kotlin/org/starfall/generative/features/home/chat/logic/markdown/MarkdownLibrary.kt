package org.starfall.generative.features.home.chat.logic.markdown

import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

internal suspend fun parseLibraryMarkdown(content: String): State {
    return MarkdownRenderWork.run { parseMarkdown(content) }
}

internal data class MarkdownFence(val language: String, val code: String, val closed: Boolean)

/** Source-backed fence metadata lets large code blocks preview without first copying the whole block. */
internal data class MarkdownFenceSource(
    val language: String,
    val codeStartOffset: Int,
    val codeEndOffset: Int,
    val closed: Boolean
)

internal fun markdownFenceSource(content: String, node: ASTNode): MarkdownFenceSource {
    val tokens = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    val start = tokens.firstOrNull()?.startOffset ?: 0
    val end = tokens.lastOrNull()?.endOffset ?: start
    return MarkdownFenceSource(
        language = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.getTextInNode(content)?.toString().orEmpty(),
        codeStartOffset = start,
        codeEndOffset = end,
        closed = node.children.any { it.type == MarkdownTokenTypes.CODE_FENCE_END }
    )
}

/** Use the library's token boundaries; preserve whitespace for copying and code preview. */
internal fun markdownFence(content: String, node: ASTNode): MarkdownFence {
    val source = markdownFenceSource(content, node)
    val code = content.substring(
        source.codeStartOffset.coerceIn(0, content.length),
        source.codeEndOffset.coerceIn(source.codeStartOffset.coerceIn(0, content.length), content.length)
    )
    return MarkdownFence(source.language, code, source.closed)
}
