package org.starfall.multigateway.shared.storage

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.starfall.multigateway.shared.model.AiChatConfig
import org.starfall.multigateway.shared.model.ChatProfile
import org.starfall.multigateway.shared.model.McpAccess
import org.starfall.multigateway.shared.storage.database.AppDatabase
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileActiveMcpEntity
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileActiveModelToolEntity
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileConfigEntity
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileEntity
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileMcpAccessEntity
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileMcpToolEntity

class ProfileRepository(private val db: AppDatabase) {
    private val dao = db.chatProfileDao()

    private val stored: Flow<List<ChatProfile>> = flow {
        dao.getAllProfiles().collect { entities -> emit(db.withTransaction { entities.map { entityToModel(it) } }) }
    }
    private val state = ImmediateState(stored)
    val allProfiles: Flow<List<ChatProfile>> = state.flow

    suspend fun getById(id: String): ChatProfile? = withContext(Dispatchers.IO) {
        db.withTransaction { dao.getProfileById(id)?.let { entityToModel(it) } }
    }

    suspend fun saveProfile(profile: ChatProfile) = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.insertOrUpdate(ChatProfileEntity(profile.id, profile.name, profile.icon, profile.sortOrder))
            dao.insertConfig(ChatProfileConfigEntity(profile.id, profile.config.systemPrompt))
            dao.deleteMcpAccess(profile.id)
            dao.deleteMcpTools(profile.id)
            dao.deleteActiveMcp(profile.id)
            dao.deleteActiveModelTools(profile.id)
            dao.insertMcpAccess(profile.config.mcpAccess.entries.mapIndexed { ordinal, (serverId, access) ->
                ChatProfileMcpAccessEntity(profile.id, ordinal, serverId, access.enabled)
            })
            dao.insertMcpTools(profile.config.mcpAccess.flatMap { (serverId, access) ->
                access.tools.entries.mapIndexed { ordinal, (toolName, enabled) ->
                    ChatProfileMcpToolEntity(profile.id, serverId, ordinal, toolName, enabled)
                }
            })
            dao.insertActiveMcp(profile.activeMcp.mapIndexed { ordinal, serverId -> ChatProfileActiveMcpEntity(profile.id, ordinal, serverId) })
            dao.insertActiveModelTools(profile.activeModelTools.mapIndexed { ordinal, tool -> ChatProfileActiveModelToolEntity(profile.id, ordinal, tool) })
        }
    }

    suspend fun deleteProfile(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun reorderProfiles(ids: List<String>) {
        state.mutate({ items -> items.map { item -> ids.indexOf(item.id).takeIf { it >= 0 }?.let { item.copy(sortOrder = it) } ?: item }.sortedBy { it.sortOrder } }) {
            db.withTransaction { ids.forEachIndexed { index, id -> dao.updateSortOrder(id, index) } }
        }
    }

    private suspend fun entityToModel(entity: ChatProfileEntity): ChatProfile {
        val accessRows = dao.getMcpAccess(entity.id)
        val tools = dao.getMcpTools(entity.id).groupBy { it.serverId }
        val access = LinkedHashMap<String, McpAccess>()
        accessRows.sortedBy { it.ordinal }.forEach { row ->
            access[row.serverId] = McpAccess(row.enabled, tools[row.serverId].orEmpty().sortedBy { it.ordinal }.associate { it.toolName to it.enabled })
        }
        return ChatProfile(
            id = entity.id,
            name = entity.name,
            icon = entity.icon,
            config = AiChatConfig(dao.getConfig(entity.id)?.systemPrompt.orEmpty(), access),
            sortOrder = entity.sortOrder,
            activeMcp = dao.getActiveMcp(entity.id).sortedBy { it.ordinal }.map { it.serverId },
            activeModelTools = dao.getActiveModelTools(entity.id).sortedBy { it.ordinal }.map { it.toolName }
        )
    }
}
