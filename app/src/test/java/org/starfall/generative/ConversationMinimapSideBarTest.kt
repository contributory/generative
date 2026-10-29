package org.starfall.generative

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.ui.ChatMessageList
import org.starfall.generative.features.home.chat.ui.ChatMessageListState
import org.starfall.generative.features.home.chat.ui.ConversationMinimapSideBar
import org.starfall.generative.features.home.chat.ui.ConversationMinimapSideBarState
import org.starfall.generative.features.home.chat.ui.minimapEdgeSwipe
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi", application = LibraryTestApplication::class)
class ConversationMinimapSideBarTest {
    @get:Rule val compose = createComposeRule()

    private fun turns(count: Int) = (0 until count).map {
        StoredMessage("m$it", if (it % 2 == 0) ChatRole.USER else ChatRole.MODEL,
            listOf(MessageVersion(content = "Message $it\n".repeat(20))))
    }

    @Test fun scrollingChatNeverOpensMinimap() {
        val list = LazyListState()
        val minimap = ConversationMinimapSideBarState()
        val messages = turns(20)
        lateinit var scope: CoroutineScope
        compose.setContent { MaterialTheme {
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { Text(it.content, Modifier.fillMaxWidth().height(400.dp)) }
                }
                ConversationMinimapSideBar("chat", messages, list, 0, 0, {}, minimap)
            }
        } }
        compose.onNodeWithTag("chat-minimap-thumb").assertDoesNotExist()
        assertFalse(minimap.isOpen)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scope.launch { list.animateScrollBy(3000f) } }
        compose.mainClock.advanceTimeBy(100)
        assertTrue("The chat must scroll normally", list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
        assertFalse("Scrolling must not open the minimap", minimap.isOpen)
        compose.onNodeWithTag("chat-minimap-thumb").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(5_000)
        assertFalse(minimap.isOpen)
        compose.onNodeWithTag("chat-minimap-thumb").assertDoesNotExist()
    }

    @Test fun onlyInwardSwipeStartingAtRightEdgeOpensMinimap() {
        val list = LazyListState()
        val minimap = ConversationMinimapSideBarState()
        val messages = turns(20)
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().minimapEdgeSwipe(minimap).testTag("minimap-gesture-host")) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { Text(it.content, Modifier.fillMaxWidth().height(400.dp)) }
                }
                ConversationMinimapSideBar("chat", messages, list, 0, 0, {}, minimap)
            }
        } }
        val host = compose.onNodeWithTag("minimap-gesture-host")
        host.performTouchInput { click(Offset(width - 8.dp.toPx(), height * 0.4f)) }
        assertFalse("A right-edge tap must not open the minimap", minimap.isOpen)
        host.performTouchInput {
            swipe(Offset(width - 8.dp.toPx(), height * 0.7f),
                Offset(width - 8.dp.toPx(), height * 0.35f), 400)
        }
        assertFalse("Vertical scrolling at the right edge must not open it", minimap.isOpen)
        host.performTouchInput {
            swipe(Offset(width * 0.6f, height * 0.4f),
                Offset(width * 0.2f, height * 0.4f), 300)
        }
        assertFalse("Horizontal swipes away from the edge must not open it", minimap.isOpen)
        host.performTouchInput {
            swipe(Offset(width - 80.dp.toPx(), height * 0.4f),
                Offset(width - 8.dp.toPx(), height * 0.4f), 300)
        }
        assertFalse("Outward swipes ending at the right edge must not open it", minimap.isOpen)
        host.performTouchInput {
            swipe(Offset(width - 8.dp.toPx(), height * 0.4f),
                Offset(width - 160.dp.toPx(), height * 0.4f), 300)
        }
        assertTrue("Only the inward right-edge swipe opens the minimap", minimap.isOpen)
        compose.onNodeWithTag("chat-minimap-thumb").assertExists()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_000)
        assertFalse("The minimap closes after interaction becomes idle", minimap.isOpen)
    }

    @Test fun draggingTheThumbScrollsTheListAndReportsDirectionChanges() {
        val list = LazyListState()
        val minimap = ConversationMinimapSideBarState()
        val messages = turns(20)
        val scrubs = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { Text(it.content, Modifier.fillMaxWidth().height(400.dp)) }
                }
                ConversationMinimapSideBar("chat", messages, list, 0, 0, { scrubs.add(it) }, minimap)
            }
        } }
        compose.runOnIdle { minimap.isOpen = true }
        compose.onNodeWithTag("chat-minimap-thumb").performTouchInput {
            swipe(center, Offset(center.x, center.y + 3000f), 700)
        }
        compose.waitForIdle()
        assertTrue("Dragging the thumb to the bottom reaches the newest turns", list.firstVisibleItemIndex >= 17)
        assertEquals(listOf(true), scrubs)
        compose.runOnIdle { minimap.isOpen = true }
        compose.onNodeWithTag("chat-minimap-thumb").performTouchInput {
            swipe(center, Offset(center.x, center.y - 3000f), 700)
        }
        compose.waitForIdle()
        assertEquals("Dragging back to the top reaches the oldest turn", 0, list.firstVisibleItemIndex)
        assertEquals(listOf(true, false), scrubs)
    }

    @Test fun onlyTheThumbTakesTouchesSoHorizontalSwipesAndTapsPassThrough() {
        val list = LazyListState()
        val minimap = ConversationMinimapSideBarState()
        val messages = turns(20)
        var dragged = 0f
        var tapped = false
        var scrubbed = false
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().draggable(rememberDraggableState { dragged += it }, Orientation.Horizontal)) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { Text(it.content, Modifier.fillMaxWidth().height(400.dp)) }
                }
                Button(onClick = { tapped = true },
                    modifier = Modifier.align(Alignment.BottomEnd).testTag("under-rail")) { Text("Action") }
                ConversationMinimapSideBar("chat", messages, list, 0, 0, { scrubbed = true }, minimap)
            }
        } }
        compose.runOnIdle { minimap.isOpen = true }
        // The thumb sits at the top; a button further down the same edge stays fully usable.
        compose.onNodeWithTag("under-rail").performTouchInput { click() }
        assertTrue("Taps away from the thumb reach what is underneath", tapped)
        compose.onNodeWithTag("chat-minimap-thumb").performTouchInput {
            swipe(center, Offset(center.x - 300f, center.y), 300)
        }
        compose.waitForIdle()
        assertTrue("A horizontal swipe that starts on the thumb is not claimed", dragged < 0f)
        assertFalse(scrubbed)
        assertEquals(0, list.firstVisibleItemIndex)
    }

    @Test fun holdingTheThumbPastTheIdleTimeoutKeepsItVisible() {
        val list = LazyListState()
        val minimap = ConversationMinimapSideBarState()
        val messages = turns(20)
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { Text(it.content, Modifier.fillMaxWidth().height(400.dp)) }
                }
                ConversationMinimapSideBar("chat", messages, list, 0, 0, {}, minimap)
            }
        } }
        compose.runOnIdle { minimap.isOpen = true }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("chat-minimap-thumb").performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f), delayMillis = 50)
        }
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue(minimap.isInteracting)
        assertTrue(minimap.isOpen)
        compose.onNodeWithTag("chat-minimap-thumb").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(3_000)
        assertFalse(minimap.isOpen)
    }

    @Test fun emptyConversationNeverShowsAScrollbar() {
        val minimap = ConversationMinimapSideBarState()
        compose.setContent { MaterialTheme {
            ConversationMinimapSideBar("empty", emptyList(), LazyListState(), 0, 0, {}, minimap)
        } }
        compose.runOnIdle { minimap.isOpen = true }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-minimap-thumb").assertDoesNotExist()
    }

    @Test fun changingConversationsHidesThePreviousScrollbar() {
        var conversation by mutableStateOf("first")
        val list = LazyListState()
        val messages = turns(3)
        val minimap = ConversationMinimapSideBarState()
        compose.setContent { MaterialTheme {
            ConversationMinimapSideBar(conversation, messages, list, 0, 0, {}, minimap)
        } }
        compose.runOnIdle { minimap.isOpen = true }
        compose.onNodeWithTag("chat-minimap-thumb").assertExists()
        compose.runOnIdle { conversation = "second" }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-minimap-thumb").assertDoesNotExist()
    }

    @Test fun scrollbarStaysBetweenAppBarAndTheResizingComposer() {
        var inputHeight by mutableStateOf(140.dp)
        val minimap = ConversationMinimapSideBarState()
        val messages = turns(3)
        compose.setContent { MaterialTheme {
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(96.dp).testTag("minimap-test-appbar"))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(inputHeight)
                    .testTag("minimap-test-input"))
                ConversationMinimapSideBar(
                    "chat", messages, remember { LazyListState() },
                    with(density) { 104.dp.roundToPx() },
                    with(density) { (inputHeight + 8.dp).roundToPx() },
                    {}, minimap
                )
            }
        } }
        compose.runOnIdle { minimap.isOpen = true }
        fun assertInsideGap() {
            val bounds = compose.onNodeWithTag("chat-minimap-thumb").getUnclippedBoundsInRoot()
            val appBar = compose.onNodeWithTag("minimap-test-appbar").getUnclippedBoundsInRoot()
            val input = compose.onNodeWithTag("minimap-test-input").getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= appBar.bottom + 8.dp)
            assertTrue(bounds.bottom <= input.top - 8.dp)
        }
        assertInsideGap()
        compose.runOnIdle { inputHeight = 360.dp }
        assertInsideGap()
    }

    @Test fun realTurnListScrubStaysStableAcrossLargeContentGrowth() {
        var messages by mutableStateOf((0..2).flatMap { i -> listOf(
            StoredMessage("u$i", ChatRole.USER, listOf(MessageVersion(content = "Question $i"))),
            StoredMessage("a$i", ChatRole.MODEL, listOf(MessageVersion(content = "Answer $i")))) })
        val viewport = ChatMessageListState(messages)
        val minimap = ConversationMinimapSideBarState()
        var oldReplyHeight by mutableStateOf(300.dp)
        compose.setContent { MaterialTheme {
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize()) {
                ChatMessageList(viewport, messages, false, false, false, null, 64.dp, 120.dp) { message ->
                    Text(message.content, Modifier.fillMaxWidth().height(
                        if (message.id == "a0") oldReplyHeight else if (message.role == ChatRole.USER) 60.dp else 1200.dp)
                        .testTag("minimap-message-${message.id}"))
                }
                ConversationMinimapSideBar("chat", messages, viewport.listState,
                    with(density) { 64.dp.roundToPx() }, with(density) { 120.dp.roundToPx() },
                    { viewport.manualScroll(it) }, minimap)
            }
        } }
        fun drag(toTop: Boolean) {
            compose.runOnIdle { minimap.isOpen = true }
            compose.onNodeWithTag("chat-minimap-thumb").performTouchInput {
                swipe(center, Offset(center.x, center.y + if (toTop) -3000f else 3000f), 700)
            }
            compose.waitForIdle()
        }
        drag(toTop = true)
        assertEquals(0, viewport.listState.firstVisibleItemIndex)
        assertTrue("Scrubbing is manual scrolling and stops following", viewport.detached)
        compose.runOnIdle {
            oldReplyHeight = 6000.dp
            messages = messages.map { if (it.id == "a0") it.copy(
                versions = listOf(MessageVersion(content = "A much longer streamed answer.\n".repeat(500)))) else it }
        }
        drag(toTop = false)
        compose.onNodeWithTag("chat-content-end-a2").assertIsDisplayed()
    }
}
