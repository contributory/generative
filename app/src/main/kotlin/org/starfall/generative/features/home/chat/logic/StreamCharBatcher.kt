package org.starfall.generative.features.home.chat.logic

import java.util.ArrayDeque

internal const val STREAM_CHARS_PER_BATCH = 30
/** Minimum interval between visible stream commits. All text received in the window is committed together. */
internal const val STREAM_COMMIT_INTERVAL_MS = 50L

/** One immutable visible commit; a commit may contain all text accumulated in one render window. */
internal data class StreamTextBatch(
    val text: String,
    val characterCount: Int,
    val queuedCharacters: Int,
    val chunkWaitMillis: Long,
    val inputToVisibleLatencyMillis: Long,
    val terminal: Boolean
)

/**
 * Buffers arbitrary provider fragments while preserving Unicode boundaries. The display layer takes all
 * pending text at each render window; [charactersPerBatch] remains only as an internal scan boundary for
 * compatibility with existing metrics and tests, never as a visible flush limit.
 *
 * The scan cursor advances only over newly appended characters. Consumed storage is compacted
 * amortized-linearly, so draining a large queued response does not shift the remaining text per chunk.
 */
internal class StreamCharBatcher(
    private val charactersPerBatch: Int = STREAM_CHARS_PER_BATCH,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class ChunkBoundary(val endOffset: Int, val completedAtMillis: Long)

    private val buffer = StringBuilder()
    private val completeChunks = ArrayDeque<ChunkBoundary>()
    private var consumedOffset = 0
    private var scanOffset = 0
    private var charactersInCurrentChunk = 0
    private var lastInputAtMillisInternal = nowMillis()
    private var terminal = false

    init {
        require(charactersPerBatch > 0) { "charactersPerBatch must be positive" }
    }

    val queuedCompleteChunks: Int
        get() = completeChunks.size

    val hasPendingText: Boolean
        get() = consumedOffset < buffer.length

    /** Number of Unicode code points waiting to become visible, including complete and partial chunks. */
    val pendingCharacterCount: Int
        get() = completeChunks.size * charactersPerBatch + charactersInCurrentChunk +
            if (scanOffset < buffer.length) 1 else 0

    val hasEligibleBatch: Boolean
        get() = !terminal && completeChunks.isNotEmpty()

    /** Appends a network fragment and scans only its new suffix. */
    fun append(fragment: String) {
        check(!terminal) { "Cannot append to a finalized stream buffer" }
        if (fragment.isEmpty()) return
        buffer.append(fragment)
        lastInputAtMillisInternal = nowMillis()
        scanAvailable(finalizing = false)
    }

    /** Returns exactly one full character chunk. Queued chunks are never coalesced together. */
    fun takeEligibleBatch(committedAtMillis: Long = nowMillis()): StreamTextBatch? {
        if (!hasEligibleBatch) return null

        val boundary = completeChunks.removeFirst()
        val selectedEnd = boundary.endOffset
        val firstCompletedAt = boundary.completedAtMillis
        val lastCompletedAt = boundary.completedAtMillis
        val text = buffer.substring(consumedOffset, selectedEnd)
        consumedOffset = selectedEnd
        val result = StreamTextBatch(
            text = text,
            characterCount = text.codePointCount(0, text.length),
            queuedCharacters = pendingCharacterCount,
            chunkWaitMillis = (committedAtMillis - firstCompletedAt).coerceAtLeast(0L),
            inputToVisibleLatencyMillis = (committedAtMillis - lastCompletedAt).coerceAtLeast(0L),
            terminal = false
        )
        compactIfNeeded()
        return result
    }

    /** Releases all buffered text, including a short chunk, without ending the stream. */
    fun takeAllPending(committedAtMillis: Long = nowMillis()): StreamTextBatch? {
        if (terminal || consumedOffset >= buffer.length) return null

        // A render window may open between two provider fragments. Keep an unpaired high surrogate
        // out of the visible commit so the next fragment can complete the code point.
        val selectedEnd = if (scanOffset < buffer.length && buffer.last().isHighSurrogate()) {
            buffer.length - 1
        } else buffer.length
        if (selectedEnd <= consumedOffset) return null
        val text = buffer.substring(consumedOffset, selectedEnd)
        val characterCount = text.codePointCount(0, text.length)
        val firstCompletedAt = completeChunks.firstOrNull()?.completedAtMillis ?: lastInputAtMillisInternal
        val lastCompletedAt = completeChunks.lastOrNull()?.completedAtMillis ?: lastInputAtMillisInternal
        consumedOffset = selectedEnd
        if (selectedEnd == buffer.length) scanOffset = buffer.length
        completeChunks.clear()
        charactersInCurrentChunk = 0
        val result = StreamTextBatch(
            text = text,
            characterCount = characterCount,
            queuedCharacters = 0,
            chunkWaitMillis = (committedAtMillis - firstCompletedAt).coerceAtLeast(0L),
            inputToVisibleLatencyMillis = (committedAtMillis - lastCompletedAt).coerceAtLeast(0L),
            terminal = false
        )
        compactIfNeeded()
        return result
    }

    /** Immediately returns all remaining text; repeated terminal signals are idempotent. */
    fun finishAndTakeRemainder(committedAtMillis: Long = nowMillis()): StreamTextBatch? {
        if (terminal) return null
        terminal = true
        scanAvailable(finalizing = true)

        if (consumedOffset >= buffer.length) {
            completeChunks.clear()
            charactersInCurrentChunk = 0
            return null
        }

        val firstCompletedAt = completeChunks.firstOrNull()?.completedAtMillis ?: lastInputAtMillisInternal
        val lastCompletedAt = completeChunks.lastOrNull()?.completedAtMillis ?: lastInputAtMillisInternal
        val text = buffer.substring(consumedOffset)
        val characterCount = text.codePointCount(0, text.length)
        consumedOffset = buffer.length
        completeChunks.clear()
        charactersInCurrentChunk = 0
        return StreamTextBatch(
            text = text,
            characterCount = characterCount,
            queuedCharacters = 0,
            chunkWaitMillis = (committedAtMillis - firstCompletedAt).coerceAtLeast(0L),
            inputToVisibleLatencyMillis = (committedAtMillis - lastCompletedAt).coerceAtLeast(0L),
            terminal = true
        )
    }

    private fun scanAvailable(finalizing: Boolean) {
        var index = scanOffset
        while (index < buffer.length) {
            val current = buffer[index]
            val nextIndex = when {
                current.isHighSurrogate() && index + 1 >= buffer.length && !finalizing -> break
                current.isHighSurrogate() && index + 1 < buffer.length && buffer[index + 1].isLowSurrogate() -> index + 2
                else -> index + 1
            }
            index = nextIndex
            charactersInCurrentChunk++
            if (charactersInCurrentChunk >= charactersPerBatch) {
                completeChunks.addLast(ChunkBoundary(index, nowMillis()))
                charactersInCurrentChunk = 0
            }
        }
        scanOffset = index
    }

    private fun compactIfNeeded() {
        if (consumedOffset < COMPACT_AFTER_CHARS || consumedOffset < buffer.length - consumedOffset) return
        val removed = consumedOffset
        buffer.delete(0, removed)
        scanOffset -= removed
        consumedOffset = 0
        if (completeChunks.isNotEmpty()) {
            val adjusted = completeChunks.map { it.copy(endOffset = it.endOffset - removed) }
            completeChunks.clear()
            completeChunks.addAll(adjusted)
        }
    }

    private companion object {
        const val COMPACT_AFTER_CHARS = 64 * 1024
    }
}
