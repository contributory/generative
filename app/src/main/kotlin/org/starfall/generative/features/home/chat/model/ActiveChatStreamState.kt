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
    val lineCount: Int,
    val queuedLines: Int,
    val lineWaitMillis: Long,
    /** Time to the state-publication call; actual on-screen latency still needs frame instrumentation. */
    val inputToVisibleLatencyMillis: Long,
    val terminal: Boolean
)

data class StreamMetricsSnapshot(
    val batchCount: Long = 0L,
    val committedLines: Long = 0L,
    val maxQueuedLines: Int = 0,
    val totalLineWaitMillis: Long = 0L,
    val totalInputToVisibleLatencyMillis: Long = 0L
)

/** A persistent, append-only link to one committed display segment. Equality is referential on purpose. */
internal class StreamTextSegment(
    val sequence: Long,
    val text: String,
    val previous: StreamTextSegment?
)

/**
 * Immutable handle to the display text for one message. Appending adds one small linked segment instead
 * of copying the full accumulated response; [materialize] is reserved for the active UI renderer.
 */
internal class StreamTextSnapshot private constructor(
    val baseText: String,
    val latestSegment: StreamTextSegment?,
    val length: Long
) {
    fun append(text: String, sequence: Long): StreamTextSnapshot {
        if (text.isEmpty()) return this
        return StreamTextSnapshot(
            baseText = baseText,
            latestSegment = StreamTextSegment(sequence, text, latestSegment),
            length = length + text.length.toLong()
        )
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
            StreamTextSnapshot(baseText, latestSegment = null, length = baseText.length.toLong())
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
