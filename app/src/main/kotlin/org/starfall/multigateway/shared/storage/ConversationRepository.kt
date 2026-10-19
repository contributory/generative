package org.starfall.multigateway.shared.storage

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.ConversationListItem
import org.starfall.multigateway.shared.model.ConversationSummary
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.ToolActivity
import org.starfall.multigateway.shared.model.pruningEmptyResponses
import org.starfall.multigateway.shared.storage.database.AppDatabase
import org.starfall.multigateway.shared.storage.database.entities.ConversationEntity
import org.starfall.multigateway.shared.storage.database.entities.ConversationSummaryEntity
import org.starfall.multigateway.shared.storage.database.entities.MessageEntity
import org.starfall.multigateway.shared.storage.database.entities.MessageVersionEntity
import org.starfall.multigateway.shared.storage.database.entities.MessageVersionFileEntity
import org.starfall.multigateway.shared.storage.database.entities.ToolActivityEntity
import org.starfall.multigateway.shared.storage.database.entities.ToolActivityFileEntity

class ConversationMigrationException(
    val conversationId: String,
    val issueCodes: List<String>
) : IllegalStateException("Conversation $conversationId needs recovery before it can be opened.")

class ConversationRepository(private val db: AppDatabase) {
    private val dao = db.conversationDao()
    private val writeLock = Mutex()

    /**
     * Compatibility full-history flow. It is cold on purpose: constructing a repository no
     * longer starts decoding every conversation. New UI code must use [conversationList] and
     * [loadById] instead.
     */
    val allConversations: Flow<List<Conversation>> = dao.getAllConversations()
        .map { entities -> entities.map { entityToModel(it) } }
        .flowOn(Dispatchers.IO)
    val conversationList: Flow<List<ConversationListItem>> = dao.getConversationListItems()

    /** Full-history lookup retained for callers that predate [loadById]. */
    suspend fun getById(id: String): Conversation? = loadById(id)

    /** Loads exactly one conversation and its relational children. */
    suspend fun loadById(id: String): Conversation? = withContext(Dispatchers.IO) {
        val entity = dao.getConversationById(id) ?: return@withContext null
        entityToModel(entity)
    }

    suspend fun saveConversation(conversation: Conversation) {
        val persisted = conversation.pruningEmptyResponses()
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                db.withTransaction {
                    upsertConversation(modelToEntity(persisted))
                    persistChildren(persisted)
                }
            }
        }
    }

    suspend fun renameConversation(id: String, title: String) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                db.withTransaction {
                    // REPLACE deletes the parent first and cascades into its history.
                    dao.renameConversation(id, title, now)
                }
            }
        }
    }

    suspend fun deleteConversation(id: String) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                db.withTransaction { dao.deleteById(id) }
            }
        }
    }

    suspend fun deleteConversations(ids: List<String>) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                db.withTransaction { ids.chunked(500).forEach { dao.deleteByIds(it) } }
            }
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                db.withTransaction { dao.deleteAll() }
            }
        }
    }

    /** The subset of [names] that no stored conversation still refers to. */
    suspend fun unreferencedFiles(names: Collection<String>): Set<String> =
        names.filterNot { dao.mentionsFile(it) }.toSet()

    /** Returns raw references without decoding any conversation or message model. */
    suspend fun allFileReferences(): List<String> = withContext(Dispatchers.IO) {
        dao.getAllFileReferences()
    }

    private suspend fun entityToModel(entity: ConversationEntity): Conversation {
        val issues = dao.getMigrationIssues(entity.id)
        if (issues.isNotEmpty()) throw ConversationMigrationException(entity.id, issues.map { it.errorCode })

        val messages = dao.getMessages(entity.id).map { message ->
            val versions = dao.getMessageVersions(entity.id, message.id).map { version ->
                val activities = dao.getToolActivities(entity.id, message.id, version.versionIndex).map { activity ->
                    ToolActivity(
                        id = activity.activityId,
                        name = activity.name,
                        wireName = activity.wireName,
                        status = activity.status,
                        summary = activity.summary,
                        files = dao.getToolActivityFiles(entity.id, message.id, version.versionIndex, activity.activityIndex).map { it.reference },
                        arguments = activity.arguments,
                        response = activity.response,
                        contentOffset = activity.contentOffset,
                        reasoningOffset = activity.reasoningOffset,
                        inlineMedia = activity.inlineMedia,
                        responsePreviewLimit = activity.responsePreviewLimit
                    )
                }
                MessageVersion(
                    content = version.content,
                    timestamp = version.timestamp,
                    files = dao.getMessageVersionFiles(entity.id, message.id, version.versionIndex).map { it.reference },
                    toolActivity = activities,
                    reasoningContent = version.reasoningContent,
                    reasoningSignature = version.reasoningSignature,
                    processingFinishedAt = version.processingFinishedAt,
                    providerId = version.providerId,
                    modelId = version.modelId,
                    modelDisplayName = version.modelDisplayName,
                    generationInterrupted = version.generationInterrupted,
                    generationError = version.generationError
                )
            }
            StoredMessage(
                id = message.id,
                role = message.role.toChatRole(),
                versions = versions,
                activeVersionIndex = message.activeVersionIndex
            )
        }
        return Conversation(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            messages = messages,
            tokenCount = entity.tokenCount,
            providerId = entity.providerId,
            modelId = entity.modelId,
            profileId = entity.profileId,
            summary = dao.getSummary(entity.id)?.toModel(),
            reasoningEffort = entity.reasoningEffort
        )
    }

    private suspend fun persistChildren(conversation: Conversation) {
        val existingMessages = dao.getMessages(conversation.id)
        val messageIds = conversation.messages.map { it.id }
        if (messageIds.isEmpty()) dao.deleteAllMessages(conversation.id)
        else dao.deleteMessagesExcept(conversation.id, messageIds)

        // Message sequence is unique. Shift only when the message set/order changed so a reorder
        // can be applied without deleting and rebuilding any message's descendants.
        if (existingMessages.map { it.id } != messageIds) dao.shiftMessageSequences(conversation.id, 1_000_000)

        conversation.messages.forEachIndexed { sequence, message ->
            val entity = MessageEntity(conversation.id, message.id, sequence, message.role.persistedValue, message.activeVersionIndex)
            val previous = existingMessages.firstOrNull { it.id == message.id }
            if (previous == null) dao.insertMessage(entity) else if (previous != entity) dao.updateMessage(entity)
            persistMessage(conversation.id, message)
        }

        val summary = conversation.summary?.takeIf { it.throughMessageId in messageIds }?.let {
            ConversationSummaryEntity(conversation.id, it.id, it.content, it.throughMessageId, it.role.name, it.createdAt)
        }
        if (summary == null) {
            dao.deleteSummary(conversation.id)
        } else {
            val previous = dao.getSummary(conversation.id)
            if (previous == null) dao.insertSummaryIfAbsent(summary)
            else if (previous != summary) dao.updateSummary(summary)
        }
    }

    private suspend fun upsertConversation(entity: ConversationEntity) {
        if (dao.updateConversation(entity) == 0) dao.insertConversation(entity)
    }

    private suspend fun persistMessage(conversationId: String, message: StoredMessage) {
        val existingVersions = dao.getMessageVersions(conversationId, message.id)
        val versionIndexes = message.versions.indices.toList()
        if (versionIndexes.isEmpty()) dao.deleteMessageVersions(conversationId, message.id)
        else dao.deleteMessageVersionsExcept(conversationId, message.id, versionIndexes)

        message.versions.forEachIndexed { versionIndex, version ->
            val entity = MessageVersionEntity(
                conversationId = conversationId,
                messageId = message.id,
                versionIndex = versionIndex,
                content = version.content,
                timestamp = version.timestamp,
                reasoningContent = version.reasoningContent,
                reasoningSignature = version.reasoningSignature,
                processingFinishedAt = version.processingFinishedAt,
                providerId = version.providerId,
                modelId = version.modelId,
                modelDisplayName = version.modelDisplayName,
                generationInterrupted = version.generationInterrupted,
                generationError = version.generationError
            )
            val previous = existingVersions.firstOrNull { it.versionIndex == versionIndex }
            if (previous == null) dao.insertMessageVersion(entity)
            else if (previous != entity) dao.updateMessageVersion(entity)
            persistVersionFiles(conversationId, message.id, versionIndex, version.files)
            persistActivities(conversationId, message.id, versionIndex, version.toolActivity)
        }
    }

    private suspend fun persistVersionFiles(conversationId: String, messageId: String, versionIndex: Int, references: List<String>) {
        val existing = dao.getMessageVersionFiles(conversationId, messageId, versionIndex)
        if (existing.map { it.reference } == references) return
        if (references.isEmpty()) dao.deleteMessageVersionFiles(conversationId, messageId, versionIndex)
        else dao.deleteMessageVersionFilesExcept(conversationId, messageId, versionIndex, references.indices.toList())
        dao.insertMessageVersionFiles(references.mapIndexed { fileIndex, reference ->
            MessageVersionFileEntity(conversationId, messageId, versionIndex, fileIndex, reference)
        })
    }

    private suspend fun persistActivities(conversationId: String, messageId: String, versionIndex: Int, activities: List<ToolActivity>) {
        val existing = dao.getToolActivities(conversationId, messageId, versionIndex)
        val activityIndexes = activities.indices.toList()
        if (activityIndexes.isEmpty()) dao.deleteToolActivities(conversationId, messageId, versionIndex)
        else dao.deleteToolActivitiesExcept(conversationId, messageId, versionIndex, activityIndexes)
        activities.forEachIndexed { activityIndex, activity ->
            val entity = ToolActivityEntity(
                conversationId, messageId, versionIndex, activityIndex, activity.id, activity.name,
                activity.wireName, activity.status, activity.summary, activity.arguments, activity.response,
                activity.contentOffset, activity.reasoningOffset, activity.inlineMedia, activity.responsePreviewLimit
            )
            val previous = existing.firstOrNull { it.activityIndex == activityIndex }
            if (previous == null) dao.insertToolActivity(entity)
            else if (previous != entity) dao.updateToolActivity(entity)
            persistActivityFiles(conversationId, messageId, versionIndex, activityIndex, activity.files)
        }
    }

    private suspend fun persistActivityFiles(conversationId: String, messageId: String, versionIndex: Int, activityIndex: Int, references: List<String>) {
        val existing = dao.getToolActivityFiles(conversationId, messageId, versionIndex, activityIndex)
        if (existing.map { it.reference } == references) return
        if (references.isEmpty()) dao.deleteToolActivityFiles(conversationId, messageId, versionIndex, activityIndex)
        else dao.deleteToolActivityFilesExcept(conversationId, messageId, versionIndex, activityIndex, references.indices.toList())
        dao.insertToolActivityFiles(references.mapIndexed { fileIndex, reference ->
            ToolActivityFileEntity(conversationId, messageId, versionIndex, activityIndex, fileIndex, reference)
        })
    }

    private fun modelToEntity(conversation: Conversation) = ConversationEntity(
        id = conversation.id,
        title = conversation.title,
        createdAt = conversation.createdAt,
        updatedAt = conversation.updatedAt,
        tokenCount = conversation.tokenCount,
        providerId = conversation.providerId,
        modelId = conversation.modelId,
        profileId = conversation.profileId,
        reasoningEffort = conversation.reasoningEffort
    )

    private fun ConversationSummaryEntity.toModel() = ConversationSummary(
        id = id,
        content = content,
        throughMessageId = throughMessageId,
        role = runCatching { org.starfall.multigateway.shared.model.SummaryRole.valueOf(role) }
            .getOrDefault(org.starfall.multigateway.shared.model.SummaryRole.SYSTEM),
        createdAt = createdAt
    )

    private fun String.toChatRole() = when (this) {
        "user" -> ChatRole.USER
        "model" -> ChatRole.MODEL
        "system" -> ChatRole.SYSTEM
        else -> throw IllegalStateException("Unknown persisted chat role: $this")
    }

    private val ChatRole.persistedValue: String
        get() = when (this) {
            ChatRole.USER -> "user"
            ChatRole.MODEL -> "model"
            ChatRole.SYSTEM -> "system"
        }
}
