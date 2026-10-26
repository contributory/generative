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
    private val linesPerBatch: Int = STREAM_LINES_PER_BATCH,
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
        require(linesPerBatch > 0) { "linesPerBatch must be positive" }
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

    /** Flushes every pending line and unterminated tail; safe to call more than once. */
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

    private fun newBatcher() = StreamLineBatcher(linesPerBatch, monotonicTimeMillis)

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

    private fun pendingCompleteLines() = contentBatcher.queuedCompleteLines +
        inlineReasoningBatcher.queuedCompleteLines + explicitReasoningBatcher.queuedCompleteLines

    private fun hasEligibleBatch() = contentBatcher.hasEligibleBatch ||
        inlineReasoningBatcher.hasEligibleBatch || explicitReasoningBatcher.hasEligibleBatch

    private fun takeNextEligibleBatch(committedAtMillis: Long): Pair<Int, StreamLineBatch>? {
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
        contentBatch: StreamLineBatch? = null,
        inlineReasoningBatch: StreamLineBatch? = null,
        explicitReasoningBatch: StreamLineBatch? = null,
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
            lineCount = batches.sumOf { it.lineCount },
            queuedLines = pendingCompleteLines(),
            lineWaitMillis = batches.maxOf { it.lineWaitMillis },
            inputToVisibleLatencyMillis = batches.maxOf { it.inputToVisibleLatencyMillis },
            terminal = batches.any { it.terminal }
        )
        lastCommitMetrics = commitMetrics
        cumulativeMetrics = cumulativeMetrics.copy(
            batchCount = cumulativeMetrics.batchCount + 1L,
            committedLines = cumulativeMetrics.committedLines + commitMetrics.lineCount.toLong(),
            maxQueuedLines = maxOf(cumulativeMetrics.maxQueuedLines, commitMetrics.queuedLines),
            totalLineWaitMillis = cumulativeMetrics.totalLineWaitMillis + commitMetrics.lineWaitMillis,
            totalInputToVisibleLatencyMillis = cumulativeMetrics.totalInputToVisibleLatencyMillis +
                commitMetrics.inputToVisibleLatencyMillis
        )
        publishCurrentState()
        lastBatchAtMillis = monotonicTimeMillis()
    }

    private fun scheduleStreamDrain() {
        if (displayFinished || renderJob?.isActive == true || !hasEligibleBatch()) return
        val scheduledEpoch = pipelineEpoch
        val now = monotonicTimeMillis()
        val initialWait = lastBatchAtMillis?.let {
            (commitIntervalMillis - (now - it)).coerceAtLeast(0L)
        } ?: 0L
        val scheduledJob = scope.launch(start = CoroutineStart.LAZY) {
            if (initialWait > 0L) waitForInterval(initialWait)
            while (isActive && !displayFinished && scheduledEpoch == pipelineEpoch) {
                val next = takeNextEligibleBatch(monotonicTimeMillis()) ?: break
                when (next.first) {
                    0 -> publishCommittedBatches(contentBatch = next.second)
                    1 -> publishCommittedBatches(inlineReasoningBatch = next.second)
                    else -> publishCommittedBatches(explicitReasoningBatch = next.second)
                }
                if (!hasEligibleBatch()) break
                waitForInterval(commitIntervalMillis)
            }
        }
        renderJob = scheduledJob
        scheduledJob.invokeOnCompletion {
            if (renderJob === scheduledJob) renderJob = null
        }
        scheduledJob.start()
    }
}
