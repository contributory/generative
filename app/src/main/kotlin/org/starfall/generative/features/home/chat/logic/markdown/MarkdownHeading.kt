package org.starfall.generative.features.home.chat.logic.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/** Raw heading body without its ATX/Setext marker, suitable for the same math splitter as paragraphs. */
internal fun markdownHeadingContent(
    content: String,
    node: ASTNode,
    contentChildType: IElementType = MarkdownTokenTypes.ATX_CONTENT
): String = (node.findChildOfType(contentChildType) ?: node).getTextInNode(content).toString().trim()
