package org.starfall.multigateway

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.shared.storage.database.AppDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConversationIncrementalPersistenceTest {
    @Test fun appendingAndEditingMessagesDoNotRebuildUnchangedRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val repository = ConversationRepository(db)
            val first = StoredMessage("first", ChatRole.USER, listOf(MessageVersion(content = "Question", files = listOf("question.txt"))))
            val answer = StoredMessage("answer", ChatRole.MODEL, listOf(
                MessageVersion(content = "Answer", toolActivity = listOf(
                    ToolActivity(id = "search", name = "Search", status = "success", files = listOf("result.json"))
                )),
                MessageVersion(content = "Alternative")
            ), activeVersionIndex = 0)
            val original = Conversation("conversation", "Incremental", 1, 2, messages = listOf(first, answer))
            repository.saveConversation(original)
            val rowIds = rowIds(db)

            repository.saveConversation(original.copy(messages = original.messages +
                StoredMessage("tail", ChatRole.USER, listOf(MessageVersion(content = "Follow-up")))))
            assertEquals(rowIds, rowIds(db).filterKeys { it != "new-message" })

            val edited = original.copy(messages = listOf(
                first,
                answer.copy(versions = answer.versions.mapIndexed { index, version ->
                    if (index == 1) version.copy(content = "Edited alternative") else version
                }),
                StoredMessage("tail", ChatRole.USER, listOf(MessageVersion(content = "Follow-up")))
            ))
            repository.saveConversation(edited)
            val afterEdit = rowIds(db)
            assertEquals(rowIds.getValue("first-message"), afterEdit.getValue("first-message"))
            assertEquals(rowIds.getValue("answer-message"), afterEdit.getValue("answer-message"))
            assertEquals(rowIds.getValue("first-version"), afterEdit.getValue("first-version"))
            assertEquals(rowIds.getValue("answer-first-version"), afterEdit.getValue("answer-first-version"))
            assertEquals(rowIds.getValue("answer-activity"), afterEdit.getValue("answer-activity"))
            assertEquals("Edited alternative", repository.loadById("conversation")!!.messages[1].versions[1].content)
            assertFalse(db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        } finally { db.close() }
    }

    private fun rowIds(db: AppDatabase): Map<String, Long> {
        fun rowId(table: String, where: String): Long = db.openHelper.readableDatabase.query(
            "SELECT rowid FROM $table WHERE $where"
        ).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getLong(0) }
        return mapOf(
            "first-message" to rowId("messages", "id = 'first'"),
            "answer-message" to rowId("messages", "id = 'answer'"),
            "first-version" to rowId("message_versions", "messageId = 'first' AND versionIndex = 0"),
            "answer-first-version" to rowId("message_versions", "messageId = 'answer' AND versionIndex = 0"),
            "answer-activity" to rowId("tool_activities", "messageId = 'answer' AND activityIndex = 0")
        )
    }
}
