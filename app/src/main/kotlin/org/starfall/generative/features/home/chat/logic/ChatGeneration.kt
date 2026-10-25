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

private const val STREAM_RENDER_INTERVAL_MS = 120L
private const val LARGE_STREAM_RENDER_INTERVAL_MS = 160L
private const val LARGE_STREAM_THRESHOLD_CHARS = 32_000
private const val VERY_LARGE_STREAM_THRESHOLD_CHARS = 128_000
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
    private val publish: (Conversation) -> Unit
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
    private var job: Job? = null
    private val pendingUpdateWrites = linkedSetOf<Job>()
    private val pendingUpdateWritesLock = Any()

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

            val output = StringBuilder()
            val reasoningOutput = StringBuilder()
            val reasoningSignatureOutput = StringBuilder()
            var activeMessageId = messageId
            var renderJob: Job? = null
            var lastRenderAt = 0L
            var generationInterrupted = false
            if (resume) {
                snapshot?.messages?.firstOrNull { it.id == messageId }?.activeVersion?.let { version ->
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

            fun update(generationErrorMessage: String? = null) {
                snapshot = updateResponse(
                    snapshot!!,
                    activeMessageId,
                    output.toString(),
                    reasoningOutput.toString().takeIf { it.isNotBlank() },
                    reasoningSignatureOutput.toString().takeIf { it.isNotBlank() }
                )
                if (generationErrorMessage != null) {
                    snapshot = snapshot!!.copy(messages = snapshot!!.messages.map { message ->
                        if (message.id != activeMessageId) message else message.copy(
                            versions = message.versions.mapIndexed { index, version ->
                                if (index == message.activeVersionIndex) {
                                    version.copy(generationError = generationErrorMessage)
                                } else version
                            }
                        )
                    })
                }
                publish(snapshot!!)
                lastRenderAt = System.currentTimeMillis()
            }

            fun scheduleUpdate() {
                if (renderJob?.isActive == true) return
                val elapsed = System.currentTimeMillis() - lastRenderAt
                val responseSize = maxOf(output.length, reasoningOutput.length)
                val interval = when {
                    responseSize >= VERY_LARGE_STREAM_THRESHOLD_CHARS -> LARGE_STREAM_RENDER_INTERVAL_MS * 2
                    responseSize >= LARGE_STREAM_THRESHOLD_CHARS -> LARGE_STREAM_RENDER_INTERVAL_MS
                    else -> STREAM_RENDER_INTERVAL_MS
                }
                val waitMs = (interval - elapsed).coerceAtLeast(0L)
                if (waitMs == 0L) {
                    update()
                } else {
                    renderJob = launch {
                        delay(waitMs)
                        update()
                    }
                }
            }

            var lastCheckpointAt = System.currentTimeMillis()
            suspend fun checkpointPartialResponse() {
                val now = System.currentTimeMillis()
                if (now - lastCheckpointAt < PARTIAL_SAVE_INTERVAL_MS) return
                if (output.isBlank() && reasoningOutput.isBlank()) return
                lastCheckpointAt = now
                renderJob?.cancel()
                renderJob = null
                update()
                try {
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
                persist(snapshot!!)
                chunks.collect { chunk ->
                    when (chunk) {
                        is GenerationEvent.Text -> {
                            appendLimited(output, chunk.text, MAX_GENERATION_TEXT_CHARS, "Answer")
                            scheduleUpdate()
                            checkpointPartialResponse()
                        }
                        is GenerationEvent.Reasoning -> {
                            appendLimited(reasoningOutput, chunk.text, MAX_GENERATION_TEXT_CHARS, "Reasoning")
                            chunk.signature?.let {
                                appendLimited(reasoningSignatureOutput, it, MAX_GENERATION_SIGNATURE_CHARS, "Reasoning signature")
                            }
                            scheduleUpdate()
                            checkpointPartialResponse()
                        }
                        is GenerationEvent.Usage -> {
                            if (usageContextKey != null && chunk.usage.isValid) {
                                renderJob?.cancel()
                                renderJob = null
                                update()
                                snapshot = recordContextUsage(snapshot!!, activeMessageId, chunk.usage, usageContextKey)
                                publish(snapshot!!)
                                persist(snapshot!!)
                            }
                        }
                        is GenerationEvent.Tool -> {
                            _waitingForTool.value = chunk.activity.status == "running"
                            logToolActivity(chunk.activity)
                            // Flush pending text before inserting a tool block so its visual anchor is stable.
                            renderJob?.cancel()
                            renderJob = null
                            update()

                            val contentOffset = visibleResponseContent(output.toString()).length
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
                                                 reasoningOffset = reasoningOutput.length
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
                            renderJob?.cancel()
                            renderJob = null
                            update()

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
                            activeMessageId = response.id
                            output.setLength(0)
                            reasoningOutput.setLength(0)
                            reasoningSignatureOutput.setLength(0)
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
                            publish(snapshot!!)
                            persist(snapshot!!)
                        }
                    }
                }
                renderJob?.cancel()
                renderJob = null
                update()
                DebugLog.log(DebugLogCategory.TOKENS, "Generation") {
                    val ms = System.currentTimeMillis() - startedAtMs
                    val answer = estimateTextTokens(output.toString())
                    val reasoning = estimateTextTokens(reasoningOutput.toString())
                    "Output ≈ $answer tokens, reasoning ≈ $reasoning tokens in $ms ms (estimate)"
                }
            } catch (e: CancellationException) {
                generationInterrupted = true
                throw e
            } catch (e: Exception) {
                DebugLog.error(DebugLogCategory.ERRORS, "Generation", "Generation failed", e)
                renderJob?.cancel()
                renderJob = null
                _error.value = e.localizedMessage ?: "Generation failed"
                update(generationErrorMessage = _error.value)
            } finally {
                renderJob?.cancel()
                try {
                    // A stopped stream must retain its partial response before allowing another send.
                    withContext(NonCancellable) {
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
                            updateResponse(
                                snapshot!!,
                                activeMessageId,
                                output.toString(),
                                reasoningOutput.toString().takeIf { it.isNotBlank() },
                                reasoningSignatureOutput.toString().takeIf { it.isNotBlank() }
                            ).copy(messages = snapshot!!.messages.map { message ->
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
