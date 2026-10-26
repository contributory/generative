package org.starfall.generative.features.home.chat.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.starfall.generative.features.home.chat.model.ActiveChatStreamState
import org.starfall.generative.features.home.chat.model.ChatStreamStatus
import org.starfall.generative.features.home.chat.model.StreamCommitMetrics
import org.starfall.generative.features.home.chat.model.StreamMetricsSnapshot
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot

/** Owns buffered text and the paced, message-scoped display state for one generation. */
internal class ChatStreamDisplay(
    private val conversationId: String,
    initialMessageId: String,
    private val generationId: Long,
    initialContent: String,
    initialReasoning: String,
    alreadyInsideVisibleContent: Boolean,
    private val scope: CoroutineScope,
    private val charactersPerBatch: Int = STREAM_CHARS_PER_BATCH,
    private val commitIntervalMillis: Long = STREAM_COMMIT_INTERVAL_MS,
    private val monotonicTimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val waitForInterval: suspend (Long) -> Unit = { delay(it) },
    private val publish: (String, ActiveChatStreamState) -> Unit
) {
    private var messageId = initialMessageId
    private var revision = 0L
    private var status = ChatStreamStatus.STREAMING
    private var lastCommitMetrics: StreamCommitMetrics? = null
    private var cumulativeMetrics = StreamMetricsSnapshot()
    private var lastBatchAtMillis: Long? = null
    private var nextBatchChannel = 0
    private var explicitReasoningPreferred = initialReasoning.isNotEmpty()
    private var displayedContent = StreamTextSnapshot.of(initialContent)
    private var displayedInlineReasoning = StreamTextSnapshot.of(initialReasoning)
    private var displayedExplicitReasoning = StreamTextSnapshot.of(initialReasoning)
    private var contentBatcher = newBatcher()
    private var inlineReasoningBatcher = newBatcher()
    private var explicitReasoningBatcher = newBatcher()
    private var responseSplitter = StreamResponseTextSplitter(
        initialContentLength = initialContent.length,
        alreadyInsideVisibleContent = alreadyInsideVisibleContent
    )
    private var displayFinished = false
    private var pipelineEpoch = 0L
    private var renderJob: Job? = null

    init {
        require(charactersPerBatch > 0) { "charactersPerBatch must be positive" }
        require(commitIntervalMillis >= 0L) { "commitIntervalMillis cannot be negative" }
    }

    val visibleContentLength: Int
        get() = responseSplitter.visibleContentLength

    val visibleReasoningLength: Int
        get() = responseSplitter.visibleReasoningLength

    val cumulativeStreamMetrics: StreamMetricsSnapshot
        get() = cumulativeMetrics

    fun publishInitialState() = publishCurrentState()

    fun appendText(fragment: String) {
        check(!displayFinished) { "Cannot append to a finalized stream display" }
        val pieces = responseSplitter.append(fragment)
        contentBatcher.append(pieces.content)
        inlineReasoningBatcher.append(pieces.reasoning)
        scheduleStreamDrain()
    }

    fun appendExplicitReasoning(text: String) {
        check(!displayFinished) { "Cannot append to a finalized stream display" }
        if (text.isEmpty()) return
        explicitReasoningPreferred = true
        explicitReasoningBatcher.append(text)
        scheduleStreamDrain()
    }

    /**
     * Commits all text received so far, including a short character chunk, without finishing the stream.
     * Called at tool boundaries so pre-tool text stays visible while the tool runs and stored offsets match.
     */
    fun flushPending() {
        if (displayFinished) return
        val committedAt = monotonicTimeMillis()
        val contentBatch = contentBatcher.takeAllPending(committedAt)
        val inlineReasoningBatch = inlineReasoningBatcher.takeAllPending(committedAt)
        val explicitReasoningBatch = explicitReasoningBatcher.takeAllPending(committedAt)
        if (contentBatch == null && inlineReasoningBatch == null && explicitReasoningBatch == null) return
        publishCommittedBatches(contentBatch, inlineReasoningBatch, explicitReasoningBatch)
    }

    /** Flushes all complete chunks and the final short tail; safe to call more than once. */
    fun finish(finalStatus: ChatStreamStatus) {
        if (displayFinished) return
        displayFinished = true
        pipelineEpoch++
        renderJob?.cancel()
        renderJob = null

        val unresolved = responseSplitter.finish()
        contentBatcher.append(unresolved.content)
        inlineReasoningBatcher.append(unresolved.reasoning)
        val finishedAt = monotonicTimeMillis()
        publishCommittedBatches(
            contentBatch = contentBatcher.finishAndTakeRemainder(finishedAt),
            inlineReasoningBatch = inlineReasoningBatcher.finishAndTakeRemainder(finishedAt),
            explicitReasoningBatch = explicitReasoningBatcher.finishAndTakeRemainder(finishedAt),
            newStatus = finalStatus
        )
    }

    /** Starts display state for the next assistant message in a multi-turn tool response. */
    fun resetForMessage(newMessageId: String) {
        check(displayFinished) { "Finish the current stream display before resetting it" }
        messageId = newMessageId
        displayedContent = StreamTextSnapshot.of()
        displayedInlineReasoning = StreamTextSnapshot.of()
        displayedExplicitReasoning = StreamTextSnapshot.of()
        explicitReasoningPreferred = false
        contentBatcher = newBatcher()
        inlineReasoningBatcher = newBatcher()
        explicitReasoningBatcher = newBatcher()
        responseSplitter = StreamResponseTextSplitter()
        displayFinished = false
        status = ChatStreamStatus.STREAMING
        lastCommitMetrics = null
        lastBatchAtMillis = null
        pipelineEpoch++
        revision++
        publishCurrentState()
    }

    private fun newBatcher() = StreamCharBatcher(charactersPerBatch, monotonicTimeMillis)

    private fun activeStreamState() = ActiveChatStreamState(
        conversationId = conversationId,
        messageId = messageId,
        generationId = generationId,
        revision = revision,
        status = status,
        content = displayedContent,
        reasoning = if (explicitReasoningPreferred) displayedExplicitReasoning else displayedInlineReasoning,
        lastCommitMetrics = lastCommitMetrics,
        cumulativeMetrics = cumulativeMetrics
    )

    private fun publishCurrentState() = publish(conversationId, activeStreamState())

    private fun batchers() = listOf(contentBatcher, inlineReasoningBatcher, explicitReasoningBatcher)

    private fun hasPendingText() = batchers().any { it.hasPendingText }

    private fun hasEligibleBatch() = batchers().any { it.hasEligibleBatch }

    private fun millisUntilIdlePartialFlush(nowMillis: Long): Long? = batchers()
        .mapNotNull { it.millisUntilIdlePartialFlush(nowMillis, commitIntervalMillis) }
        .minOrNull()

    private fun takeNextIdlePartialBatch(committedAtMillis: Long): Pair<Int, StreamTextBatch>? {
        repeat(3) { offset ->
            val channel = (nextBatchChannel + offset) % 3
            val batcher = when (channel) {
                0 -> contentBatcher
                1 -> inlineReasoningBatcher
                else -> explicitReasoningBatcher
            }
            val batch = batcher.takeIdlePartialBatch(committedAtMillis, commitIntervalMillis)
            if (batch != null) {
                nextBatchChannel = (channel + 1) % 3
                return channel to batch
            }
        }
        return null
    }

    private fun takeNextEligibleBatch(committedAtMillis: Long): Pair<Int, StreamTextBatch>? {
        repeat(3) { offset ->
            val channel = (nextBatchChannel + offset) % 3
            val batch = when (channel) {
                0 -> contentBatcher.takeEligibleBatch(committedAtMillis)
                1 -> inlineReasoningBatcher.takeEligibleBatch(committedAtMillis)
                else -> explicitReasoningBatcher.takeEligibleBatch(committedAtMillis)
            }
            if (batch != null) {
                nextBatchChannel = (channel + 1) % 3
                return channel to batch
            }
        }
        return null
    }

    private fun publishCommittedBatches(
        contentBatch: StreamTextBatch? = null,
        inlineReasoningBatch: StreamTextBatch? = null,
        explicitReasoningBatch: StreamTextBatch? = null,
        newStatus: ChatStreamStatus = status
    ) {
        val batches = listOfNotNull(contentBatch, inlineReasoningBatch, explicitReasoningBatch)
        if (batches.isEmpty()) {
            if (newStatus != status) {
                status = newStatus
                revision++
                publishCurrentState()
            }
            return
        }

        revision++
        contentBatch?.let { displayedContent = displayedContent.append(it.text, revision) }
        inlineReasoningBatch?.let {
            displayedInlineReasoning = displayedInlineReasoning.append(it.text, revision)
        }
        explicitReasoningBatch?.let {
            explicitReasoningPreferred = true
            displayedExplicitReasoning = displayedExplicitReasoning.append(it.text, revision)
        }
        status = newStatus
        val commitMetrics = StreamCommitMetrics(
            characterCount = batches.sumOf { it.characterCount },
            queuedCharacters = contentBatcher.pendingCharacterCount + inlineReasoningBatcher.pendingCharacterCount + explicitReasoningBatcher.pendingCharacterCount,
            chunkWaitMillis = batches.maxOf { it.chunkWaitMillis },
            inputToVisibleLatencyMillis = batches.maxOf { it.inputToVisibleLatencyMillis },
            terminal = batches.any { it.terminal }
        )
        lastCommitMetrics = commitMetrics
        cumulativeMetrics = cumulativeMetrics.copy(
            batchCount = cumulativeMetrics.batchCount + 1L,
            committedCharacters = cumulativeMetrics.committedCharacters + commitMetrics.characterCount.toLong(),
            maxQueuedCharacters = maxOf(cumulativeMetrics.maxQueuedCharacters, commitMetrics.queuedCharacters),
            totalChunkWaitMillis = cumulativeMetrics.totalChunkWaitMillis + commitMetrics.chunkWaitMillis,
            totalInputToVisibleLatencyMillis = cumulativeMetrics.totalInputToVisibleLatencyMillis +
                commitMetrics.inputToVisibleLatencyMillis
        )
        publishCurrentState()
        lastBatchAtMillis = monotonicTimeMillis()
    }

    private fun scheduleStreamDrain() {
        if (displayFinished || renderJob?.isActive == true || !hasPendingText()) return
        val scheduledEpoch = pipelineEpoch
        val scheduledJob = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive && !displayFinished && scheduledEpoch == pipelineEpoch) {
                val now = monotonicTimeMillis()
                val pacingWait = lastBatchAtMillis?.let {
                    (commitIntervalMillis - (now - it)).coerceAtLeast(0L)
                } ?: 0L

                // Do not coalesce a backlog: each complete 30-character chunk gets its own render turn.
                if (pacingWait == 0L) {
                    val next = takeNextEligibleBatch(now)
                    if (next != null) {
                        when (next.first) {
                            0 -> publishCommittedBatches(contentBatch = next.second)
                            1 -> publishCommittedBatches(inlineReasoningBatch = next.second)
                            else -> publishCommittedBatches(explicitReasoningBatch = next.second)
                        }
                        continue
                    }
                }

                // If the model pauses for 300 ms, reveal a short tail too; it need not reach 30 chars.
                val idlePartial = takeNextIdlePartialBatch(now)
                if (idlePartial != null) {
                    when (idlePartial.first) {
                        0 -> publishCommittedBatches(contentBatch = idlePartial.second)
                        1 -> publishCommittedBatches(inlineReasoningBatch = idlePartial.second)
                        else -> publishCommittedBatches(explicitReasoningBatch = idlePartial.second)
                    }
                    continue
                }

                val idleWait = millisUntilIdlePartialFlush(now)
                val waitMillis = listOfNotNull(
                    pacingWait.takeIf { it > 0L && hasEligibleBatch() },
                    idleWait
                ).minOrNull() ?: break
                waitForInterval(waitMillis.coerceAtLeast(1L))
            }
        }
        renderJob = scheduledJob
        scheduledJob.invokeOnCompletion {
            if (renderJob === scheduledJob) renderJob = null
        }
        scheduledJob.start()
    }
}
