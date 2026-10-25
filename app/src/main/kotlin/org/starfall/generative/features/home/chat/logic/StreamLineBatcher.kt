package org.starfall.generative.features.home.chat.logic

import java.util.ArrayDeque

internal const val STREAM_LINES_PER_BATCH = 3
internal const val STREAM_COMMIT_INTERVAL_MS = 300L

/**
 * One immutable visible commit. [lineCount] can exceed the normal batch size only for a terminal flush.
 */
internal data class StreamLineBatch(
    val text: String,
    val lineCount: Int,
    val queuedLines: Int,
    val lineWaitMillis: Long,
    val inputToVisibleLatencyMillis: Long,
    val terminal: Boolean
)

/**
 * Buffers arbitrary provider fragments and exposes only complete logical lines in fixed-size batches.
 * LF, CRLF, and standalone CR are accepted as delimiters; a trailing CR remains pending until more
 * input arrives or the stream is finalized. Delimiter bytes are retained verbatim in [StreamLineBatch.text].
 *
 * The scan cursor advances only over newly appended characters. Consumed storage is compacted
 * amortized-linearly, so repeatedly draining a large queued response does not shift the remaining text
 * on every batch.
 */
internal class StreamLineBatcher(
    private val linesPerBatch: Int = STREAM_LINES_PER_BATCH,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class LineBoundary(val endOffset: Int, val completedAtMillis: Long)

    private val buffer = StringBuilder()
    private val completeLines = ArrayDeque<LineBoundary>()
    private var consumedOffset = 0
    private var scanOffset = 0
    private var lineStartOffset = 0
    private var lastInputAtMillis = nowMillis()
    private var terminal = false

    init {
        require(linesPerBatch > 0) { "linesPerBatch must be positive" }
    }

    val queuedCompleteLines: Int
        get() = completeLines.size

    val hasEligibleBatch: Boolean
        get() = !terminal && completeLines.size >= linesPerBatch

    val pendingCharacterCount: Int
        get() = buffer.length - consumedOffset

    /** Appends a network fragment and scans only its new suffix. */
    fun append(fragment: String) {
        check(!terminal) { "Cannot append to a finalized stream buffer" }
        if (fragment.isEmpty()) return
        buffer.append(fragment)
        lastInputAtMillis = nowMillis()
        scanAvailable(finalizing = false)
    }

    /** Returns exactly [linesPerBatch] complete lines, or null while fewer are available. */
    fun takeEligibleBatch(committedAtMillis: Long = nowMillis()): StreamLineBatch? {
        if (!hasEligibleBatch) return null

        var selectedEnd = consumedOffset
        var firstCompletedAt = 0L
        var lastCompletedAt = 0L
        repeat(linesPerBatch) { index ->
            val boundary = completeLines.removeFirst()
            if (index == 0) firstCompletedAt = boundary.completedAtMillis
            selectedEnd = boundary.endOffset
            lastCompletedAt = boundary.completedAtMillis
        }
        val text = buffer.substring(consumedOffset, selectedEnd)
        consumedOffset = selectedEnd
        val result = StreamLineBatch(
            text = text,
            lineCount = linesPerBatch,
            queuedLines = completeLines.size,
            lineWaitMillis = (committedAtMillis - firstCompletedAt).coerceAtLeast(0L),
            inputToVisibleLatencyMillis = (committedAtMillis - lastCompletedAt).coerceAtLeast(0L),
            terminal = false
        )
        compactIfNeeded()
        return result
    }

    /**
     * Immediately returns all remaining complete lines and the final unterminated line, if any.
     * Repeated terminal signals are idempotent and return null.
     */
    fun finishAndTakeRemainder(committedAtMillis: Long = nowMillis()): StreamLineBatch? {
        if (terminal) return null
        terminal = true
        scanAvailable(finalizing = true)

        if (lineStartOffset < buffer.length) {
            completeLines.addLast(LineBoundary(buffer.length, lastInputAtMillis))
            lineStartOffset = buffer.length
        }
        if (consumedOffset >= buffer.length) {
            completeLines.clear()
            return null
        }

        val firstCompletedAt = completeLines.firstOrNull()?.completedAtMillis ?: lastInputAtMillis
        val lastCompletedAt = completeLines.lastOrNull()?.completedAtMillis ?: lastInputAtMillis
        val text = buffer.substring(consumedOffset)
        val lineCount = completeLines.size
        consumedOffset = buffer.length
        completeLines.clear()
        return StreamLineBatch(
            text = text,
            lineCount = lineCount,
            queuedLines = 0,
            lineWaitMillis = (committedAtMillis - firstCompletedAt).coerceAtLeast(0L),
            inputToVisibleLatencyMillis = (committedAtMillis - lastCompletedAt).coerceAtLeast(0L),
            terminal = true
        )
    }

    private fun scanAvailable(finalizing: Boolean) {
        var index = scanOffset
        while (index < buffer.length) {
            when (buffer[index]) {
                '\n' -> {
                    index++
                    completeLines.addLast(LineBoundary(index, nowMillis()))
                    lineStartOffset = index
                }
                '\r' -> {
                    if (index + 1 >= buffer.length) {
                        if (finalizing) {
                            index++
                            completeLines.addLast(LineBoundary(index, nowMillis()))
                            lineStartOffset = index
                        } else {
                            break
                        }
                    } else if (buffer[index + 1] == '\n') {
                        // Let the LF close the CRLF pair once, preserving both characters.
                        index++
                    } else {
                        index++
                        completeLines.addLast(LineBoundary(index, nowMillis()))
                        lineStartOffset = index
                    }
                }
                else -> index++
            }
        }
        scanOffset = index
    }

    private fun compactIfNeeded() {
        if (consumedOffset < COMPACT_AFTER_CHARS || consumedOffset < buffer.length - consumedOffset) return
        val removed = consumedOffset
        buffer.delete(0, removed)
        scanOffset -= removed
        lineStartOffset -= removed
        consumedOffset = 0
        if (completeLines.isNotEmpty()) {
            val adjusted = completeLines.map { it.copy(endOffset = it.endOffset - removed) }
            completeLines.clear()
            completeLines.addAll(adjusted)
        }
    }

    private companion object {
        const val COMPACT_AFTER_CHARS = 64 * 1024
    }
}
