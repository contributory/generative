package org.starfall.multigateway.home.chat.logic.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import org.starfall.multigateway.home.chat.logic.ChatGeneration
import org.starfall.multigateway.home.chat.logic.input.DirectMediaRequest
import org.starfall.multigateway.home.chat.logic.shouldGenerateConversationTitle
import org.starfall.multigateway.home.chat.logic.startedWithDirectMedia
import org.starfall.multigateway.home.chat.logic.tool.ToolChatViewModel
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.ConversationSummary
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.generation.GenerationEvent
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.home.chat.logic.effectiveContext
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.shared.storage.LocalWriteErrors
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.storage.preferences.AppPreferencesRepository

/** Handles chat-message mutations and generation entry points; state is supplied by the ViewModel. */
internal class ChatMessageActions(
    private val viewModelScope: CoroutineScope,
    private val conversationRepo: ConversationRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val toolChat: ToolChatViewModel,
    private val _currentConversation: MutableStateFlow<Conversation?>,
    private val providers: StateFlow<List<ModelProvider>>,
    private val toolSettings: StateFlow<ToolSettings>,
    private val appPreferences: StateFlow<AppPreferences>,
    private val isGenerating: StateFlow<Boolean>,
    private val enqueueMessage: (String, StoredMessage) -> Unit,
    private val summaryJob: () -> Job?,
    private val pendingConversationWrites: () -> Int,
    private val isReplacingGeneration: () -> Boolean,
    private val setReplacingGeneration: (Boolean) -> Unit,
    private val generationFor: (String) -> ChatGeneration,
    private val existingGeneration: (String) -> ChatGeneration?,
    private val generationForCurrent: () -> ChatGeneration?,
    private val selectedChatModel: (Conversation?, AppPreferences) -> Pair<ModelProvider?, String>,
    private val providerWithReasoning: (ModelProvider, String, Conversation) -> ModelProvider,
    private val conversationTitleGenerator: ConversationTitleGenerator,
    private val toolEvents: (ModelProvider, String, List<StoredMessage>, String, String) -> Flow<GenerationEvent>,
    private val writeConversation: (suspend () -> Unit) -> Unit,
    private val saveEditedConversation: (Conversation, Conversation) -> Unit,
    private val drainQueuedMessages: () -> Unit,
    private val maybeStartAutomaticSummary: (StoredMessage?) -> Boolean,
) {
    private fun summaryAfterMessageMutation(
        conversation: Conversation,
        changedMessageIndex: Int,
        newMessages: List<StoredMessage>
    ): ConversationSummary? {
        val summary = conversation.summary ?: return null
        val boundaryIndex = conversation.messages.indexOfFirst { it.id == summary.throughMessageId }
        if (boundaryIndex < 0 || changedMessageIndex <= boundaryIndex) return null
        return summary.takeIf { candidate ->
            newMessages.any { it.id == candidate.throughMessageId }
        }
    }

    internal fun generatedVersion(provider: ModelProvider?, modelId: String, timestamp: String) = MessageVersion(
        timestamp = timestamp,
        providerId = provider?.id.orEmpty(),
        modelId = modelId,
        modelDisplayName = provider?.config?.modelConfigs?.get(modelId)?.displayName?.ifBlank { modelId } ?: modelId
    )

    fun sendMessage(userText: String, fileAttachments: List<String> = emptyList()): Boolean {
        if (userText.isBlank() && fileAttachments.isEmpty()) return false

        val existing = _currentConversation.value
        if (isGenerating.value || summaryJob()?.isActive == true || pendingConversationWrites() > 0) {
            val conversationId = existing?.id ?: return false
            val queued = StoredMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.USER,
                versions = listOf(
                    MessageVersion(
                        content = userText,
                        timestamp = System.currentTimeMillis().toString(),
                        files = fileAttachments
                    )
                ),
                isQueued = true
            )
            enqueueMessage(conversationId, queued)
            return true
        }

        val incoming = StoredMessage(UUID.randomUUID().toString(), ChatRole.USER,
            listOf(MessageVersion(content = userText, files = fileAttachments, timestamp = System.currentTimeMillis().toString())),
            isQueued = true)
        if (maybeStartAutomaticSummary(incoming)) {
            val conversationId = existing?.id ?: return false
            enqueueMessage(conversationId, incoming)
            return true
        }
        val prefs = appPreferences.value
        val (baseProvider, modelId) = selectedChatModel(existing, prefs)
        val canGenerate = baseProvider != null && modelId.isNotBlank()
        val now = System.currentTimeMillis()
        val user = StoredMessage(
            UUID.randomUUID().toString(),
            ChatRole.USER,
            listOf(
                MessageVersion(
                    content = userText,
                    timestamp = now.toString(),
                    files = fileAttachments
                )
            )
        )
        val assistant = StoredMessage(
            UUID.randomUUID().toString(),
            ChatRole.MODEL,
            listOf(generatedVersion(baseProvider, modelId, now.toString()))
        )
        val firstMessage = existing == null || existing.messages.isEmpty()
        val fallbackSource = userText.ifBlank { "Attachment" }
        val fallbackTitle = fallbackSource.take(30) + if (fallbackSource.length > 30) "..." else ""
        val conv = (existing ?: Conversation(
            id = UUID.randomUUID().toString(),
            title = fallbackTitle,
            createdAt = now,
            updatedAt = now
        )).copy(
            title = if (firstMessage) fallbackTitle else existing.title,
            messages = (existing?.messages ?: emptyList()) + user +
                if (canGenerate) listOf(assistant) else emptyList(),
            updatedAt = now,
            providerId = baseProvider?.id.orEmpty(),
            modelId = modelId,
            profileId = null
        )
        _currentConversation.value = conv
        viewModelScope.launch(LocalWriteErrors.handler) {
            prefsRepo.setLastActiveConversationId(conv.id)
        }

        if (!canGenerate) {
            writeConversation { conversationRepo.saveConversation(conv) }
            return true
        }

        val provider = providerWithReasoning(requireNotNull(baseProvider), modelId, conv)
        val context = effectiveContext(
            conv,
            conv.messages.dropLast(1),
            prefs.effectiveSystemPrompt
        ).let { it.copy(messages = prefs.promptRoleMessages() + it.messages) }
        val started = generationFor(conv.id).startEvents(
            conv,
            assistant.id,
            toolEvents(provider, modelId, context.messages, context.systemPrompt, conv.id)
        )
        if (started && !conv.startedWithDirectMedia() && shouldGenerateConversationTitle(
                userText,
                conv.messages.count { it.role == ChatRole.USER || it.role == ChatRole.MODEL }
            )) {
            val originalUserText = conv.messages.firstOrNull { it.role == ChatRole.USER }?.content
                .orEmpty().ifBlank { "Attachment" }
            val expectedFallbackTitle = originalUserText.take(30) +
                if (originalUserText.length > 30) "..." else ""
            conversationTitleGenerator.generate(conv, expectedFallbackTitle)
        }
        return started
    }

    fun sendMedia(request: DirectMediaRequest): Boolean {
        if (isGenerating.value || summaryJob()?.isActive == true || pendingConversationWrites() > 0 ||
            request.prompt.isBlank() || request.prompt.length > 32000) return false
        if (request.kind !in listOf(ModelType.IMAGE_GENERATION, ModelType.VIDEO_GENERATION)) return false
        val provider = providers.value.find { it.id == request.providerId } ?: return false
        if (provider.config.modelConfigs[request.modelId]?.modelType != request.kind ||
            provider.config.modelIds?.contains(request.modelId) == false) return false
        val now = System.currentTimeMillis()
        val user = StoredMessage(UUID.randomUUID().toString(), ChatRole.USER,
            listOf(MessageVersion(content = request.prompt, timestamp = now.toString(), files = request.attachments)))
        val assistant = StoredMessage(UUID.randomUUID().toString(), ChatRole.MODEL,
            listOf(generatedVersion(provider, request.modelId, now.toString())))
        val existing = _currentConversation.value
        val isFirstMessage = existing == null || existing.messages.isEmpty()
        val conversation = (existing ?: Conversation(
            id = UUID.randomUUID().toString(), title = request.prompt.take(30),
            createdAt = now, updatedAt = now
        )).copy(
            title = if (isFirstMessage) request.prompt.take(30) else existing.title,
            messages = existing?.messages.orEmpty() + user + assistant,
            updatedAt = now
        )
        _currentConversation.value = conversation
        viewModelScope.launch(LocalWriteErrors.handler) {
            prefsRepo.setLastActiveConversationId(conversation.id)
        }
        return generationFor(conversation.id).startEvents(conversation, assistant.id,
            mediaEvents(provider, request.modelId, request.kind, request.prompt, request.attachments))
    }

    private fun mediaEvents(
        provider: ModelProvider,
        modelId: String,
        kind: ModelType,
        prompt: String,
        attachments: List<String>
    ): Flow<GenerationEvent> {
        val name = if (kind == ModelType.IMAGE_GENERATION) "generate_image" else "generate_video"
        val config = toolSettings.value.system[name]?.takeIf {
            it.providerId == provider.id && it.modelId == modelId
        }
        val modelConfig = provider.config.modelConfigs[modelId]
        val generationMode = modelConfig?.mediaApiType ?: config?.let {
            if (kind == ModelType.IMAGE_GENERATION) it.imageGenerationMode else it.videoGenerationMode
        } ?: org.starfall.multigateway.shared.model.media.MediaGenerationMode.DEFAULT
        val allSystemConfigs = toolSettings.value.system.values
        val requestProfile = if (kind == ModelType.IMAGE_GENERATION) {
            modelConfig?.mediaRequestProfileId?.let { id ->
                allSystemConfigs.firstNotNullOfOrNull { profileConfig -> profileConfig.imageRequestProfiles[id] }
            } ?: config?.activeImageRequestProfile
        } else {
            modelConfig?.mediaRequestProfileId?.let { id ->
                allSystemConfigs.firstNotNullOfOrNull { profileConfig -> profileConfig.videoRequestProfiles[id] }
            } ?: config?.activeVideoRequestProfile
        }
        return toolChat.generateMedia(provider, modelId, kind, prompt,
            config?.imageOptions ?: obj(), attachments,
            videoOptions = config?.videoOptions ?: obj(),
            generationMode = generationMode,
            requestChain = requestProfile?.chain)
    }

    /** Image/video model a response was produced with, or null when it was a normal chat reply. */
    internal class MediaTarget(val provider: ModelProvider, val modelId: String, val kind: ModelType)

    internal fun mediaTargetOf(response: StoredMessage?): MediaTarget? {
        val version = response?.takeIf { it.role == ChatRole.MODEL }?.activeVersion ?: return null
        val provider = providers.value.find { it.id == version.providerId } ?: return null
        val kind = provider.config.modelConfigs[version.modelId]?.modelType
            ?.takeIf { it == ModelType.IMAGE_GENERATION || it == ModelType.VIDEO_GENERATION } ?: return null
        return MediaTarget(provider, version.modelId, kind)
    }

    /** Restart a media response from the user message that precedes it (resend/regenerate/retry). */
    internal fun startMediaResponse(
        conversation: Conversation,
        responseId: String,
        target: MediaTarget,
        rollbackConversation: Conversation? = null
    ): Boolean {
        val responseIndex = conversation.messages.indexOfFirst { it.id == responseId }
        if (responseIndex <= 0) return false
        val user = conversation.messages[responseIndex - 1].takeIf { it.role == ChatRole.USER } ?: return false
        val prompt = user.activeVersion.content
        if (prompt.isBlank() || prompt.length > 32000) return false
        if (target.provider.config.modelIds?.contains(target.modelId) == false) return false
        return generationFor(conversation.id).startEvents(
            conversation,
            responseId,
            mediaEvents(target.provider, target.modelId, target.kind, prompt, user.activeVersion.files),
            rollbackConversation = rollbackConversation
        )
    }

    fun stopGeneration() { generationForCurrent()?.stop() }

    fun editMessage(messageId: String, newContent: String, files: List<String>): Boolean {
        if (summaryJob()?.isActive == true || (newContent.isBlank() && files.isEmpty())) return false
        if (isGenerating.value) {
            val conversation = _currentConversation.value ?: return false
            val message = conversation.messages.firstOrNull { it.id == messageId } ?: return false
            if (isReplacingGeneration()) return false
            if (message.role == ChatRole.USER) {
                val (provider, modelId) = selectedChatModel(conversation, appPreferences.value)
                if (provider == null || modelId.isBlank()) return false
            }
            setReplacingGeneration(true)
            viewModelScope.launch {
                try {
                    generationFor(conversation.id).stopAndJoin()
                    editMessageNow(messageId, newContent, files)
                } finally {
                    setReplacingGeneration(false)
                    if (!isGenerating.value) drainQueuedMessages()
                }
            }
            return true
        }
        return editMessageNow(messageId, newContent, files)
    }

    private fun editMessageNow(messageId: String, newContent: String, files: List<String>): Boolean {
        if (summaryJob()?.isActive == true || (newContent.isBlank() && files.isEmpty())) return false
        val conv = _currentConversation.value ?: return false
        val currentMsgs = conv.messages.toMutableList()
        val idx = currentMsgs.indexOfFirst { it.id == messageId }
        if (idx == -1) return false

        val oldMsg = currentMsgs[idx]
        val newVersions = oldMsg.versions.toMutableList()
        val now = System.currentTimeMillis()
        newVersions.add(
            MessageVersion(
                content = newContent,
                timestamp = now.toString(),
                files = files,
                providerId = oldMsg.activeVersion.providerId,
                modelId = oldMsg.activeVersion.modelId,
                modelDisplayName = oldMsg.activeVersion.modelDisplayName
            )
        )
        currentMsgs[idx] = oldMsg.copy(
            versions = newVersions,
            activeVersionIndex = newVersions.size - 1
        )

        if (oldMsg.role == ChatRole.USER) {
            val prefs = appPreferences.value
            val (provider, modelId) = selectedChatModel(conv, prefs)
            if (provider == null || modelId.isBlank()) return false

            val previousResponse = currentMsgs.getOrNull(idx + 1)?.takeIf { it.role == ChatRole.MODEL }
            val responseId = previousResponse?.id ?: UUID.randomUUID().toString()
            val responseVersion = generatedVersion(provider, modelId, now.toString())
            val response = previousResponse?.copy(
                versions = previousResponse.versions + responseVersion,
                activeVersionIndex = previousResponse.versions.size
            ) ?: StoredMessage(responseId, ChatRole.MODEL, listOf(responseVersion))
            val updatedMessages = currentMsgs.take(idx + 1) + response
            val updated = conv.copy(
                messages = updatedMessages,
                summary = summaryAfterMessageMutation(conv, idx, updatedMessages),
                updatedAt = now,
                providerId = provider.id,
                modelId = modelId,
                profileId = null
            )
            return startUserMessageResponse(updated, responseId, provider, modelId, prefs)
        }

        val updated = conv.copy(
            messages = currentMsgs,
            summary = summaryAfterMessageMutation(conv, idx, currentMsgs),
            updatedAt = System.currentTimeMillis()
        )
        _currentConversation.value = updated
        saveEditedConversation(conv, updated)
        return true
    }

    internal fun startUserMessageResponse(
        conversation: Conversation,
        responseId: String,
        provider: ModelProvider,
        modelId: String,
        preferences: AppPreferences,
        rollbackConversation: Conversation? = null,
        resume: Boolean = false
    ): Boolean {
        val responseIndex = conversation.messages.indexOfFirst { it.id == responseId }
        if (responseIndex <= 0) return false
        // Resuming keeps the partial answer and its tool results in the context, followed by a
        // hidden instruction (never stored or shown) to carry on from where the error happened.
        val contextMessages = if (resume) {
            conversation.messages.take(responseIndex + 1) + StoredMessage(
                id = "resume_$responseId",
                role = ChatRole.USER,
                versions = listOf(MessageVersion(
                    content = RESUME_AFTER_ERROR_PROMPT,
                    timestamp = System.currentTimeMillis().toString()
                ))
            )
        } else conversation.messages.take(responseIndex)
        val context = effectiveContext(
            conversation,
            contextMessages.dropLast(1),
            preferences.effectiveSystemPrompt
        ).let { it.copy(messages = preferences.promptRoleMessages() + it.messages + contextMessages.last()) }
        val configuredProvider = providerWithReasoning(provider, modelId, conversation)
        return generationFor(conversation.id).startEvents(
            conversation,
            responseId,
            toolEvents(configuredProvider, modelId, context.messages, context.systemPrompt, conversation.id),
            rollbackConversation = rollbackConversation,
            resume = resume
        )
    }

    fun deleteMessage(messageId: String) {
        if (summaryJob()?.isActive == true) return
        val current = _currentConversation.value ?: return
        if (isGenerating.value && existingGeneration(current.id)?.snapshot?.id == current.id) {
            viewModelScope.launch {
                generationFor(current.id).stopAndJoin()
                deleteMessageNow(messageId)
            }
            return
        }
        deleteMessageNow(messageId)
    }

    private fun deleteMessageNow(messageId: String) {
        val conv = _currentConversation.value ?: return
        val messageIndex = conv.messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return
        val currentMsgs = conv.messages.filter { it.id != messageId }
        val updated = conv.copy(
            messages = currentMsgs,
            summary = summaryAfterMessageMutation(conv, messageIndex, currentMsgs),
            updatedAt = System.currentTimeMillis()
        )
        _currentConversation.value = updated
        saveEditedConversation(conv, updated)
    }

    fun deleteMessageVersion(messageId: String) {
        if (summaryJob()?.isActive == true) return
        val current = _currentConversation.value ?: return
        if (isGenerating.value && existingGeneration(current.id)?.snapshot?.id == current.id) {
            viewModelScope.launch {
                generationFor(current.id).stopAndJoin()
                deleteMessageVersionNow(messageId)
            }
            return
        }
        deleteMessageVersionNow(messageId)
    }

    private fun deleteMessageVersionNow(messageId: String) {
        val conv = _currentConversation.value ?: return
        val currentMsgs = conv.messages.toMutableList()
        val messageIndex = currentMsgs.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return

        val message = currentMsgs[messageIndex]
        if (message.versions.size <= 1) {
            currentMsgs.removeAt(messageIndex)
        } else {
            val versions = message.versions.toMutableList().apply {
                removeAt(message.activeVersionIndex.coerceIn(indices))
            }
            currentMsgs[messageIndex] = message.copy(
                versions = versions,
                activeVersionIndex = message.activeVersionIndex.coerceAtMost(versions.lastIndex)
            )
        }

        val updated = conv.copy(
            messages = currentMsgs,
            summary = summaryAfterMessageMutation(conv, messageIndex, currentMsgs),
            updatedAt = System.currentTimeMillis()
        )
        _currentConversation.value = updated
        saveEditedConversation(conv, updated)
    }

    fun switchMessageVersion(messageId: String, versionIndex: Int) {
        if (isGenerating.value) return
        val conv = _currentConversation.value ?: return
        val currentMsgs = conv.messages.toMutableList()
        val idx = currentMsgs.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            val oldMsg = currentMsgs[idx]
            if (versionIndex in oldMsg.versions.indices) {
                currentMsgs[idx] = oldMsg.copy(activeVersionIndex = versionIndex)
                val updated = conv.copy(
                    messages = currentMsgs,
                    summary = summaryAfterMessageMutation(conv, idx, currentMsgs),
                    updatedAt = System.currentTimeMillis()
                )
                _currentConversation.value = updated
                saveEditedConversation(conv, updated)
            }
        }
    }


}

private const val RESUME_AFTER_ERROR_PROMPT =
    "The previous response was interrupted by an error. Continue it exactly from where it stopped " +
        "without repeating what was already written or already done by tools."
