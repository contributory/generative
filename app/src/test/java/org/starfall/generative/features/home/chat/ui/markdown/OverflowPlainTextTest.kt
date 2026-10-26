package org.starfall.generative.features.home.chat.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverflowPlainTextTest {
    @Test
    fun everyCharacterSurvivesSplittingAndCompletedChunksDoNotChangeWhenTextGrows() {
        val text = (0 until 400).joinToString("\n") { "line $it with some text" }
        val before = splitOverflowText(text.take(5_000), maxChunkChars = 500)
        val after = splitOverflowText(text, maxChunkChars = 500)

        assertEquals(text, after.joinToString(""))
        assertTrue(after.all { it.length <= 500 })
        before.dropLast(1).forEachIndexed { index, chunk -> assertEquals(chunk, after[index]) }
    }

    @Test
    fun chunksEndAfterANewlineWhenOneIsAvailable() {
        val text = "a".repeat(30) + "\n" + "b".repeat(30) + "\n" + "c".repeat(30)
        val chunks = splitOverflowText(text, maxChunkChars = 40)

        assertEquals(listOf("a".repeat(30) + "\n", "b".repeat(30) + "\n", "c".repeat(30)), chunks)
    }

    @Test
    fun longLinesAndSurrogatePairsAreSplitSafely() {
        val text = "x".repeat(9) + "\uD83D\uDE00" + "y".repeat(20)
        val chunks = splitOverflowText(text, maxChunkChars = 10)

        assertEquals(text, chunks.joinToString(""))
        chunks.forEach { chunk ->
            assertTrue(!chunk.last().isHighSurrogate() && !chunk.first().isLowSurrogate())
        }
    }

    @Test
    fun emptyTextHasNoChunks() {
        assertEquals(emptyList<String>(), splitOverflowText(""))
    }
}
