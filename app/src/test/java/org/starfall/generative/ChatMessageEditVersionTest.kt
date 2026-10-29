package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.service.editedMessageVersion
import org.starfall.generative.features.home.chat.logic.service.deleteAssistantBlock
import org.starfall.generative.features.home.chat.ui.assistantCopyText
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.ToolActivity

class ChatMessageEditVersionTest {
    @Test fun editingEmptyAssistantContentRetainsActionsAndThinking() {
        val action = ToolActivity(id = "tool", name = "search", status = "success")
        val source = MessageVersion(
            reasoningContent = "Reasoning",
            reasoningSignature = "signature",
            toolActivity = listOf(action),
            generationInterrupted = true,
            generationError = "Interrupted",
            providerId = "provider",
            modelId = "model"
        )
        val updated = editedMessageVersion(
            StoredMessage("reply", ChatRole.MODEL, listOf(source)),
            "Manually completed response", emptyList(), "123"
        )
        assertEquals("Manually completed response", updated.content)
        assertEquals(source.toolActivity, updated.toolActivity)
        assertEquals(source.reasoningContent, updated.reasoningContent)
        assertEquals(source.reasoningSignature, updated.reasoningSignature)
        assertEquals("provider", updated.providerId)
        assertEquals("model", updated.modelId)
        assertEquals("123", updated.timestamp)
        assertNull(updated.generationError)
        assertFalse(updated.generationInterrupted)
    }

    @Test fun missingMarkersMoveEveryBlockBeforeContentWithoutDeletingThem() {
        val initial = MessageVersion(
            content = "first second third",
            toolActivity = listOf(
                ToolActivity("a", "search", contentOffset = 0),
                ToolActivity("b", "search", contentOffset = 6),
                ToolActivity("c", "generate_image", contentOffset = 13,
                    files = listOf("tool-file:photo.png"))
            ),
            files = listOf("file.png")
        )
        val updated = editedMessageVersion(
            StoredMessage("reply", ChatRole.MODEL, listOf(initial)),
            "New answer", initial.files, "now"
        )
        assertEquals("New answer", updated.content)
        assertEquals(initial.toolActivity.map { it.id }, updated.toolActivity.map { it.id })
        assertEquals(initial.files, updated.files)
        assertEquals("[ActionTurnsBlock1][ActionTurnsBlock2][ActionTurnsBlock3][MediaBlock4][MediaBlock5]New answer",
            assistantCopyText(StoredMessage("reply", ChatRole.MODEL, listOf(updated))))
    }

    @Test fun knownNumberedMarkersMoveBlocksWithoutBecomingContent() {
        val original = MessageVersion(content = "first second", toolActivity = listOf(
            ToolActivity("a", "search", contentOffset = 0),
            ToolActivity("b", "search", contentOffset = 6)
        ))
        val message = StoredMessage("reply", ChatRole.MODEL, listOf(original))
        assertEquals("[ActionTurnsBlock1]first [ActionTurnsBlock2]second", assistantCopyText(message))
        val edited = editedMessageVersion(message,
            "[ActionTurnsBlock2]front [ActionTurnsBlock1]back", emptyList(), "2")
        assertEquals("front back", edited.content)
        assertEquals(listOf("a", "b"), edited.toolActivity.map { it.id })
        assertEquals("[ActionTurnsBlock1]front [ActionTurnsBlock2]back",
            assistantCopyText(message.copy(versions = listOf(edited))))
    }

    @Test fun mediaCanMoveIndependentlyFromItsActionBlock() {
        val original = MessageVersion(content = "answer", toolActivity = listOf(
            ToolActivity("photo", "generate_image", files = listOf("tool-file:photo.png"))
        ), files = listOf("attachment.png"))
        val message = StoredMessage("reply", ChatRole.MODEL, listOf(original))
        val edited = editedMessageVersion(message,
            "[MediaBlock2]before [ActionTurnsBlock1]after [MediaBlock3]",
            original.files, "3")
        assertEquals("before after ", edited.content)
        assertEquals("[MediaBlock1]before [ActionTurnsBlock2]after [MediaBlock3]",
            assistantCopyText(message.copy(versions = listOf(edited))))
        assertEquals(listOf("tool-file:photo.png"), edited.toolActivity.single().files)
    }

    @Test fun removingFileMarkersOrComposerAttachmentsCannotDeleteExistingMedia() {
        val version = MessageVersion(content = "", files = listOf("original.png"))
        val updated = editedMessageVersion(
            StoredMessage("reply", ChatRole.MODEL, listOf(version)),
            "New answer without any marker", emptyList(), "2"
        )
        assertEquals(listOf("original.png"), updated.files)
        assertEquals("[MediaBlock1]New answer without any marker",
            assistantCopyText(StoredMessage("reply", ChatRole.MODEL, listOf(updated))))
    }

    @Test fun adjacentMarkersRetainTheirExplicitOrderEvenAtTheSameOffset() {
        val version = MessageVersion(toolActivity = listOf(
            ToolActivity("image", "generate_image", files = listOf("tool-file:image.png"))
        ))
        val source = StoredMessage("reply", ChatRole.MODEL, listOf(version))
        assertEquals("[ActionTurnsBlock1][MediaBlock2]", assistantCopyText(source))
        val moved = editedMessageVersion(source, "[MediaBlock2][ActionTurnsBlock1]", emptyList(), "3")
        assertEquals("", moved.content)
        assertEquals("[MediaBlock1][ActionTurnsBlock2]",
            assistantCopyText(source.copy(versions = listOf(moved))))
    }

    @Test fun deletingMediaKeepsActionsAndRemovingActionsKeepsContent() {
        val original = MessageVersion(content = "answer", toolActivity = listOf(
            ToolActivity("photo", "generate_image",
                files = listOf("tool-file:photo.png", "tool-file:trace.json"))
        ))
        val withoutMedia = deleteAssistantBlock(original, 2)
        assertEquals(listOf("tool-file:trace.json"), withoutMedia.toolActivity.single().files)
        assertEquals("answer", withoutMedia.content)
        assertEquals("[ActionTurnsBlock1]answer",
            assistantCopyText(StoredMessage("reply", ChatRole.MODEL, listOf(withoutMedia))))
        val withoutActions = deleteAssistantBlock(original, 1)
        assertEquals(emptyList<ToolActivity>(), withoutActions.toolActivity)
        assertEquals(listOf("tool-file:photo.png"), withoutActions.files)
        assertEquals("[MediaBlock1]answer",
            assistantCopyText(StoredMessage("reply", ChatRole.MODEL, listOf(withoutActions))))
        assertEquals("answer", withoutActions.content)
    }

    @Test fun deletingSendFileOnlyMediaLeavesNoPhantomActionBlock() {
        val original = MessageVersion(toolActivity = listOf(
            ToolActivity("attachment", "send_file", files = listOf("tool-file:file.png"))
        ))
        assertEquals("[MediaBlock1]",
            assistantCopyText(StoredMessage("reply", ChatRole.MODEL, listOf(original))))
        val deleted = deleteAssistantBlock(original, 1)
        assertEquals(emptyList<ToolActivity>(), deleted.toolActivity)
        assertEquals("", assistantCopyText(StoredMessage("reply", ChatRole.MODEL, listOf(deleted))))
    }

    @Test fun deletingOnlyThinkingDoesNotEraseContentOrOtherVersions() {
        val message = MessageVersion(content = "answer", reasoningContent = "Thinking")
        val removed = deleteAssistantBlock(message, 1)
        assertNull(removed.reasoningContent)
        assertEquals("answer", removed.content)
    }

    @Test fun editingUserMessageKeepsNormalUserVersionSemantics() {
        val source = MessageVersion(content = "Before", modelId = "model")
        val updated = editedMessageVersion(
            StoredMessage("user", ChatRole.USER, listOf(source)), "After", emptyList(), "2"
        )
        assertEquals("After", updated.content)
        assertEquals("model", updated.modelId)
        assertEquals(emptyList<ToolActivity>(), updated.toolActivity)
    }
}
