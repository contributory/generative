package org.starfall.generative.features.home.chat.logic

import java.util.ArrayDeque

internal const val STREAM_CHARS_PER_BATCH = 30
internal const val STREAM_COMMIT_INTERVAL_MS = 300L

/** One immutable visible commit; ordinary stream commits contain one character chunk. */
internal data class StreamTextBatch(
    val text: String,
    val characterCount: Int,
    val queuedCharacters: Int,
    val chunkWaitMillis: Long,
    val inputToVisibleLatencyMillis: Long,
    val terminal: Boolean
)

/**
 * Buffers arbitrary provider fragments and exposes a chunk after about [charactersPerBatch] Unicode
 * code points, counting spaces, punctuation, tabs, and newlines. A surrogate pair split across input
 * fragments stays together. A short chunk is released after an input-idle timeout or an explicit boundary.
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

    /**
     * Returns how long until a short tail may be flushed, or null when a full chunk, empty buffer,
     * or incomplete trailing surrogate means the idle timer cannot release it yet.
     */
    fun millisUntilIdlePartialFlush(nowMillis: Long, idleTimeoutMillis: Long): Long? {
        if (terminal || completeChunks.isNotEmpty() || consumedOffset >= buffer.length ||
            charactersInCurrentChunk == 0 || scanOffset < buffer.length
        ) return null
        return (idleTimeoutMillis - (nowMillis - lastInputAtMillisInternal)).coerceAtLeast(0L)
    }

    /** Releases a short tail only after no new input has arrived for [idleTimeoutMillis]. */
    fun takeIdlePartialBatch(
        committedAtMillis: Long = nowMillis(),
        idleTimeoutMillis: Long
    ): StreamTextBatch? {
        if (millisUntilIdlePartialFlush(committedAtMillis, idleTimeoutMillis) != 0L) return null
        return takeAllPending(committedAtMillis)
    }

    /** Releases all buffered text, including a short chunk, without ending the stream. */
    fun takeAllPending(committedAtMillis: Long = nowMillis()): StreamTextBatch? {
        if (terminal || consumedOffset >= buffer.length) return null

        val text = buffer.substring(consumedOffset)
        val characterCount = text.codePointCount(0, text.length)
        val firstCompletedAt = completeChunks.firstOrNull()?.completedAtMillis ?: lastInputAtMillisInternal
        val lastCompletedAt = completeChunks.lastOrNull()?.completedAtMillis ?: lastInputAtMillisInternal
        consumedOffset = buffer.length
        scanOffset = buffer.length
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
