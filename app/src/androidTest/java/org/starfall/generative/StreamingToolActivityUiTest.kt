package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.home.chat.model.ActiveChatStreamState
import org.starfall.generative.features.home.chat.model.ChatStreamStatus
import org.starfall.generative.features.home.chat.model.StreamFreezeSignal
import org.starfall.generative.features.home.chat.model.StreamMetricsSnapshot
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.features.home.chat.ui.AssistantMessageCard
import org.starfall.generative.features.home.chat.ui.ProcessingDropdown
import org.starfall.generative.features.home.chat.ui.ProcessingDropdownItem
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.ToolActivity

class StreamingToolActivityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun runningToolAppearsBetweenStreamedTextAndRemainsAvailableAfterCompletion() {
        val before = "Before the action.\n\n"
        val after = "After the action."
        val freezeSignal = StreamFreezeSignal()
        fun stream(text: String, revision: Long) = ActiveChatStreamState(
            conversationId = "conversation",
            messageId = "assistant",
            generationId = 1L,
            revision = revision,
            status = ChatStreamStatus.STREAMING,
            content = StreamTextSnapshot.of(text),
            reasoning = StreamTextSnapshot.of(),
            stopSignal = freezeSignal,
            lastCommitMetrics = null,
            cumulativeMetrics = StreamMetricsSnapshot()
        )
        val streamFlow = MutableStateFlow<ActiveChatStreamState?>(stream(before, 1))
        var version by mutableStateOf(MessageVersion(content = before))
        var isStreaming by mutableStateOf(true)
        val action = ToolActivity("action", "search_documents", contentOffset = before.length)

        compose.setContent { MaterialTheme {
            AssistantMessageCard(
                StoredMessage("assistant", ChatRole.MODEL, listOf(version)),
                isStreaming, {}, {}, {}, {}, {}, {}, onSwitchVersion = {},
                conversationId = "conversation", streamStateFlow = streamFlow
            )
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Before the action.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { version = version.copy(toolActivity = listOf(action)) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("search documents").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("search documents").assertIsDisplayed()
        val beforeBounds = compose.onNodeWithText("Before the action.").fetchSemanticsNode().boundsInRoot
        val actionBounds = compose.onNodeWithText("search documents").fetchSemanticsNode().boundsInRoot
        assertTrue(beforeBounds.bottom <= actionBounds.top)

        compose.runOnIdle {
            version = version.copy(toolActivity = listOf(action.copy(status = "success")))
            streamFlow.value = stream(before + after, 2)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(after).fetchSemanticsNodes().isNotEmpty()
        }
        val afterBounds = compose.onNodeWithText(after).fetchSemanticsNode().boundsInRoot
        assertTrue(beforeBounds.bottom <= afterBounds.top)
        compose.onNodeWithText("search documents").assertDoesNotExist()

        compose.runOnIdle {
            version = version.copy(content = before + after)
            isStreaming = false
        }
        compose.onNodeWithText("Before the action.").assertIsDisplayed()
        compose.onNodeWithText(after).assertIsDisplayed()
        compose.onNodeWithText("Tool executed").performClick()
        compose.onNodeWithText("search documents").assertIsDisplayed()
    }

    @Test fun collapsedTimelineShowsOnlyRunningActions() {
        var activities by mutableStateOf(listOf(
            ToolActivity("done", "finished_action", status = "success"),
            ToolActivity("running-1", "first_action"),
            ToolActivity("running-2", "second_action")
        ))
        compose.setContent { MaterialTheme {
            ProcessingDropdown(
                items = listOf(ProcessingDropdownItem.Thinking("Earlier reasoning")) +
                    activities.map { ProcessingDropdownItem.Tool(it) },
                durationMillis = null,
                startNumber = 1,
                isStreaming = true
            )
        } }
        compose.onNodeWithText("first action").assertIsDisplayed()
        compose.onNodeWithText("second action").assertIsDisplayed()
        compose.onNodeWithText("finished action").assertDoesNotExist()
        compose.onNodeWithText("Earlier reasoning").assertDoesNotExist()

        compose.onNodeWithText("Generating...").performClick()
        compose.onNodeWithText("finished action").assertIsDisplayed()
        compose.onNodeWithText("Generating...").performClick()
        compose.onNodeWithText("finished action").assertDoesNotExist()

        compose.runOnIdle {
            activities = activities.map { if (it.id == "running-1") it.copy(status = "success") else it }
        }
        compose.onNodeWithText("first action").assertDoesNotExist()
        compose.onNodeWithText("second action").assertIsDisplayed()
    }

}
