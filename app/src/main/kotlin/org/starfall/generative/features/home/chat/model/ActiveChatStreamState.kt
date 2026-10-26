package org.starfall.generative.features.home.chat.model

/** Lifecycle of a message currently being generated. */
enum class ChatStreamStatus {
    STREAMING,
    COMPLETED,
    CANCELLED,
    FAILED
}

/** Metrics for the most recent visible commit; these contain no response text. */
data class StreamCommitMetrics(
    val characterCount: Int,
    val queuedCharacters: Int,
    val chunkWaitMillis: Long,
    /** Time to the state-publication call; actual on-screen latency still needs frame instrumentation. */
    val inputToVisibleLatencyMillis: Long,
    val terminal: Boolean
)

data class StreamMetricsSnapshot(
    val batchCount: Long = 0L,
    val committedCharacters: Long = 0L,
    val maxQueuedCharacters: Int = 0,
    val totalChunkWaitMillis: Long = 0L,
    val totalInputToVisibleLatencyMillis: Long = 0L
)

/** A persistent, append-only link to one committed display segment. Equality is referential on purpose. */
internal class StreamTextSegment(
    val sequence: Long,
    val ordinal: Long,
    val startOffset: Long,
    val endOffset: Long,
    val text: String,
    val previous: StreamTextSegment?,
    val skipPrevious: StreamTextSegment?
)

/**
 * Immutable handle to the display text for one message. Appending adds one small linked segment instead
 * of copying the full accumulated response; [materialize] is reserved for the active UI renderer.
 */
internal class StreamTextSnapshot private constructor(
    val baseText: String,
    val latestSegment: StreamTextSegment?,
    val length: Long,
    private val segmentCount: Long
) {
    fun append(text: String, sequence: Long): StreamTextSnapshot {
        if (text.isEmpty()) return this
        val ordinal = segmentCount + 1L
        val skipPrevious = if ((ordinal - 1L) % SEGMENT_SKIP_DISTANCE == 0L) {
            var ancestor = latestSegment
            repeat((SEGMENT_SKIP_DISTANCE - 1L).toInt()) { ancestor = ancestor?.previous }
            ancestor
        } else null
        val segment = StreamTextSegment(
            sequence = sequence,
            ordinal = ordinal,
            startOffset = length,
            endOffset = length + text.length,
            text = text,
            previous = latestSegment,
            skipPrevious = skipPrevious
        )
        return StreamTextSnapshot(
            baseText = baseText,
            latestSegment = segment,
            length = length + text.length.toLong(),
            segmentCount = ordinal
        )
    }

    /** Extracts a bounded source range without materializing the whole accumulated response. */
    fun slice(startOffset: Int, endOffset: Int): String {
        val start = startOffset.coerceAtLeast(0).toLong()
        val end = endOffset.toLong().coerceAtMost(length)
        if (end <= start) return ""
        val output = StringBuilder((end - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val baseEnd = minOf(end, baseText.length.toLong())
        if (start < baseEnd) {
            output.append(baseText, start.toInt(), baseEnd.toInt())
        }

        var segment = latestSegment
        while (segment != null && segment.startOffset >= end) {
            val skip = segment.skipPrevious
            segment = if (skip != null && skip.startOffset >= end) skip else segment.previous
        }
        val intersecting = ArrayList<StreamTextSegment>()
        while (segment != null && segment.endOffset > start) {
            if (segment.startOffset < end) intersecting += segment
            segment = segment.previous
        }
        for (index in intersecting.lastIndex downTo 0) {
            val part = intersecting[index]
            val from = maxOf(start, part.startOffset) - part.startOffset
            val until = minOf(end, part.endOffset) - part.startOffset
            if (until > from) output.append(part.text, from.toInt(), until.toInt())
        }
        return output.toString()
    }

    /** Returns only append-only display segments newer than [sequence], in source order. */
    fun segmentsAfter(sequence: Long): List<StreamTextSegment> {
        val reversed = ArrayList<StreamTextSegment>()
        var segment = latestSegment
        while (segment != null && segment.sequence > sequence) {
            reversed += segment
            segment = segment.previous
        }
        return reversed.asReversed()
    }

    fun prefix(maxLength: Int): String {
        if (maxLength <= 0) return ""
        if (baseText.length >= maxLength) return baseText.substring(0, maxLength)
        val remaining = maxLength - baseText.length
        return buildString(maxLength) {
            append(baseText)
            var appended = 0
            for (segment in segmentsAfter(Long.MIN_VALUE)) {
                if (appended >= remaining) break
                val count = minOf(segment.text.length, remaining - appended)
                append(segment.text, 0, count)
                appended += count
            }
        }
    }

    fun materialize(): String {
        val capacity = length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val segments = ArrayList<StreamTextSegment>()
        var segment = latestSegment
        while (segment != null) {
            segments += segment
            segment = segment.previous
        }
        return buildString(capacity) {
            append(baseText)
            for (index in segments.lastIndex downTo 0) append(segments[index].text)
        }
    }

    companion object {
        fun of(baseText: String = ""): StreamTextSnapshot =
            StreamTextSnapshot(baseText, latestSegment = null, length = baseText.length.toLong(), segmentCount = 0L)

        private const val SEGMENT_SKIP_DISTANCE = 32L
    }
}

/**
 * Narrow, message-scoped UI state for an active generation. It deliberately contains no Conversation
 * or history list; stream revisions therefore cannot invalidate navigation or stable history rows.
 */
class ActiveChatStreamState internal constructor(
    val conversationId: String,
    val messageId: String,
    val generationId: Long,
    val revision: Long,
    val status: ChatStreamStatus,
    internal val content: StreamTextSnapshot,
    internal val reasoning: StreamTextSnapshot,
    val lastCommitMetrics: StreamCommitMetrics?,
    val cumulativeMetrics: StreamMetricsSnapshot
)
