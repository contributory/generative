package org.starfall.generative.home.chat.logic.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.starfall.generative.home.chat.logic.ChatGeneration
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.files.uploadedFileNames
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.ConversationListItem
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.SidebarOrganization
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.storage.ConversationRepository
import org.starfall.generative.shared.storage.LocalWriteErrors
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.storage.preferences.AppPreferencesRepository

/** Coordinates conversation selection, history mutations, queued sends and persistence ordering. */
internal class ChatConversationCoordinator(
    private val scope: CoroutineScope,
    private val conversationRepo: ConversationRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val toolFiles: ToolFiles,
    private val currentConversation: MutableStateFlow<Conversation?>,
    private val queuedMessagesByConversation: MutableStateFlow<Map<String, List<StoredMessage>>>,
    private val deletingConversations: MutableStateFlow<Set<String>>,
    private val renamingConversations: MutableStateFlow<Map<String, String>>,
    private val deletingAllConversations: MutableStateFlow<Boolean>,
    private val appPreferences: StateFlow<AppPreferences>,
    private val existingGeneration: (String) -> ChatGeneration?,
    private val generationsFor: (Set<String>) -> List<ChatGeneration>,
    private val allGenerations: () -> List<ChatGeneration>,
    private val providerForConversation: (Conversation) -> ModelProvider?,
    private val disconnectRemoteMachines: () -> Unit,
    private val summaryConversationId: () -> String?,
    private val cancelSummary: suspend () -> Unit,
    private val maybeStartAutomaticSummary: () -> Boolean,
    private val isGenerationReplacementActive: () -> Boolean,
    private val isSummaryActive: () -> Boolean,
    private val sendMessage: (String, List<String>) -> Boolean,
) {
    private var conversationSelectionToken = 0L
    private var pendingWrites = 0
    private val serializedWrites = Mutex()

    val pendingConversationWrites: Int get() = pendingWrites

    val queuedMessages: StateFlow<List<StoredMessage>> = combine(
        currentConversation,
        queuedMessagesByConversation
    ) { conversation, queued ->
        conversation?.id?.let { queued[it].orEmpty() }.orEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun enqueueMessage(conversationId: String, message: StoredMessage) {
        queuedMessagesByConversation.update { queued ->
            queued + (conversationId to (queued[conversationId].orEmpty() + message))
        }
    }

    /** Atomically hands one queued message to the active tool loop for a conversation. */
    fun takePendingMessage(conversationId: String): StoredMessage? {
        var next: StoredMessage? = null
        queuedMessagesByConversation.update { queuedByConversation ->
            val queued = queuedByConversation[conversationId].orEmpty()
            next = queued.firstOrNull()
            queuedByConversation + (conversationId to queued.drop(1))
        }
        return next
    }

    fun editQueuedMessage(id: String, newContent: String, files: List<String>): Boolean {
        if ((newContent.isBlank() && files.isEmpty()) || queuedMessagesByConversation.value.values.none { queued ->
                queued.any { it.id == id }
            }) return false
        queuedMessagesByConversation.update { queuedByConversation ->
            queuedByConversation.mapValues { (_, queued) -> queued.map { message ->
                if (message.id == id) {
                    message.copy(versions = listOf(MessageVersion(
                        content = newContent,
                        timestamp = System.currentTimeMillis().toString(),
                        files = files
                    )))
                } else message
            } }
        }
        return true
    }

    fun deleteQueuedMessage(id: String) {
        queuedMessagesByConversation.update { queuedByConversation ->
            queuedByConversation.mapValues { (_, queued) -> queued.filterNot { it.id == id } }
        }
    }

    fun drainQueuedMessages() {
        val conversationId = currentConversation.value?.id ?: return
        if (existingGeneration(conversationId)?.busy?.value == true || isGenerationReplacementActive() ||
            isSummaryActive() || pendingWrites > 0) return
        if (maybeStartAutomaticSummary()) return
        var next: StoredMessage? = null
        queuedMessagesByConversation.update { queuedByConversation ->
            val queued = queuedByConversation[conversationId].orEmpty()
            next = queued.firstOrNull()
            queuedByConversation + (conversationId to queued.drop(1))
        }
        next?.let { sendMessage(it.content, it.files) }
    }

    fun writeConversation(serialize: Boolean = false, block: suspend () -> Unit) {
        pendingWrites++
        scope.launch(LocalWriteErrors.handler) {
            try {
                if (serialize) serializedWrites.withLock { block() } else block()
            } finally {
                pendingWrites--
                drainQueuedMessages()
            }
        }
    }

    fun saveEditedConversation(previous: Conversation, updated: Conversation) {
        writeConversation {
            try {
                conversationRepo.saveConversation(updated)
            } catch (error: Exception) {
                if (currentConversation.value == updated) {
                    currentConversation.value = conversationRepo.loadById(previous.id) ?: previous
                }
                throw error
            }
        }
    }

    fun restoreLastConversation() {
        scope.launch {
            val preferences = prefsRepo.appPreferencesFlow.first()
            if (!preferences.continueLastConversation || currentConversation.value != null) return@launch

            val storedId = preferences.lastActiveConversationId
            // An empty ID means the previous session was on a blank New Chat; null is a legacy install.
            if (storedId == "") return@launch
            val items = conversationRepo.conversationList.first()
            val lastConversation = if (storedId != null) {
                items.firstOrNull { it.id == storedId && it.hasMessages && !it.hasMigrationIssue }
            } else {
                items.asSequence().filter { it.hasMessages && !it.hasMigrationIssue }
                    .maxWithOrNull(compareBy<ConversationListItem> { it.updatedAt }.thenBy { it.createdAt })
            } ?: return@launch

            // A selection made while Room was loading always wins over restoration.
            if (currentConversation.value == null && !deletingAllConversations.value &&
                lastConversation.id !in deletingConversations.value
            ) selectConversation(lastConversation.id)
        }
    }

    /** Compatibility overload for callers that already have the complete in-memory conversation. */
    fun selectConversation(conversation: Conversation) {
        if (deletingAllConversations.value || conversation.id in deletingConversations.value) return
        conversationSelectionToken++
        applySelectedConversation(conversation)
    }

    fun selectConversation(conversationId: String) {
        if (deletingAllConversations.value || conversationId in deletingConversations.value) return
        val request = ++conversationSelectionToken
        scope.launch(LocalWriteErrors.handler) {
            val conversation = conversationRepo.loadById(conversationId) ?: return@launch
            // A slow history read must not replace a newer selection.
            if (request != conversationSelectionToken || deletingAllConversations.value ||
                conversationId in deletingConversations.value
            ) return@launch
            applySelectedConversation(conversation)
        }
    }

    private fun applySelectedConversation(conversation: Conversation) {
        val selected = existingGeneration(conversation.id)?.snapshot ?: conversation
        if (selected.id != currentConversation.value?.id) disconnectRemoteMachines()
        currentConversation.value = selected
        scope.launch(LocalWriteErrors.handler) {
            prefsRepo.setLastActiveConversationId(selected.id)
            if (!appPreferences.value.persistSelectedModel) return@launch
            providerForConversation(selected)?.let { provider ->
                prefsRepo.setSelectedModel(provider.id, selected.modelId)
            }
        }
        drainQueuedMessages()
    }

    fun startNewChat() {
        // A selection read started earlier must not replace this blank draft when it completes.
        conversationSelectionToken++
        disconnectRemoteMachines()
        val now = System.currentTimeMillis()
        currentConversation.value = Conversation(
            id = java.util.UUID.randomUUID().toString(),
            title = "New Chat",
            createdAt = now,
            updatedAt = now
        )
        scope.launch(LocalWriteErrors.handler) { prefsRepo.setLastActiveConversationId(null) }
    }

    fun deleteConversation(id: String) = deleteConversations(setOf(id))

    /** Deletes history and only the uploaded files no remaining conversation or input still uses. */
    fun deleteConversations(ids: Set<String>, attachedFiles: Collection<String> = emptyList()) {
        val targets = ids - deletingConversations.value
        if (targets.isEmpty()) return
        val previous = currentConversation.value?.takeIf { it.id in targets }
        deletingConversations.update { it + targets }
        if (previous != null) {
            currentConversation.value = null
            queuedMessagesByConversation.update { it - targets }
            scope.launch(LocalWriteErrors.handler) { prefsRepo.setLastActiveConversationId(null) }
        }
        writeConversation(serialize = true) {
            var deleted = false
            try {
                if (summaryConversationId() in targets) cancelSummary()
                generationsFor(targets).forEach { it.stopAndJoin() }
                // Relational file references remain readable when a quarantined chat cannot be decoded.
                val uploads = conversationRepo.allFileReferences().mapNotNull(toolFiles::nameOf).toSet() +
                    uploadsOf(listOfNotNull(previous))
                conversationRepo.deleteConversations(targets.toList())
                deleted = true
                prefsRepo.updateSidebar { it.removeChats(targets) }
                deleteOrphanedUploads(uploads, attachedFiles, removedConversationIds = targets)
            } catch (error: Exception) {
                if (!deleted && currentConversation.value == null && previous != null) {
                    currentConversation.value = previous
                }
                throw error
            } finally {
                deletingConversations.update { it - targets }
            }
        }
    }

    private fun uploadsOf(conversations: Collection<Conversation>): Set<String> =
        conversations.flatMapTo(mutableSetOf()) { it.uploadedFileNames(toolFiles) }

    private suspend fun deleteOrphanedUploads(
        candidates: Set<String>,
        attachedFiles: Collection<String>,
        removedConversationIds: Set<String>
    ) {
        if (candidates.isEmpty()) return
        val queued = queuedMessagesByConversation.value.filterKeys { it !in removedConversationIds }.values
            .flatten().flatMap { message -> message.versions.flatMap { it.files } }
        val stillWanted = (attachedFiles + queued).mapNotNull(toolFiles::nameOf).toSet()
        val orphaned = conversationRepo.unreferencedFiles(candidates - stillWanted)
        if (orphaned.isNotEmpty()) withContext(Dispatchers.IO) { toolFiles.delete(orphaned) }
    }

    fun renameConversation(id: String, newTitle: String) {
        val previous = currentConversation.value?.takeIf { it.id == id }
        renamingConversations.update { it + (id to newTitle) }
        previous?.let { currentConversation.value = it.copy(title = newTitle) }
        writeConversation(serialize = true) {
            try {
                existingGeneration(id)?.stopAndJoin()
                conversationRepo.renameConversation(id, newTitle)
            } catch (error: Exception) {
                if (currentConversation.value?.id == id && currentConversation.value?.title == newTitle) {
                    previous?.let { currentConversation.value = currentConversation.value?.copy(title = it.title) }
                }
                throw error
            } finally {
                renamingConversations.update { if (it[id] == newTitle) it - id else it }
            }
        }
    }

    fun clearAllConversations(attachedFiles: Collection<String> = emptyList()) {
        if (deletingAllConversations.value) return
        val previous = currentConversation.value
        deletingAllConversations.value = true
        currentConversation.value = null
        queuedMessagesByConversation.value = emptyMap()
        scope.launch(LocalWriteErrors.handler) { prefsRepo.setLastActiveConversationId(null) }
        writeConversation(serialize = true) {
            var deleted = false
            try {
                cancelSummary()
                allGenerations().forEach { it.stopAndJoin() }
                val uploads = conversationRepo.allFileReferences().mapNotNull(toolFiles::nameOf).toSet() +
                    uploadsOf(listOfNotNull(previous))
                conversationRepo.deleteAll()
                deleted = true
                prefsRepo.updateSidebar { SidebarOrganization() }
                deleteOrphanedUploads(uploads, attachedFiles, removedConversationIds = emptySet())
            } catch (error: Exception) {
                if (!deleted && currentConversation.value == null) currentConversation.value = previous
                throw error
            } finally {
                deletingAllConversations.value = false
            }
        }
    }

    suspend fun importConversations(items: List<Conversation>) {
        items.forEach { conversationRepo.saveConversation(it) }
    }

    /** Full-history reads happen only at the explicit export boundary, not in sidebar state. */
    suspend fun conversationsForExport(): List<Conversation> =
        conversationRepo.conversationList.first().mapNotNull { conversationRepo.loadById(it.id) }

    fun deleteAllUserData(attachedFiles: Collection<String> = emptyList()) {
        clearAllConversations(attachedFiles)
        scope.launch(LocalWriteErrors.handler) { prefsRepo.setSelectedProfileId(null) }
    }
}
