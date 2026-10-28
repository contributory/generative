package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.features.home.chat.ui.ChatRenderCaches
import org.starfall.generative.features.home.chat.ui.ChatScreen
import org.starfall.generative.features.home.chat.ui.OPENING_VEIL_TAG

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class ChatOpeningScrollTest {
    @get:Rule val compose = createComposeRule()

    // The caches live for the whole process; every test starts from a cold app.
    @Before fun coldStart() = ChatRenderCaches.clearAll()

    private fun veilNodes() = compose.onAllNodesWithTag(OPENING_VEIL_TAG).fetchSemanticsNodes()

    /**
     * Switches chats and steps through the frames one at a time, until the new chat's text shows up.
     * Returns whether the veil was already up by then: a chat must never appear unveiled while it is
     * still settling, and a chat that is fully cached must not be veiled at all.
     */
    private fun veilWasUpWhenTextAppeared(
        current: androidx.compose.runtime.MutableState<Conversation?>,
        to: Conversation,
        endText: String
    ): Boolean {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { current.value = to }
        var veilSeen = false
        repeat(400) {
            compose.mainClock.advanceTimeByFrame()
            veilSeen = veilSeen || veilNodes().isNotEmpty()
            if (compose.onAllNodesWithText(endText, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                return veilSeen
            }
            Thread.sleep(5) // parsing runs on real background threads
        }
        error("The chat never showed \"$endText\"")
    }

    private fun conversation(id: String) = Conversation(
        id = id, title = id, createdAt = 0, updatedAt = 0,
        messages = listOf(StoredMessage(
            id = "$id-answer", role = ChatRole.MODEL,
            versions = listOf(MessageVersion(content =
                (1..50).joinToString("\n\n") { "Paragraph $it of the saved answer." } +
                    "\n\nEnd of $id"
            ))
        ))
    )

    private fun show(
        current: androidx.compose.runtime.State<Conversation?>,
        loadingId: androidx.compose.runtime.State<String?> = mutableStateOf(null)
    ) {
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    conversation = current.value, isGenerating = false,
                    loadingConversationId = loadingId.value,
                    generatingConversationId = null, chatError = null,
                    providers = emptyList(), selectedProviderId = "", selectedModelName = "",
                    onNewChat = {}, onSendMessage = { _, _ -> false }, onSendMedia = { false },
                    onStopGenerating = {}, onOpenDrawer = {}, onOpenSettings = {},
                    onRegenerate = {}, onEditMessage = { _, _, _ -> false },
                    onDeleteMessage = {}, onDeleteMessageVersion = {},
                    onSwitchVersion = { _, _ -> }, onSelectModel = { _, _ -> },
                    summaryProgress = null, onSetReasoningEffort = {},
                    onStartConversationSummary = { false }, onSummaryRoleChange = {},
                    onDeleteSummary = {}, onReadMessage = { _, _ -> }, autoScroll = false
                )
            }
        }
    }

    private fun assertEndVisible(id: String) {
        // The chat stays behind the veil until it is fully loaded and at the latest message.
        compose.waitUntil(10_000) { veilNodes().isEmpty() }
        // Parsing runs off-main; allow it to finish before checking the settled layout.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("End of $id", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val end = compose.onNodeWithText("End of $id", useUnmergedTree = true)
        end.assertIsDisplayed()
        val inputTop = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.top
        assertTrue("The end of the answer must be above the input", end.fetchSemanticsNode().boundsInRoot.bottom <= inputTop)
    }

    @Test fun reopeningAndSwitchingChatsReachesTheEndAfterMarkdownLoads() {
        val current = mutableStateOf<Conversation?>(conversation("first"))
        show(current)
        assertEndVisible("first")
        compose.runOnIdle { current.value = conversation("second") }
        assertEndVisible("second")
        compose.runOnIdle { current.value = conversation("first") }
        assertEndVisible("first")
    }

    @Test fun firstOpenIsVeiledUntilSettledThenReopeningIsInstant() {
        val current = mutableStateOf<Conversation?>(conversation("first"))
        compose.mainClock.autoAdvance = false
        show(current)
        assertTrue("A cold first open waits behind the veil", veilNodes().isNotEmpty())
        compose.mainClock.autoAdvance = true
        assertEndVisible("first")

        compose.runOnIdle { current.value = conversation("second") }
        assertEndVisible("second")

        assertFalse(
            "A settled chat reopens with its text already in place and no veil",
            veilWasUpWhenTextAppeared(current, conversation("first"), "End of first")
        )
        compose.mainClock.autoAdvance = true
        assertEndVisible("first")
    }

    @Test fun clearingTheCachesMakesTheNextOpenWaitAgain() {
        val current = mutableStateOf<Conversation?>(conversation("first"))
        show(current)
        assertEndVisible("first")
        compose.runOnIdle { current.value = conversation("second") }
        assertEndVisible("second")

        // Memory pressure empties the low-priority caches.
        ChatRenderCaches.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertTrue(
            "Without the cache the chat is veiled again until it has settled",
            veilWasUpWhenTextAppeared(current, conversation("first"), "End of first")
        )
        compose.mainClock.autoAdvance = true
        assertEndVisible("first")
    }

    @Test fun delayedMessagesOpenAtTheEndAndManualScrollingKeepsControl() {
        val saved = conversation("delayed")
        val current = mutableStateOf<Conversation?>(saved.copy(messages = emptyList()))
        show(current)
        compose.runOnIdle { current.value = saved }
        assertEndVisible("delayed")
        val list = compose.onNode(hasScrollAction())
        fun position() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val bottom = position()
        list.performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertTrue("Opening must not pull the user back to the bottom", position() < bottom)
        compose.onNodeWithContentDescription("Jump to latest").assertIsDisplayed()
    }

    @Test fun pendingHistoryReadIsVeiledEvenBeforeConversationOrMessagesExist() {
        val current = mutableStateOf<Conversation?>(null)
        val loadingId = mutableStateOf<String?>("disk-history")
        show(current, loadingId)
        assertTrue("A pending disk read must show the veil with no messages yet", veilNodes().isNotEmpty())
        // A veil over the chat list must not swallow input-area interaction.
        compose.onNode(hasSetTextAction()).assertIsEnabled()
        compose.onNode(hasSetTextAction()).performTouchInput { click() }
        compose.onNode(hasSetTextAction()).performTextInput("input remains usable")
        compose.onNode(hasSetTextAction()).assertTextContains("input remains usable")

        compose.runOnIdle {
            current.value = conversation("disk-history")
            loadingId.value = null
        }
        assertEndVisible("disk-history")
    }

    @Test fun historyVeilHasNoDeadlineWhileSelectionIsStillLoading() {
        val current = mutableStateOf<Conversation?>(conversation("slow-history"))
        val loadingId = mutableStateOf<String?>("still-reading")
        compose.mainClock.autoAdvance = false
        show(current, loadingId)
        assertTrue(veilNodes().isNotEmpty())
        // Go well beyond the removed four-second deadline while a history load remains pending.
        repeat(350) { compose.mainClock.advanceTimeByFrame() }
        assertTrue("The history veil must not time out", veilNodes().isNotEmpty())
        compose.runOnIdle { loadingId.value = null }
        compose.mainClock.autoAdvance = true
        assertEndVisible("slow-history")
    }

}
