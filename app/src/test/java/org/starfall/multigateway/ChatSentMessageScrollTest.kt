package org.starfall.multigateway

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.multigateway.home.chat.ui.ChatRenderCaches
import org.starfall.multigateway.home.chat.ui.ChatScreen
import org.starfall.multigateway.home.chat.ui.OPENING_VEIL_TAG
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage

/**
 * A pending message that starts after the previous response must land in the middle of the chat
 * area, stay there while its reply arrives and ends, and only reach the app bar by the user's drag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class ChatSentMessageScrollTest {
    @get:Rule val compose = createComposeRule()

    @Before fun coldStart() = ChatRenderCaches.clearAll()

    private class Env {
        val conversation = mutableStateOf<Conversation?>(null)
        val queued = mutableStateOf<List<StoredMessage>>(emptyList())
        val generating = mutableStateOf(false)
    }

    private fun message(id: String, role: ChatRole, text: String) =
        StoredMessage(id, role, listOf(MessageVersion(content = text)))

    private fun history(turns: Int) = (1..turns).flatMap { i ->
        listOf(
            message("u$i", ChatRole.USER, "Question $i"),
            message("m$i", ChatRole.MODEL, (1..6).joinToString("\n\n") { "Answer $i paragraph $it." })
        )
    }

    private fun conversation(messages: List<StoredMessage>) =
        Conversation(id = "chat", title = "chat", createdAt = 0, updatedAt = 0, messages = messages)

    private fun show(env: Env, autoScroll: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    conversation = env.conversation.value, isGenerating = env.generating.value,
                    generatingConversationId = env.conversation.value?.id, chatError = null,
                    providers = emptyList(), selectedProviderId = "", selectedModelName = "",
                    onNewChat = {}, onSendMessage = { _, _ -> false }, onSendMedia = { false },
                    onStopGenerating = {}, onOpenDrawer = {}, onOpenSettings = {},
                    onRegenerate = {}, onEditMessage = { _, _, _ -> false },
                    onDeleteMessage = {}, onDeleteMessageVersion = {},
                    onSwitchVersion = { _, _ -> }, onSelectModel = { _, _ -> },
                    summaryProgress = null, onSetReasoningEffort = {},
                    onStartConversationSummary = { false }, onSummaryRoleChange = {},
                    onDeleteSummary = {}, onReadMessage = { _, _ -> },
                    queuedMessages = env.queued.value, autoScroll = autoScroll
                )
            }
        }
    }

    private fun veilNodes() = compose.onAllNodesWithTag(OPENING_VEIL_TAG).fetchSemanticsNodes()

    /**
     * Steps the frame clock by hand. While a response streams, the follow-the-stream loop awaits
     * a frame forever, so the app is never "idle" and waitForIdle() would time out.
     */
    private fun frames(count: Int = 40) = repeat(count) {
        compose.mainClock.advanceTimeByFrame()
        Thread.sleep(4) // markdown parsing runs on real background threads
    }

    private fun openChat(env: Env, messages: List<StoredMessage>) {
        env.conversation.value = conversation(messages)
        compose.mainClock.autoAdvance = false
        show(env)
        var guard = 0
        while (veilNodes().isNotEmpty() && guard++ < 800) frames(1)
        assertTrue("the chat never finished opening", veilNodes().isEmpty())
        frames()
    }

    private fun update(change: () -> Unit) {
        compose.runOnIdle { change() }
        frames()
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.top

    private fun rootHeight() = compose.onRoot().fetchSemanticsNode().boundsInRoot.height

    private fun inputTop() = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.top

    /** The message is in the middle band of the chat area, with its reply space below. */
    private fun assertInTheMiddle(text: String) {
        val top = top(text)
        val h = rootHeight()
        assertTrue("\"$text\" must sit around the middle, was at $top of $h", top in (0.36f * h)..(0.62f * h))
        assertTrue("room below for the reply", top < inputTop() - 0.2f * h)
    }

    private fun sendWhilePending(env: Env, sentId: String, queuedId: String = sentId) {
        // The previous answer is still streaming and the message waits in the queue.
        update {
            env.generating.value = true
            env.queued.value = listOf(message(queuedId, ChatRole.USER, "Pending question").copy(isQueued = true))
        }
        // The answer ends, the queue drains, the message is sent and its reply starts.
        update {
            env.queued.value = emptyList()
            env.conversation.value = conversation(
                env.conversation.value!!.messages +
                    message(sentId, ChatRole.USER, "Pending question") + message("reply", ChatRole.MODEL, "")
            )
        }
        frames()
    }

    @Test fun pendingMessageSentWithANewIdIsPlacedInTheMiddle() {
        val env = Env()
        openChat(env, history(10))
        sendWhilePending(env, sentId = "sent", queuedId = "queued-before-drain")
        assertInTheMiddle("Pending question")
    }

    @Test fun pendingMessageHandedOverWithItsQueuedIdIsPlacedInTheMiddle() {
        val env = Env()
        openChat(env, history(10))
        sendWhilePending(env, sentId = "same-id")
        assertInTheMiddle("Pending question")
    }

    @Test fun aShortReplyStartingAndFinishingNeverMovesTheMessage() {
        val env = Env()
        openChat(env, history(10))
        sendWhilePending(env, sentId = "sent")
        val placed = top("Pending question")

        val sent = env.conversation.value!!.messages
        update { env.conversation.value = conversation(sent.dropLast(1) + message("reply", ChatRole.MODEL, "Short reply.")) }
        assertEquals("reply text arriving must not scroll", placed, top("Pending question"), 2f)

        update { env.generating.value = false }
        assertEquals("the end of the response must not scroll", placed, top("Pending question"), 2f)
        assertInTheMiddle("Pending question")
    }

    @Test fun draggingUpIsTheOnlyWayToReachTheAppBar() {
        val env = Env()
        openChat(env, history(10))
        sendWhilePending(env, sentId = "sent")
        val placed = top("Pending question")

        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.05f, durationMillis = 200) }
        frames()
        val dragged = top("Pending question")
        assertTrue("dragging up moves it above the middle: $placed -> $dragged", dragged < placed - 50f)
        assertTrue("it can reach the app bar: $dragged of ${rootHeight()}", dragged < 0.2f * rootHeight())
    }

    @Test fun aLongReplyIsFollowedWithoutLosingTheEndOfIt() {
        val env = Env()
        openChat(env, history(10))
        sendWhilePending(env, sentId = "sent")

        val long = (1..80).joinToString("\n\n") { "Streamed paragraph $it." }
        val sent = env.conversation.value!!.messages
        update { env.conversation.value = conversation(sent.dropLast(1) + message("reply", ChatRole.MODEL, long)) }
        frames(120)
        update { env.generating.value = false }
        frames(60)

        compose.onNodeWithText("Streamed paragraph 80.", useUnmergedTree = true).assertIsDisplayed()
    }
}
