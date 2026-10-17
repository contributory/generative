package org.starfall.multigateway.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import org.starfall.multigateway.data.local.db.AppDatabase
import org.starfall.multigateway.data.local.db.entities.*
import org.starfall.multigateway.shared.model.*

class ConversationRepository(private val db: AppDatabase) {
    private val dao = db.conversationDao()

    private val stored: Flow<List<Conversation>> = dao.getAllConversations().map { entities ->
        entities.map { entityToModel(it) }
    }

    private val state = ImmediateState(stored)
    val allConversations: Flow<List<Conversation>> = state.flow

    suspend fun getById(id: String): Conversation? {
        state.value?.let { items -> return items.firstOrNull { it.id == id } }
        return withContext(Dispatchers.IO) { dao.getConversationById(id)?.let(::entityToModel) }
    }

    suspend fun saveConversation(conversation: Conversation) {
        val persisted = conversation.pruningEmptyResponses()
        state.mutate({ it.upsert(persisted) { item -> item.id }.sortedByDescending { it.updatedAt } }) {
            dao.insertOrUpdate(modelToEntity(persisted))
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
        state.mutate({ items -> items.filterNot { it.id == id } }) {
            dao.deleteById(id)
        }
    }

    suspend fun deleteConversations(ids: List<String>) {
        state.mutate({ items -> items.filterNot { it.id in ids } }) {
            db.withTransaction { ids.chunked(500).forEach { dao.deleteByIds(it) } }
        }
    }

    suspend fun deleteAll() {
        state.mutate({ emptyList() }) {
            dao.deleteAll()
        }
    }

    private fun entityToModel(entity: ConversationEntity): Conversation {
        val messages: List<StoredMessage> = try {
            json.decodeFromString(entity.messagesJson)
        } catch (e: Exception) {
            emptyList()
        }
        return Conversation(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            messages = messages.map { message -> message.copy(versions = message.versions.map { version ->
                val recoveredVersion = if (message.role == ChatRole.MODEL) {
                    recoverLegacyGenerationError(version)
                } else version
                recoveredVersion.copy(toolActivity = recoveredVersion.toolActivity.map { activity ->
                    if(activity.status == "running") activity.copy(status = "interrupted", summary = "The previous run did not finish.") else activity
                })
            }) },
            tokenCount = entity.tokenCount,
            providerId = entity.providerId,
            modelId = entity.modelId,
            profileId = entity.profileId,
            summary = entity.summaryJson?.let { raw ->
                runCatching { json.decodeFromString<ConversationSummary>(raw) }.getOrNull()
            },
            reasoningEffort = entity.reasoningEffort
        )
    }

    /** Older conversations stored generation failures as a trailing line in assistant content. */
    private fun recoverLegacyGenerationError(version: MessageVersion): MessageVersion {
        if (version.generationError != null) return version
        val marker = "[Error: "
        val markerIndex = when {
            version.content.startsWith(marker) -> 0
            else -> version.content.lastIndexOf("\n$marker").takeIf { it >= 0 }?.plus(1) ?: -1
        }
        if (markerIndex < 0 || !version.content.endsWith(']')) return version

        return version.copy(
            content = version.content.substring(0, markerIndex).removeSuffix("\n"),
            generationError = version.content.substring(markerIndex + marker.length, version.content.length - 1)
        )
    }

    private fun modelToEntity(conversation: Conversation): ConversationEntity {
        return ConversationEntity(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            messagesJson = json.encodeToString(conversation.messages),
            tokenCount = conversation.tokenCount,
            providerId = conversation.providerId,
            modelId = conversation.modelId,
            profileId = conversation.profileId,
            summaryJson = conversation.summary?.let { json.encodeToString(it) },
            reasoningEffort = conversation.reasoningEffort
        )
    }
}
