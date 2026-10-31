package org.starfall.generative

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.model.*
import org.starfall.generative.features.home.chat.ui.ChatRenderCaches
import org.starfall.generative.features.home.chat.ui.ChatScreen
import org.starfall.generative.features.home.chat.ui.chatAppBarOverlayAlpha
import org.starfall.generative.shared.model.*

/** Whole-chat regressions: mutate live state without recreating the screen or persisting chunks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ChatScreenScenarioRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clearCaches() = ChatRenderCaches.clearAll()

    private fun message(id: String, role: ChatRole, text: String = "") =
        StoredMessage(id, role, listOf(MessageVersion(content = text)))

    private fun conversation(vararg messages: StoredMessage) = Conversation(
        id = "chat", title = "Chat", createdAt = 0, updatedAt = 0,
        messages = messages.toList(), structuralRevision = messages.size.toLong()
    )

    private class Scenario(initial: Conversation) {
        var saved by mutableStateOf(initial)
        var generating by mutableStateOf(true)
        var autoScroll by mutableStateOf(true)
        val stream = MutableStateFlow<ActiveChatStreamState?>(null)
        private val freeze = StreamFreezeSignal()
        private var revision = 0L

        fun chunk(text: String, reasoning: String = "", status: ChatStreamStatus = ChatStreamStatus.STREAMING) {
            stream.value = ActiveChatStreamState(
                conversationId = "chat", messageId = "reply", generationId = 1L,
                revision = ++revision, status = status,
                content = StreamTextSnapshot.of(text), reasoning = StreamTextSnapshot.of(reasoning),
                stopSignal = freeze, lastCommitMetrics = null,
                cumulativeMetrics = StreamMetricsSnapshot()
            )
        }

        fun updateReply(version: MessageVersion) {
            val messages = saved.messages
            saved = saved.copy(messages = messages.dropLast(1) + messages.last().copy(versions = listOf(version)))
        }
    }

    private fun show(env: Scenario, onDrawer: () -> Unit = {}, onNew: () -> Unit = {}, onSettings: () -> Unit = {}) {
        compose.setContent { MaterialTheme {
            ChatScreen(
                conversation = env.saved, isGenerating = env.generating,
                generatingConversationId = env.saved.id, chatError = null,
                providers = emptyList(), selectedProviderId = "", selectedModelName = "",
                onNewChat = onNew, onSendMessage = { _, _ -> false }, onSendMedia = { false },
                onStopGenerating = {}, onOpenDrawer = onDrawer, onOpenSettings = onSettings,
                onRegenerate = {}, onEditMessage = { _, _, _ -> false },
                onDeleteMessage = {}, onDeleteMessageVersion = {},
                onSwitchVersion = { _, _ -> }, onSelectModel = { _, _ -> },
                summaryProgress = null, onSetReasoningEffort = {},
                onStartConversationSummary = { false }, onSummaryRoleChange = {},
                onDeleteSummary = {}, onReadMessage = { _, _ -> },
                autoScroll = env.autoScroll, activeStream = env.stream
            )
        } }
        compose.onNodeWithTag("chat-app-bar").assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("chat-message-list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun appBarKeepsControlsInteractiveAndScrimIndependentOfChatPhoto() {
        var drawer = 0
        var created = 0
        var settings = 0
        val env = Scenario(conversation(message("user", ChatRole.USER, "Question"), message("reply", ChatRole.MODEL)))
        show(env, onDrawer = { drawer++ }, onNew = { created++ }, onSettings = { settings++ })

        compose.onNodeWithContentDescription(context.getString(R.string.chat_open_side_navigation)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.sidebar_new_chat)).performClick()
        // The profile/logo image describes the settings action inside its IconButton.
        compose.onNodeWithContentDescription(context.getString(R.string.app_name)).performClick()
        compose.runOnIdle { assertEquals(1, drawer); assertEquals(1, created); assertEquals(1, settings) }
        assertEquals(0.60f, chatAppBarOverlayAlpha(0f), 0.0001f)
        assertEquals(0.30f, chatAppBarOverlayAlpha(0.5f), 0.0001f)
        assertEquals(0f, chatAppBarOverlayAlpha(1f), 0.0001f)
        assertEquals("Only tapping the settings icon invokes its callback", 1, settings)
    }

    @Test fun streamingTextAppearsBeforeTheStoredMessageChangesAndSurvivesCompletion() {
        val env = Scenario(conversation(message("user", ChatRole.USER, "Question"), message("reply", ChatRole.MODEL)))
        env.chunk("Live response first part.")
        show(env)
        awaitText("Live response first part.")
        assertEquals("The retained conversation snapshot must not change for the first chunk", "", env.saved.messages.last().content)

        compose.runOnIdle { env.chunk("Live response first part. Follow-up part.") }
        awaitText("Follow-up part.")
        assertEquals("", env.saved.messages.last().content)

        compose.runOnIdle {
            env.updateReply(MessageVersion(content = "Live response first part. Follow-up part."))
            env.generating = false
            env.stream.value = null
        }
        awaitText("Follow-up part.")
        compose.onNodeWithTag("chat-app-bar").assertIsDisplayed()
        assertEquals("Live response first part. Follow-up part.", env.saved.messages.last().content)
    }

    @Test fun inScreenMediaPlaceholderAppearsDuringToolCallAndRetractsOnCompletion() {
        val env = Scenario(conversation(
            message("user", ChatRole.USER, "Generate an image"),
            message("reply", ChatRole.MODEL)
        ))
        env.chunk("")
        show(env)
        compose.runOnIdle {
            env.updateReply(MessageVersion(toolActivity = listOf(
                ToolActivity("image-in-chat", "generate_image", status = "running")
            )))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("media-generation-placeholder_image-in-chat").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("media-generation-placeholder_image-in-chat").assertIsDisplayed()
        compose.runOnIdle {
            env.updateReply(MessageVersion(toolActivity = listOf(
                ToolActivity("image-in-chat", "generate_image", status = "success")
            )))
            env.generating = false
            env.stream.value = null
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("media-generation-placeholder_image-in-chat").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("chat-app-bar").assertIsDisplayed()
    }

    @Test fun liveThinkingAndToolCallAppearBeforeAnswerFinishes() {
        val env = Scenario(conversation(message("user", ChatRole.USER, "Run the search"), message("reply", ChatRole.MODEL)))
        env.chunk("", reasoning = "Reasoning before tool")
        show(env)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("live-thinking-preview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("live-thinking-preview").assertIsDisplayed()

        compose.runOnIdle {
            env.updateReply(MessageVersion(toolActivity = listOf(ToolActivity("search-1", "search_documents", status = "running"))))
        }
        awaitText("search documents")
        compose.onNodeWithText("search documents").assertExists()
        compose.onNodeWithTag("processing-dropdown-toggle").assertExists()

        compose.runOnIdle {
            env.updateReply(MessageVersion(
                content = "The tool returned results.",
                toolActivity = listOf(ToolActivity("search-1", "search_documents", status = "success"))
            ))
            env.chunk("The tool returned results.")
            env.generating = false
            env.stream.value = null
        }
        compose.onNodeWithTag("processing-dropdown-toggle").performClick()
        awaitText("search documents")
        awaitText("The tool returned results.")
    }
}
