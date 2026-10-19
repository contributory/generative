package org.starfall.multigateway.shared.storage

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    // Kept for compatibility with existing callers. New list UI should use conversationList,
    // which never loads these child rows.
    private val stored: Flow<List<Conversation>> = dao.getAllConversations().map { entities ->
        withContext(Dispatchers.IO) { entities.map { entityToModel(it) } }
    }

    private val state = ImmediateState(stored)
    val allConversations: Flow<List<Conversation>> = state.flow
    val conversationList: Flow<List<ConversationListItem>> = dao.getConversationListItems()

    suspend fun getById(id: String): Conversation? {
        state.value?.let { items -> return items.firstOrNull { it.id == id } }
        return loadById(id)
    }

    /** Loads exactly one conversation and its relational children. */
    suspend fun loadById(id: String): Conversation? = withContext(Dispatchers.IO) {
        val entity = dao.getConversationById(id) ?: return@withContext null
        entityToModel(entity)
    }

    suspend fun saveConversation(conversation: Conversation) {
        val persisted = conversation.pruningEmptyResponses()
        state.mutate({ it.upsert(persisted) { item -> item.id }.sortedByDescending { it.updatedAt } }) {
            db.withTransaction {
                dao.insertOrUpdate(modelToEntity(persisted))
                dao.deleteMessages(persisted.id)
                dao.deleteMigrationIssues(persisted.id)
                persistChildren(persisted)
            }
        }
    }

    suspend fun renameConversation(id: String, title: String) {
        val now = System.currentTimeMillis()
        state.mutate({ items ->
            items.map { if (it.id == id) it.copy(title = title, updatedAt = now) else it }
                .sortedByDescending { it.updatedAt }
        }) {
            db.withTransaction {
                val current = dao.getConversationById(id) ?: return@withTransaction
                dao.insertOrUpdate(current.copy(title = title, updatedAt = now))
            }
        }
    }

    suspend fun deleteConversation(id: String) {
        state.mutate({ items -> items.filterNot { it.id == id } }) { dao.deleteById(id) }
    }

    suspend fun deleteConversations(ids: List<String>) {
        state.mutate({ items -> items.filterNot { it.id in ids } }) {
            db.withTransaction { ids.chunked(500).forEach { dao.deleteByIds(it) } }
        }
    }

    suspend fun deleteAll() {
        state.mutate({ emptyList() }) { dao.deleteAll() }
    }

    /** The subset of [names] that no stored conversation still refers to. */
    suspend fun unreferencedFiles(names: Collection<String>): Set<String> =
        names.filterNot { dao.mentionsFile(it) }.toSet()

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
        val messages = conversation.messages.mapIndexed { sequence, message ->
            MessageEntity(conversation.id, message.id, sequence, message.role.persistedValue, message.activeVersionIndex)
        }
        dao.insertMessages(messages)
        conversation.messages.forEach { message ->
            message.versions.forEachIndexed { versionIndex, version ->
                dao.insertMessageVersions(listOf(MessageVersionEntity(
                    conversationId = conversation.id,
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
                )))
                dao.insertMessageVersionFiles(version.files.mapIndexed { fileIndex, reference ->
                    MessageVersionFileEntity(conversation.id, message.id, versionIndex, fileIndex, reference)
                })
                version.toolActivity.forEachIndexed { activityIndex, activity ->
                    dao.insertToolActivities(listOf(ToolActivityEntity(
                        conversation.id, message.id, versionIndex, activityIndex, activity.id, activity.name,
                        activity.wireName, activity.status, activity.summary, activity.arguments, activity.response,
                        activity.contentOffset, activity.reasoningOffset, activity.inlineMedia, activity.responsePreviewLimit
                    )))
                    dao.insertToolActivityFiles(activity.files.mapIndexed { fileIndex, reference ->
                        ToolActivityFileEntity(conversation.id, message.id, versionIndex, activityIndex, fileIndex, reference)
                    })
                }
            }
        }
        conversation.summary?.takeIf { it.throughMessageId in conversation.messages.map { message -> message.id } }?.let {
            dao.insertSummary(ConversationSummaryEntity(
                conversationId = conversation.id,
                id = it.id,
                content = it.content,
                throughMessageId = it.throughMessageId,
                role = it.role.name,
                createdAt = it.createdAt
            ))
        }
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
