package org.starfall.multigateway.home.chat_screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.ToolActivity

class ConversationTitleTriggerTest {
    @Test
    fun titleWaitsForTenWordsOrFiveUserAndAssistantMessages() {
        assertFalse(shouldGenerateConversationTitle("one two three four five six seven eight nine", 2))
        assertTrue(shouldGenerateConversationTitle("one two three four five six seven eight nine ten", 2))
        assertFalse(shouldGenerateConversationTitle("short message", 4))
        assertTrue(shouldGenerateConversationTitle("short message", 5))
    }

    @Test
    fun directMediaConversationIsRecognizedAndKeepsPromptBasedTitle() {
        val conversation = Conversation(
            id = "media",
            title = "A prompt for image generation",
            createdAt = 1,
            updatedAt = 1,
            messages = listOf(
                StoredMessage(
                    id = "user",
                    role = ChatRole.USER,
                    versions = listOf(MessageVersion(content = "A prompt for image generation"))
                ),
                StoredMessage(
                    id = "assistant",
                    role = ChatRole.MODEL,
                    versions = listOf(
                        MessageVersion(
                            toolActivity = listOf(ToolActivity(id = "tool", name = "generate_image"))
                        )
                    )
                )
            )
        )

        assertTrue(conversation.startedWithDirectMedia())
        assertFalse(conversation.copy(messages = emptyList()).startedWithDirectMedia())
    }
}
