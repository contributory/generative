package org.starfall.generative

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.withStructuralMessageChange
import org.starfall.generative.features.home.chat.logic.withUpdatedMessageStructure
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage

class ConversationStructureRevisionTest {
    @Test
    fun contentAndVersionUpdatesDoNotAdvanceTheStructuralRevision() {
        val original = conversation()
        val textEdited = original.withUpdatedMessageStructure(original.messages.map { message ->
            if (message.id == "answer") message.copy(versions = listOf(message.activeVersion.copy(content = "updated")))
            else message
        })

        assertEquals(0L, textEdited.structuralRevision)
        assertEquals("updated", textEdited.messages.last().content)
    }

    @Test
    fun appendRemoveReorderRoleAndQueuedChangesAdvanceTheRevision() {
        val original = conversation()
        val added = StoredMessage("second", ChatRole.USER, listOf(MessageVersion(content = "next")))

        assertEquals(1L, original.withUpdatedMessageStructure(original.messages + added).structuralRevision)
        assertEquals(1L, original.withUpdatedMessageStructure(original.messages.dropLast(1)).structuralRevision)
        assertEquals(1L, original.withUpdatedMessageStructure(original.messages.reversed()).structuralRevision)
        assertEquals(1L, original.withUpdatedMessageStructure(
            original.messages.map { if (it.id == "answer") it.copy(role = ChatRole.USER) else it }
        ).structuralRevision)
        assertEquals(1L, original.withUpdatedMessageStructure(
            original.messages.map { if (it.id == "question") it.copy(isQueued = true) else it }
        ).structuralRevision)
    }

    @Test
    fun explicitStructuralBoundaryAlwaysAdvancesAndDoesNotSerializeTheEpoch() {
        val original = conversation()
        val changed = original.withStructuralMessageChange(original.messages +
            StoredMessage("second", ChatRole.USER, listOf(MessageVersion(content = "next"))))
        val serialized = Json.encodeToString(changed)
        val restored = Json.decodeFromString<Conversation>(serialized)

        assertEquals(1L, changed.structuralRevision)
        assertEquals(0L, restored.structuralRevision)
        assertFalse(serialized.contains("structuralRevision"))
        assertFalse(serialized.contains("structural_revision"))
        assertTrue(restored.messages.any { it.id == "second" })
    }

    private fun conversation() = Conversation(
        id = "conversation",
        title = "Chat",
        createdAt = 1L,
        updatedAt = 2L,
        messages = listOf(
            StoredMessage("question", ChatRole.USER, listOf(MessageVersion(content = "Question"))),
            StoredMessage("answer", ChatRole.MODEL, listOf(MessageVersion(content = "Answer")))
        )
    )
}
