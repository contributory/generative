package org.starfall.generative.features.home.chat.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockHeightIndexTest {
    @Test
    fun prefixLookupFindsTheFirstBlockWhoseBottomPassesTheOffset() {
        val index = MarkdownBlockHeightIndex(intArrayOf(10, 20, 30))

        assertEquals(0, index.blockAt(0))
        assertEquals(0, index.blockAt(9))
        assertEquals(1, index.blockAt(10))
        assertEquals(1, index.blockAt(29))
        assertEquals(2, index.blockAt(30))
        assertEquals(3, index.blockAt(60))
        assertEquals(10, index.topOf(1))
        assertEquals(30, index.topOf(2))
        assertEquals(60, index.totalHeight)
    }

    @Test
    fun measuredHeightChangesKeepPrefixAndOffsetQueriesConsistent() {
        val index = MarkdownBlockHeightIndex(intArrayOf(10, 20, 30))

        assertTrue(index.update(1, 40))
        assertEquals(50, index.topOf(2))
        assertEquals(80, index.totalHeight)
        assertEquals(2, index.blockAt(50))
        assertTrue(!index.update(1, 40))
        assertTrue(!index.update(5, 10))
    }

    @Test
    fun emptyAndNonPositiveEstimatesRemainSafe() {
        val empty = MarkdownBlockHeightIndex(intArrayOf())
        assertEquals(0, empty.blockAt(10))
        assertEquals(0, empty.totalHeight)

        val safe = MarkdownBlockHeightIndex(intArrayOf(0, -1))
        assertEquals(2, safe.totalHeight)
        assertEquals(1, safe.blockAt(1))
    }

    @Test
    fun heightEstimateCountsHardLineBreaksInsteadOfTreatingThemAsOrdinaryCharacters() {
        val oneLine = estimateMarkdownBlockHeight(
            sourceLength = 5,
            typeName = "PARAGRAPH",
            widthPx = 400,
            density = 1f,
            spacingPx = 8
        )
        val threeShortLines = estimateMarkdownBlockHeight(
            sourceLength = 5,
            typeName = "PARAGRAPH",
            widthPx = 400,
            density = 1f,
            spacingPx = 8,
            sourceText = "a\nb\nc"
        )

        assertTrue("hard line breaks must contribute to the estimated height", threeShortLines > oneLine)
        assertEquals(3, estimateWrappedMarkdownLineCount("a\nb\nc", charsPerLine = 53))
    }

    @Test
    fun heightEstimateCountsWrappingSeparatelyForEachPhysicalLine() {
        val source = "abcdefghij\nxy"
        assertEquals(4, estimateWrappedMarkdownLineCount(source, charsPerLine = 4))
    }

    @Test
    fun appendGrowsGeometricallyAndKeepsPrefixQueriesCorrect() {
        val index = MarkdownBlockHeightIndex(intArrayOf())
        val heights = ArrayList<Int>()

        repeat(1_025) { ordinal ->
            val height = ordinal % 17 + 1
            heights += height
            index.append(height)
        }

        assertEquals(heights.size, index.size)
        assertEquals(heights.sum(), index.totalHeight)
        for (block in heights.indices step 37) {
            assertEquals(heights.take(block).sum(), index.topOf(block))
        }
        val prefixHeights = IntArray(heights.size + 1)
        heights.forEachIndexed { block, height -> prefixHeights[block + 1] = prefixHeights[block] + height }
        for (offset in 0..index.totalHeight step 113) {
            val expected = heights.indices.firstOrNull { block -> prefixHeights[block + 1] > offset }
                ?: heights.size
            assertEquals(expected, index.blockAt(offset))
        }

        val oldTotal = index.totalHeight
        assertTrue(index.update(512, 80))
        assertEquals(oldTotal - heights[512] + 80, index.totalHeight)
        assertTrue(index.update(index.size, 24).not())

        val lastHeight = heights.last()
        assertTrue(index.removeLast())
        assertEquals(oldTotal - heights[512] + 80 - lastHeight, index.totalHeight)
        assertEquals(heights.lastIndex, index.size)
        while (index.removeLast()) Unit
        assertEquals(0, index.size)
        assertTrue(!index.removeLast())
    }
}
