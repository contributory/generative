package org.starfall.multigateway.shared.storage.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.starfall.multigateway.shared.storage.database.entities.ChatProfileEntity
import org.starfall.multigateway.shared.storage.database.entities.ConversationMigrationQuarantineEntity
import org.starfall.multigateway.shared.storage.database.entities.ConversationSummaryEntity
import org.starfall.multigateway.shared.storage.database.entities.ConversationEntity
import org.starfall.multigateway.shared.storage.database.entities.MessageEntity
import org.starfall.multigateway.shared.storage.database.entities.MessageVersionEntity
import org.starfall.multigateway.shared.storage.database.entities.McpServerEntity
import org.starfall.multigateway.shared.storage.database.entities.ModelProviderEntity
import org.starfall.multigateway.shared.storage.database.entities.ProviderGroupEntity
import org.starfall.multigateway.shared.storage.database.entities.ProviderModelsEntity
import org.starfall.multigateway.shared.storage.database.entities.SpeechServiceEntity
import org.starfall.multigateway.shared.storage.database.entities.MessageVersionFileEntity
import org.starfall.multigateway.shared.storage.database.entities.ToolActivityEntity
import org.starfall.multigateway.shared.storage.database.entities.ToolActivityFileEntity
import org.starfall.multigateway.shared.model.ConversationListItem

@Dao
interface ConversationDao {
    /**
     * Reads only conversation metadata. Do not add messagesJson or summaryJson
     * to this projection: the list must not fetch or deserialize full history.
     */
    @Query("""
        SELECT c.id, c.title, c.createdAt, c.updatedAt, c.tokenCount, c.providerId, c.modelId,
            c.profileId, c.reasoningEffort,
            EXISTS (SELECT 1 FROM messages m WHERE m.conversationId = c.id)
                OR EXISTS (SELECT 1 FROM conversation_migration_quarantine q
                    WHERE q.conversationId = c.id AND q.payloadKind IN ('messages_json', 'messages_integrity')) AS hasMessages,
            EXISTS (SELECT 1 FROM conversation_summaries s WHERE s.conversationId = c.id)
                OR EXISTS (SELECT 1 FROM conversation_migration_quarantine q
                    WHERE q.conversationId = c.id AND q.payloadKind IN ('summary_json', 'summary_boundary_missing')) AS hasSummary,
            EXISTS (SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id) AS hasMigrationIssue
        FROM conversations c
        ORDER BY c.updatedAt DESC, c.id ASC
    """)
    fun getConversationListItems(): Flow<List<ConversationListItem>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id ASC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sequence ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM message_versions WHERE conversationId = :conversationId AND messageId = :messageId ORDER BY versionIndex ASC")
    suspend fun getMessageVersions(conversationId: String, messageId: String): List<MessageVersionEntity>

    @Query("SELECT * FROM message_version_files WHERE conversationId = :conversationId AND messageId = :messageId AND versionIndex = :versionIndex ORDER BY fileIndex ASC")
    suspend fun getMessageVersionFiles(conversationId: String, messageId: String, versionIndex: Int): List<MessageVersionFileEntity>

    @Query("SELECT * FROM tool_activities WHERE conversationId = :conversationId AND messageId = :messageId AND versionIndex = :versionIndex ORDER BY activityIndex ASC")
    suspend fun getToolActivities(conversationId: String, messageId: String, versionIndex: Int): List<ToolActivityEntity>

    @Query("SELECT * FROM tool_activity_files WHERE conversationId = :conversationId AND messageId = :messageId AND versionIndex = :versionIndex AND activityIndex = :activityIndex ORDER BY fileIndex ASC")
    suspend fun getToolActivityFiles(conversationId: String, messageId: String, versionIndex: Int, activityIndex: Int): List<ToolActivityFileEntity>

    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId")
    suspend fun getSummary(conversationId: String): ConversationSummaryEntity?

    @Query("SELECT * FROM conversation_migration_quarantine WHERE conversationId = :conversationId")
    suspend fun getMigrationIssues(conversationId: String): List<ConversationMigrationQuarantineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageVersions(versions: List<MessageVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolActivities(activities: List<ToolActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageVersionFiles(files: List<MessageVersionFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolActivityFiles(files: List<ToolActivityFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ConversationSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMigrationIssue(issue: ConversationMigrationQuarantineEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query("DELETE FROM conversation_migration_quarantine WHERE conversationId = :conversationId")
    suspend fun deleteMigrationIssues(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    /** True when any stored conversation still refers to [name] in either relational file table. */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM message_version_files WHERE reference = :name OR reference = 'tool-file:' || :name
                OR reference LIKE '%/' || :name OR reference LIKE 'file:%/' || :name OR reference LIKE 'tool-file:%/' || :name
            UNION ALL
            SELECT 1 FROM tool_activity_files WHERE reference = :name OR reference = 'tool-file:' || :name
                OR reference LIKE '%/' || :name OR reference LIKE 'file:%/' || :name OR reference LIKE 'tool-file:%/' || :name
        )
    """)
    suspend fun mentionsFile(name: String): Boolean

    @Query("""
        SELECT reference FROM message_version_files
        UNION ALL
        SELECT reference FROM tool_activity_files
    """)
    suspend fun getAllFileReferences(): List<String>
}

@Dao
interface ChatProfileDao {
    @Query("SELECT * FROM chat_profiles ORDER BY sortOrder ASC, id ASC")
    fun getAllProfiles(): Flow<List<ChatProfileEntity>>

    @Query("SELECT * FROM chat_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ChatProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: ChatProfileEntity)

    @Query("UPDATE chat_profiles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM chat_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProviderGroupDao {
    @Query("SELECT * FROM provider_groups ORDER BY sortOrder ASC, id ASC")
    fun getAllGroups(): Flow<List<ProviderGroupEntity>>

    @Query("SELECT * FROM provider_groups WHERE id = :id")
    suspend fun getGroupById(id: String): ProviderGroupEntity?

    @Query("SELECT * FROM provider_groups ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllGroupsOnce(): List<ProviderGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(group: ProviderGroupEntity)

    @Query("UPDATE provider_groups SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM provider_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ModelProviderDao {
    @Query("SELECT * FROM model_providers ORDER BY sortOrder ASC, id ASC")
    fun getAllProviders(): Flow<List<ModelProviderEntity>>

    @Query("SELECT * FROM model_providers WHERE id = :id")
    suspend fun getProviderById(id: String): ModelProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(provider: ModelProviderEntity)

    @Query("UPDATE model_providers SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("UPDATE model_providers SET groupId = :groupId WHERE id = :id")
    suspend fun updateGroup(id: String, groupId: String?)

    @Query("UPDATE model_providers SET groupId = :groupId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateGroupAndSortOrder(id: String, groupId: String?, sortOrder: Int)

    @Query("SELECT * FROM model_providers WHERE groupId = :groupId ORDER BY sortOrder ASC, id ASC")
    suspend fun getProvidersByGroup(groupId: String): List<ModelProviderEntity>

    @Query("SELECT * FROM model_providers WHERE groupId IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun getUngroupedProviders(): List<ModelProviderEntity>

    @Query("UPDATE model_providers SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: String)

    @Query("DELETE FROM model_providers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProviderModelsDao {
    @Query("SELECT * FROM provider_models WHERE id = :providerId")
    suspend fun getModelsForProvider(providerId: String): ProviderModelsEntity?

    @Query("SELECT * FROM provider_models")
    fun getAllModels(): Flow<List<ProviderModelsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(models: ProviderModelsEntity)
}

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY sortOrder ASC, id ASC")
    fun getAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getServerById(id: String): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(server: McpServerEntity)

    @Query("UPDATE mcp_servers SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SpeechServiceDao {
    @Query("SELECT * FROM speech_services ORDER BY sortOrder ASC, id ASC")
    fun getAllSpeechServices(): Flow<List<SpeechServiceEntity>>

    @Query("SELECT * FROM speech_services WHERE id = :id")
    suspend fun getServiceById(id: String): SpeechServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(service: SpeechServiceEntity)

    @Query("UPDATE speech_services SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM speech_services WHERE id = :id")
    suspend fun deleteById(id: String)
}
