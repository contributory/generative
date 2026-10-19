package org.starfall.multigateway.shared.storage.database

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.ConversationSummary
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage

/** Converts the v9 conversation JSON aggregate into the v10 relational conversation tables. */
internal object ConversationMigration9To10 {
    private const val SOURCE_TABLE = "conversations_v9_source"
    private val migrationJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations RENAME TO $SOURCE_TABLE")
        createTables(db)
        db.query(
            "SELECT id, title, createdAt, updatedAt, messagesJson, tokenCount, providerId, modelId, profileId, summaryJson, reasoningEffort FROM $SOURCE_TABLE ORDER BY rowid"
        ).use { rows ->
            while (rows.moveToNext()) migrateConversation(db, rows)
        }
        db.execSQL("DROP TABLE $SOURCE_TABLE")
        db.query("PRAGMA foreign_key_check").use { violations ->
            check(!violations.moveToFirst()) { "Conversation v9 to v10 migration created orphaned rows." }
        }
    }

    private fun migrateConversation(db: SupportSQLiteDatabase, rows: android.database.Cursor) {
        val id = rows.getString(0)
        db.execSQL(
            "INSERT INTO conversations (id, title, createdAt, updatedAt, tokenCount, providerId, modelId, profileId, reasoningEffort) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            args(id, rows.getString(1), rows.getLong(2), rows.getLong(3), rows.value(5), rows.getString(6), rows.getString(7), rows.value(8), rows.value(10))
        )

        val messagesRaw = rows.getString(4)
        val messages = runCatching { migrationJson.decodeFromString<List<StoredMessage>>(messagesRaw) }.getOrElse {
            quarantine(db, id, "messages_json", messagesRaw, "invalid_json")
            null
        }
        if (messages != null) {
            val ids = messages.map { it.id }
            if (ids.size != ids.toSet().size) {
                quarantine(db, id, "messages_integrity", messagesRaw, "duplicate_message_id")
            } else {
                messages.forEachIndexed { sequence, message -> migrateMessage(db, id, sequence, message) }
            }
        }

        val summaryRaw = rows.value(9) as String?
        if (summaryRaw != null) {
            val summary = runCatching { migrationJson.decodeFromString<ConversationSummary>(summaryRaw) }.getOrElse {
                quarantine(db, id, "summary_json", summaryRaw, "invalid_json")
                null
            }
            if (summary != null) {
                val hasBoundary = db.query(
                    "SELECT 1 FROM messages WHERE conversationId = ? AND id = ? LIMIT 1",
                    arrayOf(id, summary.throughMessageId)
                ).use { it.moveToFirst() }
                if (hasBoundary) {
                    db.execSQL(
                        "INSERT INTO conversation_summaries (conversationId, id, content, throughMessageId, role, createdAt) VALUES (?, ?, ?, ?, ?, ?)",
                        args(id, summary.id, summary.content, summary.throughMessageId, summary.role.name, summary.createdAt)
                    )
                } else {
                    quarantine(db, id, "summary_boundary_missing", summaryRaw, "missing_summary_boundary")
                }
            }
        }
    }

    private fun migrateMessage(db: SupportSQLiteDatabase, conversationId: String, sequence: Int, message: StoredMessage) {
        db.execSQL(
            "INSERT INTO messages (conversationId, id, sequence, role, activeVersionIndex) VALUES (?, ?, ?, ?, ?)",
            args(conversationId, message.id, sequence, message.role.persistedValue, message.activeVersionIndex)
        )
        message.versions.forEachIndexed { versionIndex, original ->
            val version = normalizeVersion(message.role, original)
            db.execSQL(
                "INSERT INTO message_versions (conversationId, messageId, versionIndex, content, timestamp, reasoningContent, reasoningSignature, processingFinishedAt, providerId, modelId, modelDisplayName, generationInterrupted, generationError) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                args(conversationId, message.id, versionIndex, version.content, version.timestamp, version.reasoningContent, version.reasoningSignature, version.processingFinishedAt, version.providerId, version.modelId, version.modelDisplayName, version.generationInterrupted, version.generationError)
            )
            version.files.forEachIndexed { fileIndex, reference ->
                db.execSQL(
                    "INSERT INTO message_version_files (conversationId, messageId, versionIndex, fileIndex, reference) VALUES (?, ?, ?, ?, ?)",
                    args(conversationId, message.id, versionIndex, fileIndex, reference)
                )
            }
            version.toolActivity.forEachIndexed { activityIndex, activity ->
                db.execSQL(
                    "INSERT INTO tool_activities (conversationId, messageId, versionIndex, activityIndex, activityId, name, wireName, status, summary, arguments, response, contentOffset, reasoningOffset, inlineMedia, responsePreviewLimit) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    args(conversationId, message.id, versionIndex, activityIndex, activity.id, activity.name, activity.wireName, activity.status, activity.summary, activity.arguments, activity.response, activity.contentOffset, activity.reasoningOffset, activity.inlineMedia, activity.responsePreviewLimit)
                )
                activity.files.forEachIndexed { fileIndex, reference ->
                    db.execSQL(
                        "INSERT INTO tool_activity_files (conversationId, messageId, versionIndex, activityIndex, fileIndex, reference) VALUES (?, ?, ?, ?, ?, ?)",
                        args(conversationId, message.id, versionIndex, activityIndex, fileIndex, reference)
                    )
                }
            }
        }
    }

    private fun normalizeVersion(role: ChatRole, version: MessageVersion): MessageVersion {
        val recovered = if (role == ChatRole.MODEL) recoverLegacyGenerationError(version) else version
        return recovered.copy(toolActivity = recovered.toolActivity.map { activity ->
            if (activity.status == "running") activity.copy(status = "interrupted", summary = "The previous run did not finish.") else activity
        })
    }

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

    private fun quarantine(db: SupportSQLiteDatabase, conversationId: String, kind: String, rawJson: String?, errorCode: String) {
        db.execSQL(
            "INSERT INTO conversation_migration_quarantine (conversationId, payloadKind, rawJson, errorCode) VALUES (?, ?, ?, ?)",
            args(conversationId, kind, rawJson, errorCode)
        )
    }

    private fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, tokenCount INTEGER, providerId TEXT NOT NULL, modelId TEXT NOT NULL, profileId TEXT, reasoningEffort TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX index_conversations_updatedAt ON conversations(updatedAt, id)")
        db.execSQL("CREATE TABLE messages (conversationId TEXT NOT NULL, id TEXT NOT NULL, sequence INTEGER NOT NULL, role TEXT NOT NULL, activeVersionIndex INTEGER NOT NULL, PRIMARY KEY(conversationId, id), FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX index_messages_conversation_sequence ON messages(conversationId, sequence)")
        db.execSQL("CREATE TABLE message_versions (conversationId TEXT NOT NULL, messageId TEXT NOT NULL, versionIndex INTEGER NOT NULL, content TEXT NOT NULL, timestamp TEXT NOT NULL, reasoningContent TEXT, reasoningSignature TEXT, processingFinishedAt INTEGER, providerId TEXT NOT NULL, modelId TEXT NOT NULL, modelDisplayName TEXT NOT NULL, generationInterrupted INTEGER NOT NULL, generationError TEXT, PRIMARY KEY(conversationId, messageId, versionIndex), FOREIGN KEY(conversationId, messageId) REFERENCES messages(conversationId, id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_message_versions_conversation_message ON message_versions(conversationId, messageId)")
        db.execSQL("CREATE TABLE tool_activities (conversationId TEXT NOT NULL, messageId TEXT NOT NULL, versionIndex INTEGER NOT NULL, activityIndex INTEGER NOT NULL, activityId TEXT NOT NULL, name TEXT NOT NULL, wireName TEXT NOT NULL, status TEXT NOT NULL, summary TEXT NOT NULL, arguments TEXT NOT NULL, response TEXT NOT NULL, contentOffset INTEGER NOT NULL, reasoningOffset INTEGER, inlineMedia INTEGER NOT NULL, responsePreviewLimit INTEGER NOT NULL, PRIMARY KEY(conversationId, messageId, versionIndex, activityIndex), FOREIGN KEY(conversationId, messageId, versionIndex) REFERENCES message_versions(conversationId, messageId, versionIndex) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_tool_activities_conversation_message_version ON tool_activities(conversationId, messageId, versionIndex)")
        db.execSQL("CREATE INDEX index_tool_activities_activityId ON tool_activities(activityId)")
        db.execSQL("CREATE TABLE message_version_files (conversationId TEXT NOT NULL, messageId TEXT NOT NULL, versionIndex INTEGER NOT NULL, fileIndex INTEGER NOT NULL, reference TEXT NOT NULL, PRIMARY KEY(conversationId, messageId, versionIndex, fileIndex), FOREIGN KEY(conversationId, messageId, versionIndex) REFERENCES message_versions(conversationId, messageId, versionIndex) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_message_version_files_conversation_message_version ON message_version_files(conversationId, messageId, versionIndex)")
        db.execSQL("CREATE INDEX index_message_version_files_reference ON message_version_files(reference)")
        db.execSQL("CREATE TABLE tool_activity_files (conversationId TEXT NOT NULL, messageId TEXT NOT NULL, versionIndex INTEGER NOT NULL, activityIndex INTEGER NOT NULL, fileIndex INTEGER NOT NULL, reference TEXT NOT NULL, PRIMARY KEY(conversationId, messageId, versionIndex, activityIndex, fileIndex), FOREIGN KEY(conversationId, messageId, versionIndex, activityIndex) REFERENCES tool_activities(conversationId, messageId, versionIndex, activityIndex) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_tool_activity_files_conversation_message_version_activity ON tool_activity_files(conversationId, messageId, versionIndex, activityIndex)")
        db.execSQL("CREATE INDEX index_tool_activity_files_reference ON tool_activity_files(reference)")
        db.execSQL("CREATE TABLE conversation_summaries (conversationId TEXT NOT NULL, id TEXT NOT NULL, content TEXT NOT NULL, throughMessageId TEXT NOT NULL, role TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(conversationId), FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(conversationId, throughMessageId) REFERENCES messages(conversationId, id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_conversation_summaries_conversation_throughMessage ON conversation_summaries(conversationId, throughMessageId)")
        db.execSQL("CREATE TABLE conversation_migration_quarantine (conversationId TEXT NOT NULL, payloadKind TEXT NOT NULL, rawJson TEXT, errorCode TEXT NOT NULL, PRIMARY KEY(conversationId, payloadKind), FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_conversation_migration_quarantine_conversation ON conversation_migration_quarantine(conversationId)")
    }

    private fun args(vararg values: Any?): Array<Any?> {
        val result = arrayOfNulls<Any>(values.size)
        values.forEachIndexed { index, value -> result[index] = value }
        return result
    }

    private fun android.database.Cursor.value(index: Int): Any? = if (isNull(index)) null else getString(index)

    private val ChatRole.persistedValue: String
        get() = when (this) {
            ChatRole.USER -> "user"
            ChatRole.MODEL -> "model"
            ChatRole.SYSTEM -> "system"
        }
}
