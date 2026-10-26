package org.starfall.generative.features.home.chat.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.mikepenz.markdown.model.State
import org.intellij.markdown.ast.ASTNode
import org.starfall.generative.features.home.chat.logic.markdown.parseLibraryMarkdown
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import kotlin.random.Random

class IncrementalMarkdownParserTest {
    @Test
    fun stableAstNodesAreSharedAcrossLaterAppends() {
        val parser = IncrementalMarkdownParser(MarkdownParserCancellationToken())
        val firstText = "First block.\n\n"
        val firstSource = StreamTextSnapshot.of(firstText)
        val first = parser.append(firstText, firstSource, maxUnstableTailChars = 128, maxParserAppendChars = 16)
        assertTrue(first.snapshot.stableNodes.isNotEmpty())
        val firstStableNode = first.snapshot.stableNodes.first()

        val secondText = "Second block."
        val fullSource = firstSource.append(secondText, sequence = 1)
        val second = parser.append(secondText, fullSource, maxUnstableTailChars = 128, maxParserAppendChars = 16)

        assertSame(firstStableNode, second.snapshot.stableNodes.first())
        assertEquals(fullSource.length.toInt(), second.snapshot.parsedLength)
        assertEquals(secondText.length, second.consumedChars)
    }

    @Test
    fun longUnstableBlockStopsAtTheConfiguredBoundAndReportsItsSourceStart() {
        IncrementalMarkdownMetrics.resetForTests()
        val parser = IncrementalMarkdownParser(MarkdownParserCancellationToken())
        val text = "x".repeat(400)
        val source = StreamTextSnapshot.of(text)

        val result = parser.append(
            text = text,
            source = source,
            maxUnstableTailChars = 64,
            maxParserAppendChars = 16
        )

        assertEquals(64, result.consumedChars)
        assertEquals(64, result.snapshot.parsedLength)
        assertEquals(0, result.snapshot.overflowStart)
        assertTrue(result.snapshot.unstableNodes.isNotEmpty())
        val metrics = IncrementalMarkdownMetrics.metrics()
        assertEquals(64, metrics.maxUnstableTailChars)
        assertEquals(1L, metrics.boundedTailFallbackCount)
        assertTrue(metrics.unstableTailCharsReprocessed > metrics.parserInputChars)
    }

    @Test
    fun inlineLinkTargetsArePublishedForTheMarkdownReferenceHandler() {
        val parser = IncrementalMarkdownParser(MarkdownParserCancellationToken())
        val text = "[guide](https://example.test/guide)"
        val source = StreamTextSnapshot.of(text)

        val result = parser.append(
            text = text,
            source = source,
            maxUnstableTailChars = 128,
            maxParserAppendChars = 32
        )
        result.linkUpdates.forEach { (label, destination) ->
            parser.referenceLinkHandler.store(label, destination)
        }

        assertEquals("https://example.test/guide", parser.referenceLinkHandler.find("guide"))
    }

    @Test
    fun parserCancellationIsCheckedBeforeItConsumesAnotherChunk() {
        val token = MarkdownParserCancellationToken()
        val parser = IncrementalMarkdownParser(token)
        val text = "A paragraph."
        val source = StreamTextSnapshot.of(text)
        val cancelledJob = kotlinx.coroutines.Job().apply { cancel() }
        token.ownerJob = cancelledJob

        try {
            parser.append(text, source, maxUnstableTailChars = 128, maxParserAppendChars = 8)
            throw AssertionError("Expected cancellation")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(0, parser.file.endOffset)
        }
    }

    @Test
    fun allSplitPointsOfRepresentativeMarkdownMatchTheFinalFullParse() = runBlocking {
        val fixtures = listOf(
            "Hello \uD83D\uDE00.\r\n\r\nSecond paragraph.\r\n",
            "# Heading\n\nA **bold** and _italic_ paragraph with `code`.\n\n> quote\n",
            "- outer\n  - nested\n  - sibling\n\n1. ordered\n2. next\n",
            "```kotlin\nval answer = 42\n\nprintln(answer)\n```\n\nAfter fence.\n",
            "| Name | Value |\n| --- | ---: |\n| first | 1 |\n| second | 2 |\n",
            "<section>\n<div>html</div>\n</section>\n\n\$inline math\$ and [guide](https://example.test).\n",
            "[manual][guide]\n\n[guide]: https://example.test/reference\n"
        )

        fixtures.forEach { text ->
            for (split in 0..text.length) {
                assertMatchesFullParse(text, listOf(split))
            }
        }
    }

    @Test
    fun randomizedFragmentBoundariesMatchTheFinalFullParse() = runBlocking {
        val fixtures = listOf(
            "A paragraph with **emphasis** and a [link](https://example.test).\n\nNext.\n",
            "~~~text\nline one\n\nline two\n~~~\n\n- item one\n- item two\n",
            "| A | B |\n| --- | --- |\n| α | 😀 |\n\n<div>ok</div>\n"
        )
        val random = Random(0x51A7)
        repeat(30) {
            val text = fixtures[it % fixtures.size]
            val boundaries = (1 until text.length).filter { random.nextBoolean() }
            assertMatchesFullParse(text, boundaries)
        }
    }

    private suspend fun assertMatchesFullParse(text: String, boundaries: List<Int>) {
        val parser = IncrementalMarkdownParser(MarkdownParserCancellationToken())
        var source = StreamTextSnapshot.of()
        var cursor = 0
        var sequence = 0L
        (boundaries + text.length).distinct().sorted().forEach { end ->
            val fragment = text.substring(cursor, end)
            if (fragment.isNotEmpty()) {
                sequence++
                source = source.append(fragment, sequence)
                val result = parser.append(
                    text = fragment,
                    source = source,
                    maxUnstableTailChars = text.length + 1,
                    maxParserAppendChars = 32
                )
                assertEquals(fragment.length, result.consumedChars)
            }
            cursor = end
        }
        val expected = parseLibraryMarkdown(text) as State.Success
        assertEquals(astShape(expected.node), astShape(parser.file))
    }

    private fun astShape(root: ASTNode): String = buildString {
        fun appendNode(node: ASTNode) {
            append('(').append(node.type.name).append(':').append(node.startOffset).append('-').append(node.endOffset)
            node.children.forEach(::appendNode)
            append(')')
        }
        appendNode(root)
    }
}
