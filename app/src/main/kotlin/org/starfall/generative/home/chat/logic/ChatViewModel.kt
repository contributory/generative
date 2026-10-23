package org.starfall.generative.home.chat.logic


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.media.SpeechAudioPlayer
import org.starfall.generative.shared.media.SpeechSynthesisService
import org.starfall.generative.shared.media.TtsHelper
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.ConversationListItem
import org.starfall.generative.shared.model.ConversationSummaryRequest
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.SummaryRole
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.storage.ConversationRepository
import org.starfall.generative.shared.storage.LocalWriteErrors
import org.starfall.generative.shared.storage.McpRepository
import org.starfall.generative.shared.storage.ModelRepository
import org.starfall.generative.shared.storage.SpeechRepository
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.storage.preferences.AppPreferencesRepository
import org.starfall.generative.shared.storage.preferences.ToolSettingsStore
import org.starfall.generative.home.chat.logic.service.AutomaticConversationSummaryCoordinator
import org.starfall.generative.home.chat.logic.service.ChatConversationCoordinator
import org.starfall.generative.home.chat.logic.service.ChatMessageActions
import org.starfall.generative.home.chat.logic.service.ChatMessageRetryActions
import org.starfall.generative.home.chat.logic.service.ChatModelContextCoordinator
import org.starfall.generative.home.chat.logic.service.ChatToolSettingsActions
import org.starfall.generative.home.chat.logic.service.ConversationTitleGenerator
import org.starfall.generative.home.chat.logic.tool.ToolChatViewModel
import org.starfall.generative.home.chat.logic.input.DirectMediaRequest
import org.starfall.generative.home.chat.model.ContextWindowStatus

internal fun shouldGenerateConversationTitle(userText: String, messageCount: Int): Boolean =
    userText.trim().split(Regex("\\s+")).count { it.isNotEmpty() } >= 10 || messageCount >= 5

internal fun Conversation.startedWithDirectMedia(): Boolean =
    messages.firstOrNull { it.role == ChatRole.MODEL }?.activeVersion?.toolActivity?.any {
        it.name == "generate_image" || it.name == "generate_video"
    } == true

/** Composes chat UI state; workflows are delegated to focused services under `logic/service/`. */
class ChatViewModel(
    private val conversationRepo: ConversationRepository,
    private val modelRepo: ModelRepository,
    private val mcpRepo: McpRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val toolChat: ToolChatViewModel,
    private val toolStore: ToolSettingsStore,
    private val speechRepo: SpeechRepository,
    private val ttsHelper: TtsHelper,
    private val speechSynthesis: SpeechSynthesisService,
    private val speechAudioPlayer: SpeechAudioPlayer,
    private val toolFiles: ToolFiles,
) : ViewModel() {

    val toolSettings = toolStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ToolSettings())
    private val toolSettingsActions = ChatToolSettingsActions(viewModelScope, toolStore)
    fun setSystemTool(name: String, config: SystemToolConfig) = toolSettingsActions.setSystemTool(name, config)
    fun setSendFileEnabled(enabled: Boolean) = toolSettingsActions.setSendFileEnabled(enabled)
    fun setQuickMcp(id: String, enabled: Boolean) = toolSettingsActions.setQuickMcp(id, enabled)
    fun setMcpToolEnabled(serverId: String, toolName: String, enabled: Boolean) =
        toolSettingsActions.setMcpToolEnabled(serverId, toolName, enabled)
    fun setMcpToolParameter(
        serverId: String,
        toolName: String,
        parameterName: String,
        config: org.starfall.generative.shared.model.McpToolParameterConfig,
    ) = toolSettingsActions.setMcpToolParameter(serverId, toolName, parameterName, config)
    fun setMachinesEnabled(enabled: Boolean) = toolSettingsActions.setMachinesEnabled(enabled)
    fun setMachineEnabled(machineId: String, enabled: Boolean, knownMachineIds: List<String> = emptyList()) =
        toolSettingsActions.setMachineEnabled(machineId, enabled, knownMachineIds)
    fun setMachineToolEnabled(machineId: String, toolName: String, enabled: Boolean) =
        toolSettingsActions.setMachineToolEnabled(machineId, toolName, enabled)

    private fun toolEvents(
        provider: ModelProvider,
        model: String,
        messages: List<StoredMessage>,
        prompt: String,
        conversationId: String
    ) =
        toolChat.generate(provider, model, messages, prompt, mcpServers.value, providers.value,
            settings = { toolSettings.value },
            maxToolRounds = { appPreferences.value.effectiveToolRoundLimit },
            takePendingMessage = { takePendingMessage(conversationId) })

    private val deletingAllConversations = MutableStateFlow(false)
    private val deletingConversations = MutableStateFlow<Set<String>>(emptySet())
    private val renamingConversations = MutableStateFlow<Map<String, String>>(emptyMap())
    val conversations: StateFlow<List<ConversationListItem>> = combine(
        conversationRepo.conversationList, deletingConversations, renamingConversations, deletingAllConversations
    ) { items, deleted, renamed, deletingAll ->
        if (deletingAll) emptyList() else items.filterNot { it.id in deleted }.map { item ->
            renamed[item.id]?.let { item.copy(title = it) } ?: item
        }
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val providers: StateFlow<List<ModelProvider>> = modelRepo.allProviders
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mcpServers: StateFlow<List<McpInfo>> = mcpRepo.allServers
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speechServices: StateFlow<List<SpeechService>> = speechRepo.allServices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val appPreferences: StateFlow<AppPreferences> = prefsRepo.appPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private data class GenerationUiState(
        val busy: Boolean = false,
        val error: String? = null,
        val waitingForTool: Boolean = false
    )

    /** Each conversation owns its stream so changing chats never changes another chat's controls. */
    private val generations = mutableMapOf<String, ChatGeneration>()
    private val generationObservers = mutableMapOf<String, Job>()
    private val generationLock = Any()
    private val _generationStates = MutableStateFlow<Map<String, GenerationUiState>>(emptyMap())

    private fun generationFor(conversationId: String): ChatGeneration = synchronized(generationLock) {
        generations.getOrPut(conversationId) {
            ChatGeneration(viewModelScope, conversationRepo::saveConversation) { updated ->
                if (_currentConversation.value?.id == updated.id) {
                    _currentConversation.value = renamingConversations.value[updated.id]?.let {
                        updated.copy(title = it)
                    } ?: updated
                }
            }.also { generation ->
                generationObservers[conversationId] = viewModelScope.launch {
                    combine(generation.busy, generation.error, generation.waitingForTool) { busy, error, waitingForTool ->
                        GenerationUiState(busy, error, waitingForTool)
                    }.collect { state ->
                        _generationStates.update { it + (conversationId to state) }
                    }
                }
            }
        }
    }

    private fun existingGeneration(conversationId: String): ChatGeneration? =
        synchronized(generationLock) { generations[conversationId] }

    private fun generationForCurrent(): ChatGeneration? =
        _currentConversation.value?.id?.let(::existingGeneration)

    /** Busy state for the selected chat only; this controls its stop/send button. */
    val isGenerating: StateFlow<Boolean> = combine(_currentConversation, _generationStates) { conversation, states ->
        conversation?.id?.let { states[it]?.busy } == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isWaitingForTool: StateFlow<Boolean> = combine(_currentConversation, _generationStates) { conversation, states ->
        conversation?.id?.let { states[it]?.waitingForTool } == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Global state used only by the background-generation service. */
    val isAnyGenerating: StateFlow<Boolean> = _generationStates
        .map { states -> states.values.any { it.busy } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * A response is active before the first API event arrives as well as while content is being
     * streamed. This is the state used by the screen wakelock's automatic mode; tool calls are
     * deliberately excluded there.
     */
    val isAnyResponseActive: StateFlow<Boolean> = _generationStates
        .map { states -> states.values.any { it.busy && !it.waitingForTool } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val generatingConversationIds: StateFlow<Set<String>> = _generationStates
        .map { states -> states.filterValues { it.busy }.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Compatibility value for the current chat screen; the drawer uses the complete id set. */
    val generatingConversationId: StateFlow<String?> = combine(_currentConversation, isGenerating) { conversation, busy ->
        conversation?.id?.takeIf { busy }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chatError: StateFlow<String?> = combine(_currentConversation, _generationStates) { conversation, states ->
        conversation?.id?.let { states[it]?.error }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val summaries = ConversationSummaryCoordinator(conversationRepo, toolChat, { _currentConversation.value }) { updated ->
        if (_currentConversation.value?.id == updated.id) {
            _currentConversation.value = renamingConversations.value[updated.id]?.let { updated.copy(title = it) } ?: updated
        }
    }
    val summaryProgress = summaries.progress
    private var summaryJob: Job? = null

    private val modelContext = ChatModelContextCoordinator(
        scope = viewModelScope,
        currentConversation = _currentConversation,
        appPreferences = appPreferences,
        providers = providers,
        toolSettings = toolSettings,
    )
    val contextWindowStatus: StateFlow<ContextWindowStatus?> get() = modelContext.contextWindowStatus

    private val automaticSummary = AutomaticConversationSummaryCoordinator(
        isGenerating = isGenerating,
        isGenerationReplacementActive = { replacingGeneration },
        isSummaryActive = { summaryJob?.isActive == true },
        pendingWrites = { pendingConversationWrites },
        currentConversation = _currentConversation,
        appPreferences = appPreferences,
        providers = providers,
        modelContext = modelContext,
        startSummary = ::startConversationSummary,
    )
    private val _queuedMessages = MutableStateFlow<Map<String, List<StoredMessage>>>(emptyMap())
    private var replacingGeneration = false

    private val conversationCoordinator = ChatConversationCoordinator(
        scope = viewModelScope,
        conversationRepo = conversationRepo,
        prefsRepo = prefsRepo,
        toolFiles = toolFiles,
        currentConversation = _currentConversation,
        queuedMessagesByConversation = _queuedMessages,
        deletingConversations = deletingConversations,
        renamingConversations = renamingConversations,
        deletingAllConversations = deletingAllConversations,
        appPreferences = appPreferences,
        existingGeneration = ::existingGeneration,
        generationsFor = { ids -> synchronized(generationLock) {
            generations.filterKeys { it in ids }.values.toList()
        } },
        allGenerations = { synchronized(generationLock) { generations.values.toList() } },
        providerForConversation = modelContext::conversationModelProvider,
        disconnectRemoteMachines = toolChat::disconnectRemoteMachines,
        summaryConversationId = { summaryProgress.value?.conversationId },
        cancelSummary = { summaryJob?.cancelAndJoin() },
        maybeStartAutomaticSummary = { automaticSummary.maybeStart() },
        isGenerationReplacementActive = { replacingGeneration },
        isSummaryActive = { summaryJob?.isActive == true },
        sendMessage = { text, files -> sendMessage(text, files) },
    )

    private val conversationTitleGenerator = ConversationTitleGenerator(
        scope = viewModelScope,
        conversationRepo = conversationRepo,
        toolChat = toolChat,
        currentConversation = _currentConversation,
        configuredTextModel = modelContext::configuredTextModel,
        existingGeneration = ::existingGeneration,
        generationFor = ::generationFor,
    )

    private val messageActions = ChatMessageActions(
        viewModelScope = viewModelScope,
        conversationRepo = conversationRepo,
        prefsRepo = prefsRepo,
        toolChat = toolChat,
        _currentConversation = _currentConversation,
        providers = providers,
        toolSettings = toolSettings,
        appPreferences = appPreferences,
        isGenerating = isGenerating,
        enqueueMessage = conversationCoordinator::enqueueMessage,
        summaryJob = { summaryJob },
        pendingConversationWrites = { pendingConversationWrites },
        isReplacingGeneration = { replacingGeneration },
        setReplacingGeneration = { replacingGeneration = it },
        generationFor = ::generationFor,
        existingGeneration = ::existingGeneration,
        generationForCurrent = ::generationForCurrent,
        selectedChatModel = modelContext::selectedChatModel,
        providerWithReasoning = modelContext::providerWithReasoning,
        conversationTitleGenerator = conversationTitleGenerator,
        toolEvents = ::toolEvents,
        writeConversation = { block -> conversationCoordinator.writeConversation(block = block) },
        saveEditedConversation = ::saveEditedConversation,
        drainQueuedMessages = ::drainQueuedMessages,
        maybeStartAutomaticSummary = automaticSummary::maybeStart,
    )

    private val messageRetryActions = ChatMessageRetryActions(
        isGenerating = isGenerating,
        summaryJob = { summaryJob },
        pendingConversationWrites = { pendingConversationWrites },
        currentConversation = _currentConversation,
        appPreferences = appPreferences,
        selectedChatModel = modelContext::selectedChatModel,
        generatedVersion = messageActions::generatedVersion,
        mediaTargetOf = messageActions::mediaTargetOf,
        startMediaResponseAction = { conversation, responseId, target, rollback ->
            messageActions.startMediaResponse(conversation, responseId, target, rollback)
        },
        startUserMessageResponseAction = { conversation, responseId, provider, modelId, preferences, rollback, resume ->
            messageActions.startUserMessageResponse(
                conversation, responseId, provider, modelId, preferences, rollback, resume
            )
        },
    )

    val queuedMessages: StateFlow<List<StoredMessage>> get() = conversationCoordinator.queuedMessages
    private val pendingConversationWrites: Int get() = conversationCoordinator.pendingConversationWrites

    private fun restoreLastConversation() = conversationCoordinator.restoreLastConversation()
    private fun drainQueuedMessages() = conversationCoordinator.drainQueuedMessages()
    private fun takePendingMessage(conversationId: String): StoredMessage? =
        conversationCoordinator.takePendingMessage(conversationId)
    private fun writeConversation(serialize: Boolean = false, block: suspend () -> Unit) =
        conversationCoordinator.writeConversation(serialize, block)
    private fun saveEditedConversation(previous: Conversation, updated: Conversation) =
        conversationCoordinator.saveEditedConversation(previous, updated)

    fun editQueuedMessage(id: String, newContent: String, files: List<String>): Boolean =
        conversationCoordinator.editQueuedMessage(id, newContent, files)
    fun deleteQueuedMessage(id: String) = conversationCoordinator.deleteQueuedMessage(id)
    fun selectConversation(conversation: Conversation) = conversationCoordinator.selectConversation(conversation)
    fun selectConversation(conversationId: String) = conversationCoordinator.selectConversation(conversationId)
    fun startNewChat() = conversationCoordinator.startNewChat()
    fun deleteConversation(id: String) = conversationCoordinator.deleteConversation(id)
    fun deleteConversations(ids: Set<String>, attachedFiles: Collection<String> = emptyList()) =
        conversationCoordinator.deleteConversations(ids, attachedFiles)
    fun renameConversation(id: String, newTitle: String) = conversationCoordinator.renameConversation(id, newTitle)
    fun clearAllConversations(attachedFiles: Collection<String> = emptyList()) =
        conversationCoordinator.clearAllConversations(attachedFiles)
    suspend fun importConversations(items: List<Conversation>) = conversationCoordinator.importConversations(items)
    suspend fun conversationsForExport(): List<Conversation> = conversationCoordinator.conversationsForExport()
    fun deleteAllUserData(attachedFiles: Collection<String> = emptyList()) =
        conversationCoordinator.deleteAllUserData(attachedFiles)


    init {
        viewModelScope.launch { modelContext.contextEstimate.collect { automaticSummary.maybeStart() } }
        viewModelScope.launch {
            isGenerating.collect { busy ->
                if (!busy && !replacingGeneration && !automaticSummary.maybeStart()) drainQueuedMessages()
            }
        }
        restoreLastConversation()
    }

    fun setDefaultSystemPrompt(prompt: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            prefsRepo.setDefaultSystemPrompt(prompt)
        }
    }
    fun setPromptEnabled(promptId: String, enabled: Boolean) =
        conversationCoordinator.setPromptEnabled(promptId, enabled)
    fun setPromptLibrary(library: PromptLibrary) { viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setPromptLibrary(library) } }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            launch { prefsRepo.setSelectedModel(providerId, modelId) }
            val provider = modelRepo.getProviderById(providerId)
            if (provider?.config?.modelIds != null && modelId !in provider.config.modelIds) {
                modelRepo.saveProvider(provider.copy(config = provider.config.copy(
                    modelIds = provider.config.modelIds + modelId,
                    modelConfigs = provider.config.modelConfigs + (modelId to ModelConfiguration())
                )))
            }
            // Keep the open conversation's selection separate from the new-chat default.
            val current = _currentConversation.value
            if (appPreferences.value.persistSelectedModel && current != null) {
                val updated = current.copy(
                    providerId = providerId,
                    modelId = modelId,
                    updatedAt = System.currentTimeMillis()
                )
                if (existingGeneration(current.id)?.snapshot?.id == current.id) {
                    generationFor(current.id).updateConversation {
                        it.copy(providerId = providerId, modelId = modelId, updatedAt = updated.updatedAt)
                    }
                } else {
                    _currentConversation.value = updated
                }
                if (updated.messages.isNotEmpty() && existingGeneration(current.id)?.snapshot?.id != current.id) {
                    writeConversation { conversationRepo.saveConversation(updated) }
                }
            }
        }
    }

    fun setPersistSelectedModel(enabled: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            prefsRepo.setPersistSelectedModel(enabled)
            if (enabled) {
                val current = _currentConversation.value ?: return@launch
                val provider = modelContext.conversationModelProvider(current) ?: return@launch
                prefsRepo.setSelectedModel(provider.id, current.modelId)
            }
        }
    }

    fun selectSpeechService(serviceId: String?) {
        viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setSelectedSpeechServiceId(serviceId) }
    }

    private val speech = ChatSpeechCoordinator(viewModelScope, appPreferences, providers, speechServices,
        ttsHelper, speechSynthesis, speechAudioPlayer)
    val speakingMessageId = speech.speakingMessageId
    val activeTestSpeechServiceId = speech.activeTestServiceId
    fun speakText(text: String, messageId: String? = null) = speech.speak(text, messageId)
    fun testVoice(service: SpeechService, text: String) = speech.speakWithService(service, text, isTest = true)
    fun stopSpeaking() = speech.stop()

    fun setConversationReasoningEffort(effort: String?) {
        val current = _currentConversation.value ?: return
        val normalized = effort?.takeIf { it.isNotBlank() }
        val updated = current.copy(reasoningEffort = normalized, updatedAt = System.currentTimeMillis())
        _currentConversation.value = updated
        if (updated.messages.isNotEmpty()) writeConversation { conversationRepo.saveConversation(updated) }
    }

    fun setSummaryRole(role: SummaryRole) {
        val current = _currentConversation.value ?: return
        val summary = current.summary ?: return
        val updated = current.copy(
            summary = summary.copy(role = role),
            updatedAt = System.currentTimeMillis()
        )
        _currentConversation.value = updated
        writeConversation { conversationRepo.saveConversation(updated) }
    }

    fun deleteConversationSummary() {
        val current = _currentConversation.value ?: return
        if (current.summary == null) return
        val updated = current.copy(summary = null, updatedAt = System.currentTimeMillis())
        _currentConversation.value = updated
        writeConversation { conversationRepo.saveConversation(updated) }
    }

    fun cancelConversationSummary() {
        val progress = summaryProgress.value
        val current = _currentConversation.value
        automaticSummary.recordCancellation(progress, current, appPreferences.value)
        summaryJob?.cancel()
    }

    fun startConversationSummary(request: ConversationSummaryRequest): Boolean {
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0) return false
        val conversation = _currentConversation.value ?: return false
        if (conversation.messages.isEmpty()) return false
        val configured = modelContext.summaryTextModel() ?: return false
        val (provider, config) = configured
        summaryJob = viewModelScope.launch {
            try {
                summaries.summarize(conversation, provider, config, request)
            } finally {
                summaryJob = null
                drainQueuedMessages()
            }
        }
        return true
    }


    fun cleanCache() {
        stopGeneration()
        stopSpeaking()
    }

    fun sendMessage(userText: String, fileAttachments: List<String> = emptyList()): Boolean =
        messageActions.sendMessage(userText, fileAttachments)
    fun sendMedia(request: DirectMediaRequest): Boolean = messageActions.sendMedia(request)
    fun stopGeneration() = messageActions.stopGeneration()
    fun editMessage(messageId: String, newContent: String, files: List<String>): Boolean =
        messageActions.editMessage(messageId, newContent, files)
    fun deleteMessage(messageId: String) = messageActions.deleteMessage(messageId)
    fun deleteMessageVersion(messageId: String) = messageActions.deleteMessageVersion(messageId)
    fun switchMessageVersion(messageId: String, versionIndex: Int) =
        messageActions.switchMessageVersion(messageId, versionIndex)
    fun resendUserMessage(messageId: String): Boolean = messageRetryActions.resendUserMessage(messageId)
    fun retryFromError(messageId: String) = messageRetryActions.retryFromError(messageId)
    fun regenerateMessage(messageId: String) = messageRetryActions.regenerateMessage(messageId)

    override fun onCleared() {
        super.onCleared()
        speech.shutdown()
    }

}
