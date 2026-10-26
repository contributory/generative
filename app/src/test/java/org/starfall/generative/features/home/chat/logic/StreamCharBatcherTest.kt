package org.starfall.generative.features.home.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCharBatcherTest {
    @Test
    fun commitsEveryConfiguredCharacterIncludingWhitespaceAndRetainsThePartialTail() {
        var now = 10L
        val batcher = StreamCharBatcher(charactersPerBatch = 5) { now }

        batcher.append("a b")
        assertFalse(batcher.hasEligibleBatch)
        batcher.append(" cdef")
        assertTrue(batcher.hasEligibleBatch)

        val first = batcher.takeEligibleBatch(committedAtMillis = 20L)!!
        assertEquals("a b c", first.text)
        assertEquals(5, first.characterCount)
        assertEquals(3, first.queuedCharacters)
        assertEquals(10L, first.chunkWaitMillis)
        assertEquals(10L, first.inputToVisibleLatencyMillis)
        assertFalse(batcher.hasEligibleBatch)

        now = 30L
        batcher.append("gh")
        val second = batcher.takeEligibleBatch(committedAtMillis = 30L)!!
        assertEquals("defgh", second.text)
        assertEquals(5, second.characterCount)
        assertEquals(0, second.queuedCharacters)
    }

    @Test
    fun queuedCharacterChunksAreNeverCoalescedTogether() {
        val batcher = StreamCharBatcher(charactersPerBatch = 5) { 100L }
        batcher.append("1234567890abcdefghij")

        assertEquals("12345", batcher.takeEligibleBatch()!!.text)
        assertEquals(3, batcher.queuedCompleteChunks)
        val second = batcher.takeEligibleBatch()!!
        assertEquals("67890", second.text)
        assertEquals(5, second.characterCount)
        assertEquals(2, batcher.queuedCompleteChunks)
    }

    @Test
    fun partialTailCanBeReleasedAfterThreeHundredMillisecondsWithoutInput() {
        var now = 100L
        val batcher = StreamCharBatcher(charactersPerBatch = 30) { now }
        batcher.append("short tail")

        assertEquals(300L, batcher.millisUntilIdlePartialFlush(now, 300L))
        assertNull(batcher.takeIdlePartialBatch(committedAtMillis = 399L, idleTimeoutMillis = 300L))
        assertEquals(1L, batcher.millisUntilIdlePartialFlush(399L, 300L))

        now = 400L
        val partial = batcher.takeIdlePartialBatch(committedAtMillis = now, idleTimeoutMillis = 300L)!!
        assertEquals("short tail", partial.text)
        assertEquals(10, partial.characterCount)
        assertFalse(partial.terminal)
        assertFalse(batcher.hasPendingText)
    }

    @Test
    fun defaultBatchTargetIsThirtyCharactersAndNewlinesCountAsCharacters() {
        val batcher = StreamCharBatcher { 1L }
        val input = "line 1\n" + "x".repeat(23)
        assertEquals(30, input.length)
        batcher.append(input)
        assertTrue(batcher.hasEligibleBatch)
        val first = batcher.takeEligibleBatch()!!
        assertEquals(input, first.text)
        assertEquals(30, first.characterCount)
    }

    @Test
    fun terminalFlushIncludesShortTailOnce() {
        val batcher = StreamCharBatcher(charactersPerBatch = 5) { 50L }
        batcher.append("ab\ncd ")
        val first = batcher.takeEligibleBatch(committedAtMillis = 52L)!!
        assertEquals("ab\ncd", first.text)
        assertEquals(5, first.characterCount)

        val final = batcher.finishAndTakeRemainder(committedAtMillis = 55L)!!
        assertEquals(" ", final.text)
        assertEquals(1, final.characterCount)
        assertEquals(0, final.queuedCharacters)
        assertTrue(final.terminal)
        assertNull(batcher.finishAndTakeRemainder(committedAtMillis = 60L))
    }

    @Test
    fun aTrailingSurrogateWaitsForItsPairAndIsNeverSplitBetweenChunks() {
        val batcher = StreamCharBatcher(charactersPerBatch = 2) { 1L }
        batcher.append("a\uD83D")
        assertFalse(batcher.hasEligibleBatch)
        batcher.append("\uDE00 ")

        val result = batcher.takeEligibleBatch()!!
        assertEquals("a😀", result.text)
        assertEquals(2, result.characterCount)
        val final = batcher.finishAndTakeRemainder()!!
        assertEquals(" ", final.text)
        assertEquals(1, final.characterCount)
    }

    @Test
    fun takeAllPendingReleasesShortTextAtAStructuralBoundaryAndKeepsAcceptingInput() {
        val batcher = StreamCharBatcher(charactersPerBatch = 10) { 10L }
        batcher.append("Let me search. ")

        val boundary = batcher.takeAllPending(committedAtMillis = 12L)!!
        assertEquals("Let me search. ", boundary.text)
        assertFalse(boundary.terminal)
        assertEquals(15, boundary.characterCount)
        assertEquals(0, batcher.queuedCompleteChunks)
        assertNull("nothing is left to release", batcher.takeAllPending())

        batcher.append("next text")
        assertEquals("next text", batcher.finishAndTakeRemainder()!!.text)
    }

    @Test
    fun takeAllPendingIsIgnoredAfterTheTerminalFlush() {
        val batcher = StreamCharBatcher(charactersPerBatch = 5) { 1L }
        batcher.append("one")
        assertEquals("one", batcher.finishAndTakeRemainder()!!.text)
        assertNull(batcher.takeAllPending())
    }
}
