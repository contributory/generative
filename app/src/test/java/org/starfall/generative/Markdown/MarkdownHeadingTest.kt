package org.starfall.generative

import com.mikepenz.markdown.model.State
import kotlinx.coroutines.runBlocking
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.markdown.MathPiece
import org.starfall.generative.features.home.chat.logic.markdown.MathSplitter
import org.starfall.generative.features.home.chat.logic.markdown.markdownHeadingContent
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown

class MarkdownHeadingTest {
    private fun ASTNode.descendants(): List<ASTNode> = listOf(this) + children.flatMap { it.descendants() }

    @Test fun everyAtxLevelKeepsLatexAfterRemovingTheMarker() = runBlocking {
        for (level in 1..6) {
            val source = "${"#".repeat(level)} Result \$x^2\$"
            val state = parseLibraryMarkdown(source) as State.Success
            val heading = state.node.descendants().first { node ->
                node.children.any { it.type == MarkdownTokenTypes.ATX_CONTENT }
            }
            val body = markdownHeadingContent(state.content, heading)

            assertEquals("Result \$x^2\$", body)
            assertTrue(MathSplitter.split(body, "ON").any { it is MathPiece.Math })
        }
    }

    @Test fun setextHeadingAlsoKeepsLatex() = runBlocking {
        val source = "Result \$x^2\$\n---"
        val state = parseLibraryMarkdown(source) as State.Success
        val heading = state.node.descendants().first { node ->
            node.children.any { it.type == MarkdownTokenTypes.SETEXT_CONTENT }
        }
        val body = markdownHeadingContent(state.content, heading, MarkdownTokenTypes.SETEXT_CONTENT)

        assertEquals("Result \$x^2\$", body)
        assertTrue(MathSplitter.split(body, "ON").any { it is MathPiece.Math })
    }
}
