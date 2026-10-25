package org.starfall.generative.features.home.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLineBatcherTest {
    @Test
    fun commitsExactlyTheConfiguredLinesAndRetainsSurplus() {
        var now = 10L
        val batcher = StreamLineBatcher(linesPerBatch = 3) { now }

        batcher.append("one\r")
        assertFalse(batcher.hasEligibleBatch)
        batcher.append("\ntwo\r\nthree\nfour\n")
        assertTrue(batcher.hasEligibleBatch)

        val first = batcher.takeEligibleBatch(committedAtMillis = 20L)!!
        assertEquals("one\r\ntwo\r\nthree\n", first.text)
        assertEquals(3, first.lineCount)
        assertEquals(1, first.queuedLines)
        assertEquals(10L, first.lineWaitMillis)
        assertEquals(10L, first.inputToVisibleLatencyMillis)
        assertFalse("surplus is retained but cannot form a second batch yet", batcher.hasEligibleBatch)

        now = 30L
        batcher.append("five\nsix\n")
        val second = batcher.takeEligibleBatch(committedAtMillis = 30L)!!
        assertEquals("four\nfive\nsix\n", second.text)
        assertEquals(3, second.lineCount)
        assertEquals(0, second.queuedLines)
    }

    @Test
    fun incompleteLinesAndUndersizedBatchesWaitForMoreInput() {
        val batcher = StreamLineBatcher(linesPerBatch = 3) { 100L }
        batcher.append("first\nsecond")
        assertEquals(1, batcher.queuedCompleteLines)
        assertFalse(batcher.hasEligibleBatch)
        assertNull(batcher.takeEligibleBatch())

        batcher.append("\nthird")
        assertEquals(2, batcher.queuedCompleteLines)
        assertFalse("two complete lines are still undersized", batcher.hasEligibleBatch)
        batcher.append("\n")
        assertEquals("first\nsecond\nthird\n", batcher.takeEligibleBatch()!!.text)
    }

    @Test
    fun terminalFlushIncludesCompleteLinesAndFinalUnterminatedLineOnlyOnce() {
        val batcher = StreamLineBatcher(linesPerBatch = 3) { 50L }
        batcher.append("one\ntwo\nlast partial")
        val final = batcher.finishAndTakeRemainder(committedAtMillis = 55L)!!

        assertEquals("one\ntwo\nlast partial", final.text)
        assertEquals(3, final.lineCount)
        assertEquals(0, final.queuedLines)
        assertTrue(final.terminal)
        assertNull(batcher.finishAndTakeRemainder(committedAtMillis = 60L))
    }

    @Test
    fun terminalFlushTreatsTrailingCarriageReturnAsOneDelimiter() {
        val batcher = StreamLineBatcher(linesPerBatch = 3) { 1L }
        batcher.append("line\r")
        assertEquals(0, batcher.queuedCompleteLines)

        val final = batcher.finishAndTakeRemainder(committedAtMillis = 2L)!!
        assertEquals("line\r", final.text)
        assertEquals(1, final.lineCount)
        assertNull(batcher.finishAndTakeRemainder())
    }

    @Test
    fun standaloneCarriageReturnDelimitsWithoutLosingFollowingText() {
        val batcher = StreamLineBatcher(linesPerBatch = 1) { 1L }
        batcher.append("before\rafter")

        assertEquals("before\r", batcher.takeEligibleBatch()!!.text)
        val final = batcher.finishAndTakeRemainder()!!
        assertEquals("after", final.text)
        assertEquals(1, final.lineCount)
    }

    @Test
    fun emptyFinalTailDoesNotAddAnExtraLineAndConsumedContentIsNotReemitted() {
        val batcher = StreamLineBatcher(linesPerBatch = 1) { 1L }
        batcher.append("done\n")
        assertEquals("done\n", batcher.takeEligibleBatch()!!.text)
        assertNull(batcher.finishAndTakeRemainder())
    }

    @Test
    fun unicodeCodePointsArePreservedAcrossFragmentBoundaries() {
        val batcher = StreamLineBatcher(linesPerBatch = 1) { 1L }
        batcher.append("xin chào \uD83D")
        batcher.append("\uDE00\n")

        assertEquals("xin chào 😀\n", batcher.takeEligibleBatch()!!.text)
    }
}
