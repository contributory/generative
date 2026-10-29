package org.starfall.generative

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.features.home.chat.logic.SAVE_CONVERSATION_ERROR_PREFIX
import org.starfall.generative.features.home.chat.ui.AssistantMessageCard
import org.starfall.generative.features.home.chat.ui.ChatScreen
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage

/** Exercises the two real chat call sites of the shared error surface. */
@RunWith(AndroidJUnit4::class)
class ChatErrorContainerDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    @Test fun assistantGenerationErrorSupportsRetryExpansionAndFullCopy() {
        val error = (1..7).joinToString("\n") { "HTTP failure detail $it" }
        var retries = 0
        val message = StoredMessage(
            id = "failed-answer",
            role = ChatRole.MODEL,
            versions = listOf(MessageVersion(content = "Partial answer", generationError = error))
        )

        compose.setContent {
            MaterialTheme {
                AssistantMessageCard(
                    message = message,
                    isStreaming = false,
                    onCopy = {},
                    onRegenerate = {},
                    onRetryError = { retries++ },
                    onEdit = {},
                    onDelete = {},
                    onRead = {},
                    onSwitchVersion = {}
                )
            }
        }

        compose.onNodeWithText("Partial answer", substring = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.message_generation_error)).assertIsDisplayed()
        compose.onNodeWithTag("error-container-toggle").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.message_collapse_error))
            .assertExists()
        compose.onNodeWithTag("error-container-copy").performClick()
        compose.runOnIdle { assertEquals(error, clipboardText()) }
        compose.onNodeWithText(context.getString(R.string.common_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun chatSaveFailureUsesDismissibleCompactContainer() {
        val message = "disk full"
        val expected = "${context.getString(R.string.chat_save_conversation_failed_prefix)} $message"
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    conversation = Conversation(
                        id = "chat-error-test", title = "Chat", createdAt = 0L, updatedAt = 0L
                    ),
                    isGenerating = false,
                    generatingConversationId = null,
                    chatError = "$SAVE_CONVERSATION_ERROR_PREFIX $message",
                    providers = emptyList(),
                    selectedProviderId = "",
                    selectedModelName = "",
                    onSendMessage = { _, _ -> false },
                    onSendMedia = { false },
                    onStopGenerating = {},
                    onOpenDrawer = {},
                    onNewChat = {},
                    onOpenSettings = {},
                    onRegenerate = {},
                    onEditMessage = { _, _, _ -> false },
                    onDeleteMessage = {},
                    onDeleteMessageVersion = {},
                    onSwitchVersion = { _, _ -> },
                    onSelectModel = { _, _ -> },
                    summaryProgress = null,
                    onSetReasoningEffort = {},
                    onStartConversationSummary = { false },
                    onSummaryRoleChange = {},
                    onDeleteSummary = {},
                    onReadMessage = { _, _ -> }
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("error-container-dismiss").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(expected).assertIsDisplayed()
        compose.onNodeWithTag("error-container-toggle").assertDoesNotExist()
        compose.onNodeWithTag("error-container-copy").performClick()
        compose.runOnIdle { assertEquals(expected, clipboardText()) }
        compose.onNodeWithTag("error-container-dismiss").performClick()
        compose.onNodeWithText(expected).assertDoesNotExist()
    }
}
