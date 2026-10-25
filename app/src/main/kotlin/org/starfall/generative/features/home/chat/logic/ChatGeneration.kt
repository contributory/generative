package org.starfall.generative.features.home.chat.logic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.logging.DebugLog
import org.starfall.generative.shared.model.generation.GenerationEvent
import org.starfall.generative.shared.model.logging.DebugLogCategory
import org.starfall.generative.shared.model.logging.DebugLogLevel
import org.starfall.generative.shared.model.isErrorOnly
import org.starfall.generative.shared.model.pruningEmptyResponses
import org.starfall.generative.shared.model.withAttempt
import org.starfall.generative.features.home.chat.model.ActiveChatStreamState
import org.starfall.generative.features.home.chat.model.ChatStreamStatus

private const val MAX_GENERATION_TEXT_CHARS = 512_000
private const val MAX_GENERATION_SIGNATURE_CHARS = 64_000
// While streaming, the partial answer is checkpointed this often so a crash or process kill keeps it.
private const val PARTIAL_SAVE_INTERVAL_MS = 2_000L

/** Marks a persistence failure so the chat screen can show it inline in the user's language. */
internal const val SAVE_CONVERSATION_ERROR_PREFIX = "Could not save conversation:"

/** A single generation owns its conversation snapshot, independently of navigation. */
internal class ChatGeneration(
    private val scope: CoroutineScope,
    private val save: suspend (Conversation) -> Unit,
    private val publish: (Conversation) -> Unit,
    private val publishStream: (String, ActiveChatStreamState?) -> Unit = { _, _ -> },
    private val linesPerBatch: Int = STREAM_LINES_PER_BATCH,
    private val streamCommitIntervalMs: Long = STREAM_COMMIT_INTERVAL_MS,
    private val monotonicTimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val waitForStreamInterval: suspend (Long) -> Unit = { delay(it) }
) {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _waitingForTool = MutableStateFlow(false)
    val waitingForTool = _waitingForTool.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId = _conversationId.asStateFlow()
    var snapshot: Conversation? = null
        private set
    private var snapshotMutationRevision = 0L
    private var job: Job? = null
    private var nextGenerationId = 0L
    private val pendingUpdateWrites = linkedSetOf<Job>()
    private val pendingUpdateWritesLock = Any()

    init {
        require(linesPerBatch > 0) { "linesPerBatch must be positive" }
        require(streamCommitIntervalMs >= 0L) { "streamCommitIntervalMs cannot be negative" }
    }

    fun start(
        conversation: Conversation,
        messageId: String,
        chunks: Flow<String>,
        rollbackConversation: Conversation? = null
    ): Boolean =
        startEvents(conversation, messageId, chunks.map { GenerationEvent.Text(it) }, rollbackConversation)

    fun startEvents(
        conversation: Conversation,
        messageId: String,
        chunks: Flow<GenerationEvent>,
        rollbackConversation: Conversation? = null,
        // Continue the response already stored in [messageId] instead of starting it from empty.
        resume: Boolean = false,
        usageContextKey: String? = null
    ): Boolean {
        if (_busy.value) return false
        val generationId = ++nextGenerationId
        _busy.value = true
        _waitingForTool.value = false
        _error.value = null
        _conversationId.value = conversation.id
        snapshot = conversation
        publish(conversation)
        job = scope.launch(start = CoroutineStart.LAZY) {
            suspend fun persist(conversation: Conversation) {
                // Keep the live placeholder for the streaming UI, but never write an AI message
                // that still has no answer, reasoning, files, tools or error.
                save(conversation.pruningEmptyResponses())
            }

            val initialVersion = snapshot?.messages?.firstOrNull { it.id == messageId }?.activeVersion
            val initialContent = if (resume) initialVersion?.content.orEmpty() else ""
            val initialReasoning = if (resume) initialVersion?.reasoningContent.orEmpty() else ""
            val output = StringBuilder()
            val reasoningOutput = StringBuilder()
            val reasoningSignatureOutput = StringBuilder()
            var activeMessageId = messageId
            var generationInterrupted = false
            var generationErrorMessage: String? = null
            val streamDisplay = ChatStreamDisplay(
                conversationId = conversation.id,
                initialMessageId = messageId,
                generationId = generationId,
                initialContent = initialContent,
                initialReasoning = initialReasoning,
                alreadyInsideVisibleContent = resume && initialContent.isNotEmpty(),
                scope = scope,
                linesPerBatch = linesPerBatch,
                commitIntervalMillis = streamCommitIntervalMs,
                monotonicTimeMillis = monotonicTimeMillis,
                waitForInterval = waitForStreamInterval,
                publish = { id, state -> publishStream(id, state) }
            )
            if (resume) {
                initialVersion?.let { version ->
                    output.append(version.content)
                    version.reasoningContent?.let { reasoningOutput.append(it) }
                    version.reasoningSignature?.let { reasoningSignatureOutput.append(it) }
                }
            }

            fun appendLimited(target: StringBuilder, text: String, limit: Int, label: String) {
                check(target.length.toLong() + text.length <= limit) {
                    "$label exceeds the safe response size limit"
                }
                target.append(text)
            }

            fun resetDisplayFor(message: StoredMessage) {
                activeMessageId = message.id
                output.setLength(0)
                reasoningOutput.setLength(0)
                reasoningSignatureOutput.setLength(0)
                streamDisplay.resetForMessage(message.id)
            }

            suspend fun buildResponseSnapshot(
                current: Conversation,
                errorMessage: String?
            ): Conversation = withContext(Dispatchers.Default) {
                var updated = updateResponse(
                    current,
                    activeMessageId,
                    output.toString(),
                    reasoningOutput.toString().takeIf { it.isNotBlank() },
                    reasoningSignatureOutput.toString().takeIf { it.isNotBlank() }
                )
                if (errorMessage != null) {
                    updated = updated.copy(messages = updated.messages.map { message ->
                        if (message.id != activeMessageId) message else message.copy(
                            versions = message.versions.mapIndexed { index, version ->
                                if (index == message.activeVersionIndex) {
                                    version.copy(generationError = errorMessage)
                                } else version
                            }
                        )
                    })
                }
                updated
            }

            suspend fun responseSnapshot(errorMessage: String? = null): Conversation {
                while (true) {
                    val current = snapshot ?: error("Missing conversation snapshot for active generation")
                    val mutationRevision = snapshotMutationRevision
                    val updated = buildResponseSnapshot(current, errorMessage)
                    // updateConversation may run while the response is assembled on Dispatchers.Default.
                    // Retry so assigning this result cannot roll back its title/model/instruction change.
                    if (snapshot === current && snapshotMutationRevision == mutationRevision) return updated
                }
            }

            var lastCheckpointAt = System.currentTimeMillis()
            suspend fun checkpointPartialResponse() {
                val now = System.currentTimeMillis()
                if (now - lastCheckpointAt < PARTIAL_SAVE_INTERVAL_MS) return
                if (output.isEmpty() && reasoningOutput.isEmpty()) return
                lastCheckpointAt = now
                try {
                    snapshot = responseSnapshot()
                    persist(snapshot!!)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed checkpoint must not abort the generation; the final save still runs.
                    DebugLog.error(DebugLogCategory.ERRORS, "Generation", "Could not save partial response", e)
                }
            }

            val startedAtMs = System.currentTimeMillis()
            DebugLog.log(DebugLogCategory.TOKENS, "Generation") {
                val history = snapshot?.messages.orEmpty()
                "Context ≈ ${history.sumOf { estimateMessageTokens(it, true) }} tokens in ${history.size} messages (estimate)"
            }
            try {
                streamDisplay.publishInitialState()
                persist(snapshot!!)
                chunks.collect { chunk ->
                    when (chunk) {
                        is GenerationEvent.Text -> {
                            appendLimited(output, chunk.text, MAX_GENERATION_TEXT_CHARS, "Answer")
                            streamDisplay.appendText(chunk.text)
                            checkpointPartialResponse()
                        }
                        is GenerationEvent.Reasoning -> {
                            appendLimited(reasoningOutput, chunk.text, MAX_GENERATION_TEXT_CHARS, "Reasoning")
                            chunk.signature?.let {
                                appendLimited(reasoningSignatureOutput, it, MAX_GENERATION_SIGNATURE_CHARS, "Reasoning signature")
                            }
                            streamDisplay.appendExplicitReasoning(chunk.text)
                            checkpointPartialResponse()
                        }
                        is GenerationEvent.Usage -> {
                            if (usageContextKey != null && chunk.usage.isValid) {
                                snapshot = responseSnapshot()
                                snapshot = recordContextUsage(snapshot!!, activeMessageId, chunk.usage, usageContextKey)
                                publish(snapshot!!)
                                persist(snapshot!!)
                            }
                        }
                        is GenerationEvent.Tool -> {
                            _waitingForTool.value = chunk.activity.status == "running"
                            logToolActivity(chunk.activity)
                            // Commit a structural/tool boundary, but leave undersized display batches queued.
                            snapshot = responseSnapshot()
                            val contentOffset = streamDisplay.visibleContentLength
                            val reasoningOffset = if (reasoningOutput.isNotEmpty()) {
                                reasoningOutput.length
                            } else {
                                streamDisplay.visibleReasoningLength
                            }
                            snapshot = snapshot!!.copy(messages = snapshot!!.messages.map { message ->
                                if (message.id != activeMessageId) message else message.copy(versions = message.versions.mapIndexed { index, version ->
                                    if (index != message.activeVersionIndex) version else {
                                        val existingIndex = version.toolActivity.indexOfFirst { it.id == chunk.activity.id }
                                        val anchoredActivity = if (existingIndex >= 0) {
                                            val existing = version.toolActivity[existingIndex]
                                            chunk.activity.copy(
                                                contentOffset = existing.contentOffset,
                                                reasoningOffset = existing.reasoningOffset
                                            )
                                        } else {
                                            chunk.activity.copy(
                                                contentOffset = contentOffset,
                                                reasoningOffset = reasoningOffset
                                            )
                                        }
                                        val updatedActivities = version.toolActivity.toMutableList().apply {
                                            if (existingIndex >= 0) set(existingIndex, anchoredActivity) else add(anchoredActivity)
                                        }
                                        version.copy(toolActivity = updatedActivities)
                                    }
                                })
                            })
                            publish(snapshot!!)
                            persist(snapshot!!)
                        }
                        is GenerationEvent.UserMessage -> {
                            _waitingForTool.value = false
                            streamDisplay.finish(ChatStreamStatus.STREAMING)
                            snapshot = responseSnapshot()

                            val previousId = activeMessageId
                            val now = System.currentTimeMillis()
                            val currentAssistant = snapshot!!.messages.firstOrNull { it.id == previousId }
                            val responseVersion = currentAssistant?.activeVersion?.let { version ->
                                MessageVersion(
                                    timestamp = now.toString(),
                                    providerId = version.providerId,
                                    modelId = version.modelId,
                                    modelDisplayName = version.modelDisplayName
                                )
                            } ?: MessageVersion(timestamp = now.toString())
                            val response = StoredMessage(
                                id = java.util.UUID.randomUUID().toString(),
                                role = ChatRole.MODEL,
                                versions = listOf(responseVersion)
                            )
                            snapshot = snapshot!!.copy(
                                updatedAt = now,
                                messages = snapshot!!.messages.map { message ->
                                    if (message.id != previousId) message else message.copy(
                                        versions = message.versions.mapIndexed { index, version ->
                                            if (index == message.activeVersionIndex) {
                                                version.copy(processingFinishedAt = now)
                                            } else version
                                        }
                                    )
                                } + chunk.message.copy(isQueued = false) + response
                            )
                            resetDisplayFor(response)
                            publish(snapshot!!)
                            persist(snapshot!!)
                        }
                    }
                }
                streamDisplay.finish(ChatStreamStatus.COMPLETED)
                DebugLog.log(DebugLogCategory.TOKENS, "Generation") {
                    val ms = System.currentTimeMillis() - startedAtMs
                    val answer = estimateTextTokens(output.toString())
                    val reasoning = estimateTextTokens(reasoningOutput.toString())
                    val metrics = streamDisplay.cumulativeStreamMetrics
                    val batches = metrics.batchCount
                    val averageLatency = if (batches == 0L) 0L else {
                        metrics.totalInputToVisibleLatencyMillis / batches
                    }
                    val averageLineWait = if (batches == 0L) 0L else {
                        metrics.totalLineWaitMillis / batches
                    }
                    "Output ≈ $answer tokens, reasoning ≈ $reasoning tokens in $ms ms (estimate); " +
                        "stream batches=$batches, lines=${metrics.committedLines}, " +
                        "max queued lines=${metrics.maxQueuedLines}, " +
                        "average line wait=${averageLineWait}ms, " +
                        "average input-to-visible=${averageLatency}ms"
                }
            } catch (e: CancellationException) {
                generationInterrupted = true
                streamDisplay.finish(ChatStreamStatus.CANCELLED)
                throw e
            } catch (e: Exception) {
                DebugLog.error(DebugLogCategory.ERRORS, "Generation", "Generation failed", e)
                generationErrorMessage = e.localizedMessage ?: "Generation failed"
                _error.value = generationErrorMessage
                streamDisplay.finish(ChatStreamStatus.FAILED)
            } finally {
                try {
                    // A stopped stream must retain its partial response before allowing another send.
                    withContext(NonCancellable) {
                        val terminalStatus = when {
                            generationInterrupted -> ChatStreamStatus.CANCELLED
                            generationErrorMessage != null -> ChatStreamStatus.FAILED
                            else -> ChatStreamStatus.COMPLETED
                        }
                        streamDisplay.finish(terminalStatus)
                        val hasGeneratedResponse = output.isNotBlank() || reasoningOutput.isNotBlank() ||
                            snapshot!!.messages.firstOrNull { it.id == activeMessageId }
                                ?.activeVersion?.toolActivity
                                ?.any { !it.name.endsWith(": connect") } == true
                        snapshot = if (generationInterrupted && rollbackConversation != null && !hasGeneratedResponse) {
                            // Regeneration created a provisional version. If the user stops before
                            // any response exists, restore the original conversation instead of
                            // persisting an empty extra version.
                            rollbackConversation
                        } else {
                            val completed = responseSnapshot(generationErrorMessage)
                            completed.copy(messages = completed.messages.map { message ->
                                if (message.id != activeMessageId) message else message.copy(versions = message.versions.mapIndexed { index, version ->
                                    if (index != message.activeVersionIndex) version else version.copy(
                                        generationInterrupted = generationInterrupted,
                                        toolActivity = version.toolActivity.map {
                                            if (it.status == "running") it.copy(status = "cancelled", summary = "Stopped") else it
                                        },
                                        processingFinishedAt = System.currentTimeMillis()
                                    )
                                })
                            })
                        }
                        publish(snapshot!!)
                        persist(snapshot!!)
                    }
                } catch (e: Exception) {
                    _error.value = "$SAVE_CONVERSATION_ERROR_PREFIX ${e.localizedMessage}"
                } finally {
                    publishStream(conversation.id, null)
                    snapshot = null
                    _conversationId.value = null
                    _waitingForTool.value = false
                    _busy.value = false
                }
            }
        }
        job!!.start()
        return true
    }

    fun updateConversation(transform: (Conversation) -> Conversation) {
        val current = snapshot ?: return
        val updated = transform(current)
        snapshot = updated
        snapshotMutationRevision++
        publish(updated)
        val writeJob = scope.launch(start = CoroutineStart.LAZY) { save(updated) }
        synchronized(pendingUpdateWritesLock) { pendingUpdateWrites += writeJob }
        writeJob.invokeOnCompletion {
            synchronized(pendingUpdateWritesLock) { pendingUpdateWrites -= writeJob }
        }
        writeJob.start()
    }

    fun stop() { job?.cancel() }

    suspend fun stopAndJoin() {
        job?.cancelAndJoin()
        // Tool activity can schedule persistence in the ViewModel scope independently of [job].
        // Join every such write before a full restore replaces the conversation database.
        while (true) {
            val writes = synchronized(pendingUpdateWritesLock) { pendingUpdateWrites.toList() }
            if (writes.isEmpty()) break
            writes.joinAll()
        }
    }
}
private fun logToolActivity(activity: org.starfall.generative.shared.model.ToolActivity) {
    val failed = activity.status.contains("error", ignoreCase = true) || activity.status.contains("fail", ignoreCase = true)
    val level = if (failed) DebugLogLevel.ERROR else DebugLogLevel.INFO
    val message = {
        buildString {
            append(activity.name).append(" [").append(activity.status).append(']')
            if (activity.summary.isNotBlank()) append(' ').append(activity.summary)
            if (activity.arguments.isNotBlank()) append("\narguments: ").append(activity.arguments.take(1200))
            if (activity.response.isNotBlank()) append("\nresponse: ").append(activity.response.take(1200))
        }
    }
    DebugLog.log(DebugLogCategory.TOOLS, "Tool", level, message)
    if (failed) DebugLog.log(DebugLogCategory.ERRORS, "Tool", level, message)
}

private fun visibleResponseContent(output: String): String {
    val thinking = output.startsWith("<think>")
    val end = output.indexOf("</think>")
    return if (thinking) {
        if (end >= 0) output.substring(end + 8).trimStart() else ""
    } else {
        output
    }
}

private fun visibleReasoningContent(output: String): String? {
    if (!output.startsWith("<think>")) return null
    val end = output.indexOf("</think>")
    return output.substring(7, if (end >= 0) end else output.length).trim()
}
internal fun updateResponse(
    conversation: Conversation,
    messageId: String,
    output: String,
    explicitReasoning: String? = null,
    explicitReasoningSignature: String? = null
): Conversation {
    val reasoning = explicitReasoning ?: visibleReasoningContent(output)
    val content = visibleResponseContent(output)
    return conversation.copy(
        updatedAt = System.currentTimeMillis(),
        messages = conversation.messages.map { message ->
            if (message.id != messageId) message else message.copy(
                versions = message.versions.mapIndexed { index, version ->
                    if (index == message.activeVersionIndex) version.copy(
                        content = content,
                        reasoningContent = reasoning,
                        reasoningSignature = explicitReasoningSignature
                    ) else version
                }
            )
        }
    )
}

/** Regeneration keeps earlier versions and uses only the context before the selected answer. */
internal fun prepareRegeneration(conversation: Conversation, messageId: String,
    version: MessageVersion = MessageVersion(timestamp = System.currentTimeMillis().toString())): Conversation? {
    val index = conversation.messages.indexOfFirst { it.id == messageId && it.role == ChatRole.MODEL }
    if (index < 1 || conversation.messages.take(index).none { it.role == ChatRole.USER }) return null
    val message = conversation.messages[index]
    val replaceActive = message.activeVersion.isErrorOnly()
    return conversation.copy(messages = conversation.messages.take(index) +
        message.withAttempt(version, replaceActive))
}
