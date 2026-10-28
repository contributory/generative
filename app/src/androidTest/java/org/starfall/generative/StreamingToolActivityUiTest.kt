package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test fun completedToolOnlyResponseShowsActionToolbar() {
        var copied = false
        compose.setContent { MaterialTheme {
            AssistantMessageCard(
                message = StoredMessage(
                    "tool-only", ChatRole.MODEL,
                    listOf(MessageVersion(toolActivity = listOf(
                        ToolActivity(id = "tool", name = "search_documents", status = "success")
                    )))
                ),
                isStreaming = false,
                onCopy = { copied = true },
                onRegenerate = {},
                onEdit = {},
                onDelete = {},
                onRead = {},
                onSwitchVersion = {}
            )
        } }
        val copyLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.common_copy)
        compose.onNodeWithContentDescription(copyLabel).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(copied) }
    }

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
            streamFlow.value = null // ChatGeneration clears the stream after normal completion.
        }
        compose.onNodeWithText("Before the action.").assertIsDisplayed()
        compose.onNodeWithText(after).assertIsDisplayed()
        compose.onNodeWithText("Tool executed").performClick()
        compose.onNodeWithText("search documents").assertIsDisplayed()
    }

    @Test fun stoppingBeforeTheIncrementalParserRunsNeverHidesCommittedText() {
        val frozen = StreamFreezeSignal().apply { freeze() }
        val text = "AI answer that was committed just before Stop."
        val flow = MutableStateFlow<ActiveChatStreamState?>(ActiveChatStreamState(
            conversationId = "chat", messageId = "answer", generationId = 7L,
            revision = 1L, status = ChatStreamStatus.CANCELLED,
            content = StreamTextSnapshot.of(text), reasoning = StreamTextSnapshot.of(),
            stopSignal = frozen, lastCommitMetrics = null, cumulativeMetrics = StreamMetricsSnapshot()
        ))
        compose.setContent { MaterialTheme {
            AssistantMessageCard(
                message = StoredMessage("answer", ChatRole.MODEL, listOf(MessageVersion(content = text))),
                isStreaming = true, onCopy = {}, onRegenerate = {}, onEdit = {},
                onDelete = {}, onRead = {}, onSwitchVersion = {},
                conversationId = "chat", streamStateFlow = flow
            )
        } }
        compose.onNodeWithText(text).assertIsDisplayed()
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

    @Test fun liveThinkingStepShowsWhileClosedAndRetractsWhenAToolTakesOver() {
        var items by mutableStateOf<List<ProcessingDropdownItem>>(listOf(ProcessingDropdownItem.Thinking("Planning the work")))
        var live by mutableStateOf(true)
        compose.setContent { MaterialTheme {
            ProcessingDropdown(items = items, durationMillis = null, startNumber = 1, isStreaming = live)
        } }
        // The dropdown header and the running thinking step are visible without expanding.
        compose.onNodeWithText("Thinking...").assertIsDisplayed()
        compose.onNodeWithTag("live-thinking-preview").assertIsDisplayed()

        compose.runOnIdle {
            items = items + ProcessingDropdownItem.Tool(ToolActivity("tool", "search_documents"))
        }
        compose.onNodeWithText("search documents").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-thinking-preview").fetchSemanticsNodes().isEmpty() }

        compose.runOnIdle {
            items = listOf(items.first(), ProcessingDropdownItem.Tool(ToolActivity("tool", "search_documents", status = "success")))
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("search documents").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Generating...").assertIsDisplayed()

        compose.onNodeWithText("Generating...").performClick()
        compose.onNodeWithText("search documents").assertIsDisplayed()
    }

    @Test fun liveThinkingComesFromTheStreamAndRetractsOnceTheAnswerStarts() {
        val freezeSignal = StreamFreezeSignal()
        fun stream(reasoning: String, answer: String, revision: Long) = ActiveChatStreamState(
            conversationId = "conversation",
            messageId = "assistant",
            generationId = 1L,
            revision = revision,
            status = ChatStreamStatus.STREAMING,
            content = StreamTextSnapshot.of(answer),
            reasoning = StreamTextSnapshot.of(reasoning),
            stopSignal = freezeSignal,
            lastCommitMetrics = null,
            cumulativeMetrics = StreamMetricsSnapshot()
        )
        val streamFlow = MutableStateFlow<ActiveChatStreamState?>(stream("", "", 1))
        // The stored version has no reasoning or content yet: only the stream has them.
        val stored = MessageVersion()
        compose.setContent { MaterialTheme {
            AssistantMessageCard(
                StoredMessage("assistant", ChatRole.MODEL, listOf(stored)),
                true, {}, {}, {}, {}, {}, {}, onSwitchVersion = {},
                conversationId = "conversation", streamStateFlow = streamFlow
            )
        } }
        compose.onAllNodesWithTag("live-thinking-preview").assertCountEquals(0)

        compose.runOnIdle { streamFlow.value = stream("Considering options", "", 2) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-thinking-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("live-thinking-preview").assertIsDisplayed()

        compose.runOnIdle { streamFlow.value = stream("Considering options", "Here is the answer.", 3) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-thinking-preview").fetchSemanticsNodes().isEmpty() }
    }

}
