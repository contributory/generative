package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.ui.assistantCopyText
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.ToolActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssistantMessageCopyTextTest {
    @Test
    fun toolOnlyResponseHasCopyableActionPlaceholder() {
        val message = StoredMessage(
            id = "assistant",
            role = ChatRole.MODEL,
            versions = listOf(MessageVersion(toolActivity = listOf(ToolActivity(id = "tool", name = "search"))))
        )
        assertEquals("[ActionTurnsBlock1]", assistantCopyText(message))
    }

    @Test
    fun hiddenInternalToolCallStillProducesCopyableContent() {
        val message = StoredMessage("internal", ChatRole.MODEL, listOf(
            MessageVersion(toolActivity = listOf(ToolActivity(id = "internal", name = "remote: connect")))
        ))
        assertEquals("[ActionTurnsBlock1]", assistantCopyText(message))
    }

    @Test
    fun sendFileOnlyAndDetachedFilesStillProduceCopyableContent() {
        val sendFile = StoredMessage("send-file", ChatRole.MODEL, listOf(
            MessageVersion(toolActivity = listOf(
                ToolActivity(id = "file", name = "send_file", files = listOf("tool-file:document.pdf"))
            ))
        ))
        val attached = StoredMessage("file", ChatRole.MODEL, listOf(
            MessageVersion(files = listOf("image.png"))
        ))
        val unfinishedSendFile = StoredMessage("no-file", ChatRole.MODEL, listOf(
            MessageVersion(toolActivity = listOf(ToolActivity(id = "pending", name = "send_file")))
        ))
        assertEquals("[MediaBlock1]", assistantCopyText(sendFile))
        assertEquals("[MediaBlock1]", assistantCopyText(attached))
        assertEquals("[ActionTurnsBlock1]", assistantCopyText(unfinishedSendFile))
    }

    @Test
    fun thinkingAndErrorWithoutAnswerUseTheSameCopyRepresentationAsShare() {
        val thinking = StoredMessage("thinking", ChatRole.MODEL,
            listOf(MessageVersion(reasoningContent = "**Original reasoning**")))
        val error = StoredMessage("error", ChatRole.MODEL,
            listOf(MessageVersion(generationError = "Request failed")))
        val answered = StoredMessage("answered", ChatRole.MODEL,
            listOf(MessageVersion(content = "Answer", reasoningContent = "Thinking")))
        assertEquals("[ActionTurnsBlock1]", assistantCopyText(thinking))
        assertEquals("Request failed", assistantCopyText(error))
        assertEquals("[ActionTurnsBlock1]Answer", assistantCopyText(answered))
    }

    @Test
    fun emptyResponseDoesNotProducePhantomCopyContent() {
        val empty = StoredMessage("empty", ChatRole.MODEL, listOf(MessageVersion()))
        assertEquals("", assistantCopyText(empty))
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

        assertEquals("before [ActionTurnsBlock1][MediaBlock2]after", assistantCopyText(message))
    }
}
