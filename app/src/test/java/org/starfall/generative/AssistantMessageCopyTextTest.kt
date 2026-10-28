package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.assistantCopyText
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.ToolActivity

class AssistantMessageCopyTextTest {
    @Test
    fun toolOnlyResponseHasCopyableActionPlaceholder() {
        val message = StoredMessage(
            id = "assistant",
            role = ChatRole.MODEL,
            versions = listOf(MessageVersion(toolActivity = listOf(ToolActivity(id = "tool", name = "search"))))
        )
        assertEquals("[ActionTurnsBlock]", assistantCopyText(message))
    }

    @Test
    fun actionAndMediaBlocksUseSelectionPlaceholdersAtTheirContentOffsets() {
        val message = StoredMessage(
            id = "assistant",
            role = ChatRole.MODEL,
            versions = listOf(
                MessageVersion(
                    content = "before after",
                    toolActivity = listOf(
                        ToolActivity(id = "tool", name = "search", contentOffset = 7),
                        ToolActivity(
                            id = "media",
                            name = "send_file",
                            contentOffset = 7,
                            files = listOf("tool-file:image.png")
                        )
                    )
                )
            )
        )

        assertEquals("before[ActionTurnsBlock][MediaBlock]after", assistantCopyText(message))
    }
}
