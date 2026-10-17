package org.starfall.multigateway.home.chat.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import org.starfall.multigateway.home.chat.screen.ChatAppbar.ContextWindowStatus
import org.starfall.multigateway.home.chat.screen.ChatAppbar.contextWindowStatus
import org.starfall.multigateway.home.chat.screen.ChatAppbar.effectiveContext
import org.starfall.multigateway.home.chat.screen.ChatAppbar.estimateMessageTokens
import org.starfall.multigateway.shared.media.SpeechAudioPlayer
import org.starfall.multigateway.shared.media.SpeechSynthesisService
import org.starfall.multigateway.shared.media.TtsHelper
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.ConversationSummary
import org.starfall.multigateway.shared.model.ConversationSummaryRequest
import org.starfall.multigateway.shared.model.DEFAULT_TITLE_GENERATION_PROMPT
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.PromptLibrary
import org.starfall.multigateway.shared.model.SidebarOrganization
import org.starfall.multigateway.shared.model.SpeechService
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.SummaryRole
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.language.withConversationReasoning
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.networking.obj
import org.starfall.multigateway.shared.networking.text
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.shared.storage.LocalWriteErrors
import org.starfall.multigateway.shared.storage.McpRepository
import org.starfall.multigateway.shared.storage.ModelRepository
import org.starfall.multigateway.shared.storage.SpeechRepository
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.shared.storage.preferences.AppPreferencesRepository
import org.starfall.multigateway.shared.storage.preferences.ToolSettingsStore

internal fun shouldGenerateConversationTitle(userText: String, messageCount: Int): Boolean =
    userText.trim().split(Regex("\\s+")).count { it.isNotEmpty() } >= 10 || messageCount >= 5

internal fun Conversation.startedWithDirectMedia(): Boolean =
    messages.firstOrNull { it.role == ChatRole.MODEL }?.activeVersion?.toolActivity?.any {
        it.name == "generate_image" || it.name == "generate_video"
    } == true

class ChatViewModel(
    private val conversationRepo: ConversationRepository,
    private val modelRepo: ModelRepository,
    private val mcpRepo: McpRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val toolChat: ToolChat,
    private val toolStore: ToolSettingsStore,
    private val speechRepo: SpeechRepository,
    private val ttsHelper: TtsHelper,
    private val speechSynthesis: SpeechSynthesisService,
    private val speechAudioPlayer: SpeechAudioPlayer,
) : ViewModel() {

    val toolSettings = toolStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ToolSettings())
    fun setSystemTool(name: String, config: SystemToolConfig) { viewModelScope.launch(LocalWriteErrors.handler) { toolStore.update { it.copy(system = it.system + (name to config)) } } }
    fun setSendFileEnabled(enabled: Boolean) { viewModelScope.launch(LocalWriteErrors.handler) { toolStore.update { it.copy(sendFileEnabled = enabled) } } }
    fun setQuickMcp(id: String, enabled: Boolean) { viewModelScope.launch(LocalWriteErrors.handler) { toolStore.update { it.copy(quickMcp = it.quickMcp + (id to enabled)) } } }
    fun setMcpToolEnabled(serverId: String, toolName: String, enabled: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            toolStore.update { settings ->
                val serverTools = settings.mcpTools[serverId].orEmpty() + (toolName to enabled)
                settings.copy(mcpTools = settings.mcpTools + (serverId to serverTools))
            }
        }
    }
    fun setMachinesEnabled(enabled: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            toolStore.update { it.copy(machinesEnabled = enabled) }
        }
    }
    fun setMachineEnabled(machineId: String, enabled: Boolean, knownMachineIds: List<String> = emptyList()) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            toolStore.update { settings ->
                val initialMachineSwitches = if (settings.enabledMachines.isEmpty() && knownMachineIds.isNotEmpty()) {
                    knownMachineIds.associateWith { settings.machinesEnabled }
                } else {
                    settings.enabledMachines
                }
                settings.copy(
                    enabledMachines = initialMachineSwitches + (machineId to enabled),
                    machinesEnabled = settings.machinesEnabled || enabled
                )
            }
        }
    }
    fun setMachineToolEnabled(machineId: String, toolName: String, enabled: Boolean) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            toolStore.update { settings ->
                val machineTools = settings.machineTools[machineId].orEmpty() + (toolName to enabled)
                settings.copy(machineTools = settings.machineTools + (machineId to machineTools))
            }
        }
    }
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
    val conversations: StateFlow<List<Conversation>> = combine(
        conversationRepo.allConversations, deletingConversations, renamingConversations, deletingAllConversations
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

    private fun providerWithReasoning(
        provider: ModelProvider,
        modelId: String,
        conversation: Conversation
    ): ModelProvider {
        val effort = conversation.reasoningEffort?.takeIf { it.isNotBlank() } ?: return provider
        val config = provider.config.modelConfigs[modelId] ?: return provider
        if (!config.supportsThinking) return provider
        return provider.copy(
            config = provider.config.copy(
                modelConfigs = provider.config.modelConfigs +
                    (modelId to config.withConversationReasoning(effort))
            )
        )
    }

    private fun conversationModelProvider(conversation: Conversation): ModelProvider? {
        if (conversation.modelId.isBlank()) return null
        return providers.value.firstOrNull { provider ->
            provider.id == conversation.providerId &&
                provider.config.modelIds?.contains(conversation.modelId) != false &&
                (provider.config.modelConfigs[conversation.modelId]?.modelType
                    ?: ModelType.TEXT_GENERATION) == ModelType.TEXT_GENERATION
        }
    }

    /** Resolve the model the chat composer currently presents for this conversation. */
    private fun selectedChatModel(
        conversation: Conversation?,
        preferences: AppPreferences
    ): Pair<ModelProvider?, String> {
        val conversationProvider = conversation?.takeIf {
            preferences.persistSelectedModel && it.messages.isNotEmpty()
        }?.let(::conversationModelProvider)
        val provider = conversationProvider
            ?: providers.value.find { it.id == preferences.selectedProviderId }
        val modelId = if (conversationProvider != null) {
            conversation?.modelId.orEmpty()
        } else {
            preferences.selectedModelId
        }
        return provider to modelId
    }

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

    private fun configuredTextModel(name: String): Pair<ModelProvider, SystemToolConfig>? {
        val config = toolSettings.value.system[name] ?: return null
        val provider = providers.value.find { it.id == config.providerId } ?: return null
        val model = provider.config.modelConfigs[config.modelId] ?: return null
        if (model.modelType != ModelType.TEXT_GENERATION) return null
        return provider to config
    }

    private data class ContextEstimate(
        val conversation: Conversation?,
        val preferences: AppPreferences,
        val model: ModelConfiguration?,
        val status: ContextWindowStatus?,
    )

    private val contextEstimate: StateFlow<ContextEstimate?> = combine(_currentConversation, appPreferences, providers) { conversation, prefs, available ->
        val conversationProvider = conversation?.takeIf {
            prefs.persistSelectedModel && it.messages.isNotEmpty()
        }?.let { selected ->
            available.firstOrNull { provider ->
                provider.id == selected.providerId &&
                    provider.config.modelIds?.contains(selected.modelId) != false &&
                    (provider.config.modelConfigs[selected.modelId]?.modelType
                        ?: ModelType.TEXT_GENERATION) == ModelType.TEXT_GENERATION
            }
        }
        val provider = conversationProvider ?: available.find { it.id == prefs.selectedProviderId }
        val modelId = conversationProvider?.let { conversation?.modelId } ?: prefs.selectedModelId
        val config = provider?.config?.modelConfigs?.get(modelId)
            ?: provider?.takeIf { modelId.isNotBlank() }?.let {
                // Discovered/custom models may not have a persisted configuration yet. Keep the
                // context indicator useful by using the conservative app default in that case.
                ModelConfiguration(displayName = modelId)
            }
        ContextEstimate(conversation, prefs, config,
            if (conversation == null || config == null || config.modelType != ModelType.TEXT_GENERATION) null
            else org.starfall.multigateway.home.chat.screen.ChatAppbar.contextWindowStatus(conversation, prefs.effectiveSystemPrompt, config, prefs.promptRoleMessages()))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val contextWindowStatus: StateFlow<ContextWindowStatus?> = contextEstimate.map { it?.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var lastAutomaticSummaryAttempt: String? = null

    private fun summaryTextModel(): Pair<ModelProvider, SystemToolConfig>? {
        configuredTextModel("chat_summary")?.let { return it }
        val prefs = appPreferences.value
        val provider = providers.value.find { it.id == prefs.selectedProviderId } ?: return null
        val model = provider.config.modelConfigs[prefs.selectedModelId] ?: return null
        if (model.modelType != ModelType.TEXT_GENERATION) return null
        return provider to SystemToolConfig(providerId = provider.id, modelId = prefs.selectedModelId)
    }

    private fun maybeStartAutomaticSummary(incoming: StoredMessage? = null): Boolean {
        if (isGenerating.value || replacingGeneration || summaryJob?.isActive == true || pendingConversationWrites > 0) return false
        val current = _currentConversation.value ?: return false
        val prefs = appPreferences.value
        val model = providers.value.find { it.id == prefs.selectedProviderId }?.config?.modelConfigs?.get(prefs.selectedModelId) ?: return false
        val status = contextEstimate.value?.takeIf {
            it.conversation === current && it.preferences == prefs && it.model == model
        }?.status ?: if (incoming != null) {
            org.starfall.multigateway.home.chat.screen.ChatAppbar.contextWindowStatus(current, prefs.effectiveSystemPrompt, model, prefs.promptRoleMessages())
        } else return false
        val projected = status.copy(estimatedTokens = status.estimatedTokens + (incoming?.let { estimateMessageTokens(it, false) } ?: 0))
        if (model.modelType != ModelType.TEXT_GENERATION || !projected.shouldAutoSummarize) return false
        val boundary = current.messages.lastOrNull()?.id ?: return false
        if (current.summary?.throughMessageId == boundary) return false
        val attempt = "${current.id}/$boundary/${current.summary?.id}/${prefs.selectedProviderId}/${prefs.selectedModelId}"
        if (lastAutomaticSummaryAttempt == attempt) return false
        val helper = summaryTextModel() ?: return false
        val helperWindow = helper.first.config.modelConfigs[helper.second.modelId]?.contextWindowTokens?.coerceAtLeast(1) ?: DEFAULT_CONTEXT_WINDOW_TOKENS
        lastAutomaticSummaryAttempt = attempt
        return startConversationSummary(ConversationSummaryRequest(
            targetTokens = minOf(1200, (helperWindow / 8).coerceAtLeast(1)),
            chunked = true,
            tokensPerChunk = minOf(20_000, (helperWindow * 0.6).toInt().coerceAtLeast(1))
        ))
    }

    private val _queuedMessages = MutableStateFlow<Map<String, List<StoredMessage>>>(emptyMap())
    val queuedMessages: StateFlow<List<StoredMessage>> = combine(_currentConversation, _queuedMessages) { conversation, queued ->
        conversation?.id?.let { queued[it].orEmpty() }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var replacingGeneration = false

    init {
        viewModelScope.launch { contextEstimate.collect { maybeStartAutomaticSummary() } }
        viewModelScope.launch {
            isGenerating.collect { busy ->
                if (!busy && !replacingGeneration && !maybeStartAutomaticSummary()) drainQueuedMessages()
            }
        }
        restoreLastConversation()
    }

    /**
     * Reopen the most recently updated persisted chat once this ViewModel is created.
     * The repository and preferences flows intentionally do not emit until their first
     * disk snapshot is ready, so this avoids racing the initial empty StateFlow values.
     */
    private fun restoreLastConversation() {
        viewModelScope.launch {
            val preferences = prefsRepo.appPreferencesFlow.first()
            if (!preferences.continueLastConversation || _currentConversation.value != null) return@launch

            val lastConversation = conversationRepo.allConversations.first()
                .asSequence()
                .filter { it.messages.isNotEmpty() }
                .maxWithOrNull(compareBy<Conversation> { it.updatedAt }.thenBy { it.createdAt })
                ?: return@launch

            // Do not replace a selection made while Room was loading.
            if (_currentConversation.value == null &&
                !deletingAllConversations.value &&
                lastConversation.id !in deletingConversations.value
            ) {
                selectConversation(lastConversation)
            }
        }
    }

    private fun drainQueuedMessages() {
        val conversationId = _currentConversation.value?.id ?: return
        if (existingGeneration(conversationId)?.busy?.value == true ||
            replacingGeneration || summaryJob?.isActive == true || pendingConversationWrites > 0) return
        if (maybeStartAutomaticSummary()) return
        var next: StoredMessage? = null
        _queuedMessages.update { queuedByConversation ->
            val queued = queuedByConversation[conversationId].orEmpty()
            next = queued.firstOrNull()
            queuedByConversation + (conversationId to queued.drop(1))
        }
        val message = next ?: return
        sendMessage(message.content, message.files)
    }

    /** Atomically hands one queued message to this conversation's active tool loop. */
    private fun takePendingMessage(conversationId: String): StoredMessage? {
        var next: StoredMessage? = null
        _queuedMessages.update { queuedByConversation ->
            val queued = queuedByConversation[conversationId].orEmpty()
            next = queued.firstOrNull()
            queued.drop(1).let { queuedByConversation + (conversationId to it) }
        }
        return next
    }

    fun editQueuedMessage(id: String, newContent: String, files: List<String>): Boolean {
        if ((newContent.isBlank() && files.isEmpty()) || _queuedMessages.value.values.none { queued ->
                queued.any { it.id == id }
            }) return false
        _queuedMessages.update { queuedByConversation ->
            queuedByConversation.mapValues { (_, queued) -> queued.map { msg ->
                if (msg.id == id) {
                    msg.copy(
                        versions = listOf(
                            MessageVersion(
                                content = newContent,
                                timestamp = System.currentTimeMillis().toString(),
                                files = files
                            )
                        )
                    )
                } else msg
            } }
        }
        return true
    }

    fun deleteQueuedMessage(id: String) {
        _queuedMessages.update { queuedByConversation ->
            queuedByConversation.mapValues { (_, queued) -> queued.filterNot { it.id == id } }
        }
    }
    private var pendingConversationWrites = 0
    private val conversationWrites = Mutex()

    private fun writeConversation(serialize: Boolean = false, block: suspend () -> Unit) {
        pendingConversationWrites++
        viewModelScope.launch(LocalWriteErrors.handler) {
            try {
                if (serialize) conversationWrites.withLock { block() } else block()
            } finally {
                pendingConversationWrites--
                drainQueuedMessages()
            }
        }
    }

    private fun saveEditedConversation(previous: Conversation, updated: Conversation) {
        writeConversation {
            try {
                conversationRepo.saveConversation(updated)
            } catch (error: Exception) {
                if (_currentConversation.value == updated) {
                    _currentConversation.value = conversationRepo.getById(previous.id) ?: previous
                }
                throw error
            }
        }
    }

    fun selectConversation(conversation: Conversation) {
        if (deletingAllConversations.value || conversation.id in deletingConversations.value) return
        val selected = existingGeneration(conversation.id)?.snapshot ?: conversation
        if (selected.id != _currentConversation.value?.id) toolChat.disconnectRemoteMachines()
        _currentConversation.value = selected
        drainQueuedMessages()
        if (!appPreferences.value.persistSelectedModel) return
        conversationModelProvider(selected)?.let { provider ->
            viewModelScope.launch(LocalWriteErrors.handler) {
                prefsRepo.setSelectedModel(provider.id, selected.modelId)
            }
        }
    }

    fun startNewChat() {
        toolChat.disconnectRemoteMachines()
        val now = System.currentTimeMillis()
        _currentConversation.value = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            createdAt = now,
            updatedAt = now
        )
    }

    fun deleteConversation(id: String) = deleteConversations(setOf(id))

    fun deleteConversations(ids: Set<String>) {
        val targets = ids - deletingConversations.value
        if (targets.isEmpty()) return
        val previous = _currentConversation.value?.takeIf { it.id in targets }
        deletingConversations.update { it + targets }
        if (previous != null) {
            _currentConversation.value = null
            _queuedMessages.update { queued -> queued - targets }
        }
        writeConversation(serialize = true) {
            var deleted = false
            try {
                if (summaryProgress.value?.conversationId in targets) summaryJob?.cancelAndJoin()
                synchronized(generationLock) {
                    generations.filterKeys { it in targets }.values.toList()
                }.forEach { it.stopAndJoin() }
                conversationRepo.deleteConversations(targets.toList())
                deleted = true
                prefsRepo.updateSidebar { it.removeChats(targets) }
            } catch (error: Exception) {
                if (!deleted && _currentConversation.value == null && previous != null) {
                    _currentConversation.value = previous
                }
                throw error
            } finally {
                deletingConversations.update { it - targets }
            }
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        val previous = _currentConversation.value?.takeIf { it.id == id }
        renamingConversations.update { it + (id to newTitle) }
        previous?.let { _currentConversation.value = it.copy(title = newTitle) }
        writeConversation(serialize = true) {
            try {
                existingGeneration(id)?.stopAndJoin()
                conversationRepo.renameConversation(id, newTitle)
            } catch (error: Exception) {
                if (_currentConversation.value?.id == id && _currentConversation.value?.title == newTitle) {
                    previous?.let { _currentConversation.value = _currentConversation.value?.copy(title = it.title) }
                }
                throw error
            } finally {
                renamingConversations.update { if (it[id] == newTitle) it - id else it }
            }
        }
    }

    fun clearAllConversations() {
        if (deletingAllConversations.value) return
        val previous = _currentConversation.value
        deletingAllConversations.value = true
        _currentConversation.value = null
        _queuedMessages.value = emptyMap()
        writeConversation(serialize = true) {
            var deleted = false
            try {
                summaryJob?.cancelAndJoin()
                synchronized(generationLock) { generations.values.toList() }.forEach { it.stopAndJoin() }
                conversationRepo.deleteAll()
                deleted = true
                prefsRepo.updateSidebar { SidebarOrganization() }
            } catch (error: Exception) {
                if (!deleted && _currentConversation.value == null) _currentConversation.value = previous
                throw error
            } finally {
                deletingAllConversations.value = false
            }
        }
    }

    suspend fun importConversations(items: List<Conversation>) {
        items.forEach { conversationRepo.saveConversation(it) }
    }

    fun deleteAllUserData() {
        clearAllConversations()
        viewModelScope.launch(LocalWriteErrors.handler) { prefsRepo.setSelectedProfileId(null) }
    }

    fun cleanCache() {
        // Clear cached responses and stopped streams
        stopGeneration()
        stopSpeaking()
    }


    fun setDefaultSystemPrompt(prompt: String) {
        viewModelScope.launch(LocalWriteErrors.handler) {
            prefsRepo.setDefaultSystemPrompt(prompt)
        }
    }
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
                val provider = conversationModelProvider(current) ?: return@launch
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
        if (progress != null && current?.id == progress.conversationId) {
            // Do not immediately restart an automatic summary for the same boundary after a
            // manual cancellation. A later message or an explicit manual start can still run it.
            val prefs = appPreferences.value
            lastAutomaticSummaryAttempt =
                "${progress.conversationId}/${progress.throughMessageId}/${current.summary?.id}/" +
                    "${prefs.selectedProviderId}/${prefs.selectedModelId}"
        }
        summaryJob?.cancel()
    }

    fun startConversationSummary(request: ConversationSummaryRequest): Boolean {
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0) return false
        val conversation = _currentConversation.value ?: return false
        if (conversation.messages.isEmpty()) return false
        val configured = summaryTextModel() ?: return false
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

    private fun generateConversationTitle(
        conversation: Conversation,
        expectedFallbackTitle: String
    ) {
        val configured = configuredTextModel("title_generation") ?: return
        val (provider, config) = configured
        viewModelScope.launch {
            val conversationId = conversation.id
            val titleMessages = conversation.messages
                .filter { it.role == ChatRole.USER || it.role == ChatRole.MODEL }
                .map { message ->
                    StoredMessage(
                        id = message.id,
                        role = message.role,
                        versions = listOf(MessageVersion(content = message.content))
                    )
                }
                .filter { it.content.isNotBlank() }
            val raw = runCatching {
                toolChat.completeText(
                    provider,
                    config.modelId,
                    titleMessages,
                    config.prompt.ifBlank { DEFAULT_TITLE_GENERATION_PROMPT }
                )
            }.getOrNull().orEmpty()
            val title = raw.lineSequence().firstOrNull().orEmpty()
                .trim().trim('"', '\'', '`').take(80)
            if (title.isBlank()) return@launch

            if (existingGeneration(conversationId)?.snapshot?.id == conversationId) {
                generationFor(conversationId).updateConversation { current ->
                    if (current.id == conversationId && current.title == expectedFallbackTitle) {
                        current.copy(title = title, updatedAt = System.currentTimeMillis())
                    } else {
                        current
                    }
                }
            } else {
                val current = _currentConversation.value?.takeIf { it.id == conversationId }
                    ?: conversationRepo.getById(conversationId)
                    ?: return@launch
                if (current.title != expectedFallbackTitle) return@launch
                val updated = current.copy(title = title, updatedAt = System.currentTimeMillis())
                conversationRepo.saveConversation(updated)
                if (_currentConversation.value?.id == conversationId) _currentConversation.value = updated
            }
        }
    }

    private fun generatedVersion(provider: ModelProvider?, modelId: String, timestamp: String) = MessageVersion(
        timestamp = timestamp,
        providerId = provider?.id.orEmpty(),
        modelId = modelId,
        modelDisplayName = provider?.config?.modelConfigs?.get(modelId)?.displayName?.ifBlank { modelId } ?: modelId
    )

    fun sendMessage(userText: String, fileAttachments: List<String> = emptyList()): Boolean {
        if (userText.isBlank() && fileAttachments.isEmpty()) return false

        val existing = _currentConversation.value
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0) {
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
            _queuedMessages.update { queuedByConversation ->
                queuedByConversation + (conversationId to (queuedByConversation[conversationId].orEmpty() + queued))
            }
            return true
        }

        val incoming = StoredMessage(UUID.randomUUID().toString(), ChatRole.USER,
            listOf(MessageVersion(content = userText, files = fileAttachments, timestamp = System.currentTimeMillis().toString())),
            isQueued = true)
        if (maybeStartAutomaticSummary(incoming)) {
            val conversationId = existing?.id ?: return false
            _queuedMessages.update { queuedByConversation ->
                queuedByConversation + (conversationId to (queuedByConversation[conversationId].orEmpty() + incoming))
            }
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
            title = if (firstMessage) fallbackTitle else existing?.title ?: fallbackTitle,
            messages = (existing?.messages ?: emptyList()) + user +
                if (canGenerate) listOf(assistant) else emptyList(),
            updatedAt = now,
            providerId = baseProvider?.id.orEmpty(),
            modelId = modelId,
            profileId = null
        )
        _currentConversation.value = conv

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
            generateConversationTitle(conv, expectedFallbackTitle)
        }
        return started
    }

    fun sendMedia(request: DirectMediaRequest): Boolean {
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0 ||
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
            title = if (isFirstMessage) request.prompt.take(30) else existing?.title ?: request.prompt.take(30),
            messages = existing?.messages.orEmpty() + user + assistant,
            updatedAt = now
        )
        val name = if (request.kind == ModelType.IMAGE_GENERATION) "generate_image" else "generate_video"
        val config = toolSettings.value.system[name]?.takeIf {
            it.providerId == provider.id && it.modelId == request.modelId
        }
        _currentConversation.value = conversation
        return generationFor(conversation.id).startEvents(conversation, assistant.id,
            toolChat.generateMedia(provider, request.modelId, request.kind, request.prompt,
                config?.imageOptions ?: obj(), request.attachments,
                videoOptions = config?.videoOptions ?: obj()))
    }

    fun stopGeneration() { generationForCurrent()?.stop() }

    fun editMessage(messageId: String, newContent: String, files: List<String>): Boolean {
        if (summaryJob?.isActive == true || (newContent.isBlank() && files.isEmpty())) return false
        if (isGenerating.value) {
            val conversation = _currentConversation.value ?: return false
            val message = conversation.messages.firstOrNull { it.id == messageId } ?: return false
            if (replacingGeneration) return false
            if (message.role == ChatRole.USER) {
                val (provider, modelId) = selectedChatModel(conversation, appPreferences.value)
                if (provider == null || modelId.isBlank()) return false
            }
            replacingGeneration = true
            viewModelScope.launch {
                try {
                    generationFor(conversation.id).stopAndJoin()
                    editMessageNow(messageId, newContent, files)
                } finally {
                    replacingGeneration = false
                    if (!isGenerating.value) drainQueuedMessages()
                }
            }
            return true
        }
        return editMessageNow(messageId, newContent, files)
    }

    private fun editMessageNow(messageId: String, newContent: String, files: List<String>): Boolean {
        if (summaryJob?.isActive == true || (newContent.isBlank() && files.isEmpty())) return false
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

    private fun startUserMessageResponse(
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
        if (summaryJob?.isActive == true) return
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
        if (summaryJob?.isActive == true) return
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

    fun resendUserMessage(messageId: String): Boolean {
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0) return false
        val current = _currentConversation.value ?: return false
        val prefs = appPreferences.value
        val (baseProvider, model) = selectedChatModel(current, prefs)
        if (baseProvider == null || model.isBlank()) return false
        val responseId = UUID.randomUUID().toString()
        val conv = prepareUserMessageRetry(current, messageId, responseId,
            generatedVersion(baseProvider, model, System.currentTimeMillis().toString()))?.copy(
            providerId = baseProvider.id,
            modelId = model,
            profileId = null
        ) ?: return false
        val userIndex = conv.messages.indexOfFirst { it.id == messageId }
        val responseMessageId = conv.messages.getOrNull(userIndex + 1)
            ?.takeIf { it.role == ChatRole.MODEL }?.id ?: return false
        return startUserMessageResponse(conv, responseMessageId, baseProvider, model, prefs)
    }

    /**
     * Retry a failed response from the point where it failed. The partial answer, reasoning and
     * tool results stay in the same message and generation continues into it, unlike resend or
     * regenerate which restart from the user message.
     */
    fun retryFromError(messageId: String) {
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0) return
        val current = _currentConversation.value ?: return
        val failed = current.messages.lastOrNull()?.takeIf {
            it.id == messageId && it.role == ChatRole.MODEL && it.activeVersion.generationError != null
        } ?: return
        val prefs = appPreferences.value
        val (baseProvider, model) = selectedChatModel(current, prefs)
        if (baseProvider == null || model.isBlank()) return
        val version = failed.activeVersion
        val hasPartial = version.content.isNotBlank() || !version.reasoningContent.isNullOrBlank() ||
            version.toolActivity.any { !it.name.endsWith(": connect") }
        val cleared = failed.copy(versions = failed.versions.mapIndexed { index, item ->
            if (index != failed.activeVersionIndex) item else item.copy(
                generationError = null,
                generationInterrupted = false,
                processingFinishedAt = null,
                // Connection handshakes are recreated by the new request.
                toolActivity = item.toolActivity.filterNot { it.name.endsWith(": connect") }
            )
        })
        val conv = current.copy(
            messages = current.messages.dropLast(1) + cleared,
            providerId = baseProvider.id,
            modelId = model,
            profileId = null,
            updatedAt = System.currentTimeMillis()
        )
        startUserMessageResponse(conv, messageId, baseProvider, model, prefs, resume = hasPartial)
    }

    fun regenerateMessage(messageId: String) {
        if (isGenerating.value || summaryJob?.isActive == true || pendingConversationWrites > 0) return
        val current = _currentConversation.value ?: return
        val prefs = appPreferences.value
        val (baseProvider, model) = selectedChatModel(current, prefs)
        if (baseProvider == null || model.isBlank()) return
        var conv = prepareRegeneration(current, messageId,
            generatedVersion(baseProvider, model, System.currentTimeMillis().toString()))?.copy(
            providerId = baseProvider.id,
            modelId = model,
            profileId = null
        ) ?: return

        val summaryBoundary = current.summary?.throughMessageId
        if (summaryBoundary != null && conv.messages.none { it.id == summaryBoundary }) {
            conv = conv.copy(summary = null)
        }
        startUserMessageResponse(
            conv,
            messageId,
            baseProvider,
            model,
            prefs,
            rollbackConversation = current
        )
    }

    override fun onCleared() {
        super.onCleared()
        speech.shutdown()
    }
}

private const val RESUME_AFTER_ERROR_PROMPT =
    "The previous response was interrupted by an error. Continue it exactly from where it stopped " +
        "without repeating what was already written or already done by tools."
