package org.starfall.generative.features.home.chat.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeMarkdownParagraphTest {
    @Test
    fun chunkingPreservesTheCompleteSourceWithoutOversizedRows() {
        val source = buildString {
            repeat(4_107) { append('a' + it % 26) }
        }

        val chunks = chunkLargeMarkdownParagraph(source, chunkSize = 256)

        assertTrue(chunks.all { it.length <= 256 })
        assertEquals(source, chunks.joinToString(separator = ""))
        assertEquals(17, chunks.size)
    }

    @Test
    fun shortInputStillProducesOneExactChunk() {
        assertEquals(listOf("short"), chunkLargeMarkdownParagraph("short", chunkSize = 256))
        assertEquals(emptyList<String>(), chunkLargeMarkdownParagraph("", chunkSize = 256))
    }

    @Test
    fun sourceRangeIsChunkedWithoutIncludingSurroundingContent() {
        val source = "prefix abcdef suffix"
        assertEquals(listOf("ab", "cd", "ef"), chunkLargeMarkdownParagraph(source, 7, 13, chunkSize = 2))
    }

    @Test
    fun chunkBoundariesDoNotSplitUnicodeSurrogatePairs() {
        val source = "abc\uD83D\uDE00z"
        val chunks = chunkLargeMarkdownParagraph(source, chunkSize = 4)
        assertEquals(source, chunks.joinToString(separator = ""))
        assertTrue(chunks.none { it == "\uD83D" || it == "\uDE00" })
    }
}
