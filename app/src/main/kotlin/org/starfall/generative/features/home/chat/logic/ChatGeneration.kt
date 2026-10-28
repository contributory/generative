package org.starfall.generative.features.home.chat.logic

import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext
import org.starfall.generative.shared.files.TemporaryChatFiles
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

/** Marks a persistence failure so the chat screen can show it inline in the user's language. */
internal const val SAVE_CONVERSATION_ERROR_PREFIX = "Could not save conversation:"

/** A single generation owns its conversation snapshot, independently of navigation. */
internal class ChatGeneration(
    private val scope: CoroutineScope,
    private val save: suspend (Conversation) -> Unit,
    private val publish: (Conversation) -> Unit,
    private val publishStream: (String, ActiveChatStreamState?) -> Unit = { _, _ -> },
    private val charactersPerBatch: Int = STREAM_CHARS_PER_BATCH,
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
    @Volatile private var activeStopSession: GenerationStopSession? = null

    init {
        require(charactersPerBatch > 0) { "charactersPerBatch must be positive" }
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
        val stopSession = GenerationStopSession()
        activeStopSession = stopSession
        _busy.value = true
        _waitingForTool.value = false
        _error.value = null
        _conversationId.value = conversation.id
        snapshot = conversation
        publish(conversation)
        job = scope.launch(
            context = if (conversation.isTemporary) TemporaryChatFiles(conversation.id) else EmptyCoroutineContext,
            start = CoroutineStart.LAZY
        ) {
            suspend fun persist(
                conversation: Conversation,
                allowStoppedSnapshot: Boolean = false
            ) {
                // Only completed or interrupted assistant blocks reach storage. A Stop that
                // races with a block boundary is saved by the finalization path instead.
                if (stopSession.isRequested() && !allowStoppedSnapshot) return
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
                charactersPerBatch = charactersPerBatch,
                commitIntervalMillis = streamCommitIntervalMs,
                monotonicTimeMillis = monotonicTimeMillis,
                waitForInterval = waitForStreamInterval,
                publish = { id, state -> publishStream(id, state) }
            )
            synchronized(stopSession.lock) {
                stopSession.freeze = { streamDisplay.freezeForStop() }
                if (stopSession.requested) stopSession.frozenState = streamDisplay.freezeForStop()
            }
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

            val startedAtMs = System.currentTimeMillis()
            DebugLog.log(DebugLogCategory.TOKENS, "Generation") {
                val history = snapshot?.messages.orEmpty()
                "Context ≈ ${history.sumOf { estimateMessageTokens(it, true) }} tokens in ${history.size} messages (estimate)"
            }
            try {
                if (!stopSession.isRequested()) streamDisplay.publishInitialState()
                chunks.collect { chunk ->
                    if (stopSession.isRequested()) return@collect
                    when (chunk) {
                        is GenerationEvent.Text -> {
                            synchronized(stopSession.lock) {
                                if (!stopSession.requested) {
                                    appendLimited(output, chunk.text, MAX_GENERATION_TEXT_CHARS, "Answer")
                                    streamDisplay.appendText(chunk.text)
                                }
                            }
                        }
                        is GenerationEvent.Reasoning -> {
                            synchronized(stopSession.lock) {
                                if (!stopSession.requested) {
                                    appendLimited(reasoningOutput, chunk.text, MAX_GENERATION_TEXT_CHARS, "Reasoning")
                                    chunk.signature?.let {
                                        appendLimited(reasoningSignatureOutput, it, MAX_GENERATION_SIGNATURE_CHARS, "Reasoning signature")
                                    }
                                    streamDisplay.appendExplicitReasoning(chunk.text)
                                }
                            }
                        }
                        is GenerationEvent.Usage -> {
                            if (usageContextKey != null && chunk.usage.isValid && !stopSession.isRequested()) {
                                val current = responseSnapshot()
                                if (stopSession.isRequested()) return@collect
                                snapshot = recordContextUsage(current, activeMessageId, chunk.usage, usageContextKey)
                                if (stopSession.isRequested()) return@collect
                                publish(snapshot!!)
                            }
                        }
                        is GenerationEvent.Tool -> {
                            val accepted = synchronized(stopSession.lock) {
                                if (stopSession.requested) false else {
                                    _waitingForTool.value = chunk.activity.status == "running"
                                    logToolActivity(chunk.activity)
                                    streamDisplay.flushPending()
                                    true
                                }
                            }
                            if (!accepted) return@collect
                            // A tool boundary shows everything received so far, so text written before the call
                            // is not hidden behind an undersized batch while the tool runs.
                            val current = responseSnapshot()
                            if (stopSession.isRequested()) return@collect
                            snapshot = current
                            val contentOffset = streamDisplay.visibleContentLength
                            val explicitReasoningLength = reasoningOutput.toString()
                                .takeIf { it.isNotBlank() }
                                ?.length
                            val reasoningOffset = explicitReasoningLength ?: streamDisplay.visibleReasoningLength
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
                            if (stopSession.isRequested()) return@collect
                            publish(snapshot!!)
                        }
                        is GenerationEvent.UserMessage -> {
                            val accepted = synchronized(stopSession.lock) {
                                if (stopSession.requested) false else {
                                    _waitingForTool.value = false
                                    streamDisplay.finish(ChatStreamStatus.STREAMING)
                                    true
                                }
                            }
                            if (!accepted) return@collect
                            val current = responseSnapshot()
                            if (stopSession.isRequested()) return@collect
                            snapshot = current

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
                            val nextMessages = snapshot!!.messages.map { message ->
                                if (message.id != previousId) message else message.copy(
                                    versions = message.versions.mapIndexed { index, version ->
                                        if (index == message.activeVersionIndex) {
                                            version.copy(processingFinishedAt = now)
                                        } else version
                                    }
                                )
                            } + chunk.message.copy(isQueued = false) + response
                            snapshot = snapshot!!.withStructuralMessageChange(nextMessages).copy(updatedAt = now)
                            val stillAccepted = synchronized(stopSession.lock) {
                                if (stopSession.requested) false else {
                                    resetDisplayFor(response)
                                    true
                                }
                            }
                            if (!stillAccepted) return@collect
                            publish(snapshot!!)
                            persist(snapshot!!)
                        }
                    }
                }
                if (stopSession.isRequested()) generationInterrupted = true
                else streamDisplay.finish(ChatStreamStatus.COMPLETED)
                DebugLog.log(DebugLogCategory.TOKENS, "Generation") {
                    val ms = System.currentTimeMillis() - startedAtMs
                    val answer = estimateTextTokens(output.toString())
                    val reasoning = estimateTextTokens(reasoningOutput.toString())
                    val metrics = streamDisplay.cumulativeStreamMetrics
                    val batches = metrics.batchCount
                    val averageLatency = if (batches == 0L) 0L else {
                        metrics.totalInputToVisibleLatencyMillis / batches
                    }
                    val averageChunkWait = if (batches == 0L) 0L else {
                        metrics.totalChunkWaitMillis / batches
                    }
                    "Output ≈ $answer tokens, reasoning ≈ $reasoning tokens in $ms ms (estimate); " +
                        "stream batches=$batches, characters=${metrics.committedCharacters}, " +
                        "max queued characters=${metrics.maxQueuedCharacters}, " +
                        "average chunk wait=${averageChunkWait}ms, " +
                        "average input-to-visible=${averageLatency}ms"
                }
            } catch (e: CancellationException) {
                generationInterrupted = true
                if (!stopSession.isRequested()) streamDisplay.finish(ChatStreamStatus.CANCELLED)
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
                        val stoppedAtClick = stopSession.isRequested()
                        if (stoppedAtClick) {
                            generationInterrupted = true
                            stopSession.frozenState?.let { frozen ->
                                output.setLength(0)
                                output.append(frozen.content.materialize())
                                reasoningOutput.setLength(0)
                                reasoningOutput.append(frozen.reasoning.materialize())
                            }
                        }
                        val terminalStatus = when {
                            generationInterrupted -> ChatStreamStatus.CANCELLED
                            generationErrorMessage != null -> ChatStreamStatus.FAILED
                            else -> ChatStreamStatus.COMPLETED
                        }
                        if (!stoppedAtClick) streamDisplay.finish(terminalStatus)
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
                        persist(snapshot!!, allowStoppedSnapshot = stoppedAtClick)
                    }
                } catch (e: Exception) {
                    _error.value = "$SAVE_CONVERSATION_ERROR_PREFIX ${e.localizedMessage}"
                } finally {
                    if (!stopSession.isRequested()) publishStream(conversation.id, null)
                    if (activeStopSession === stopSession) activeStopSession = null
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
        // Metadata changes remain in this generation's RAM snapshot until its block settles.
    }

    /** Synchronous UI cutoff first, network/job cancellation second. */
    fun stop() {
        val session = activeStopSession ?: return
        synchronized(session.lock) {
            if (session.requested) return
            session.requested = true
            session.frozenState = session.freeze?.invoke()
        }
        job?.cancel(CancellationException("Stopped by user"))
    }

    suspend fun stopAndJoin() {
        job?.cancelAndJoin()
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
    return conversation.withUpdatedMessageStructure(
        conversation.messages.take(index) + message.withAttempt(version, replaceActive)
    )
}
private class GenerationStopSession {
    val lock = Any()
    var requested: Boolean = false
    var freeze: (() -> ActiveChatStreamState)? = null
    var frozenState: ActiveChatStreamState? = null

    fun isRequested(): Boolean = synchronized(lock) { requested }
}
