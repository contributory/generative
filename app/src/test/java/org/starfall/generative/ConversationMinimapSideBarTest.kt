package org.starfall.generative

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.ui.ConversationMinimapSideBar
import org.starfall.generative.features.home.chat.ui.ConversationMinimapSideBarState
import org.starfall.generative.features.home.chat.ui.dismissMinimapOnOutsideTap
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi", application = LibraryTestApplication::class)
class ConversationMinimapSideBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun edgeSwipeOpensTapNavigatesAndOutsideTapOrRightSwipeCloses() {
        val list = LazyListState()
        val drawer = DrawerState(DrawerValue.Closed)
        var navigated = false
        var outsideClicked = false
        val minimap = ConversationMinimapSideBarState()
        val messages = (0..19).map {
            StoredMessage("m$it", if (it % 2 == 0) ChatRole.USER else ChatRole.MODEL,
                listOf(MessageVersion(content = "Message $it\n".repeat(20))))
        }
        compose.setContent { MaterialTheme {
            ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                ModalDrawerSheet { Text("Left drawer") }
            }) {
                Box(Modifier.fillMaxSize().dismissMinimapOnOutsideTap(minimap)) {
                    LazyColumn(state = list, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                        items(messages.asReversed(), key = { it.id }) {
                            Text(it.content, Modifier.fillMaxWidth().height(400.dp))
                        }
                    }
                    Button(onClick = { outsideClicked = true }, modifier = Modifier.testTag("outside-action")) {
                        Text("Outside action")
                    }
                    ConversationMinimapSideBar("chat", messages, list, 0, 0, {}, { navigated = true }, minimap)
                }
            }
        } }
        compose.onNodeWithTag("chat-minimap-edge").performTouchInput {
            swipe(Offset(width - 2f, height / 2f), Offset(-160f, height / 2f), 300)
        }
        compose.onNodeWithTag("chat-minimap-sidebar").assertIsDisplayed()
            .assertWidthIsEqualTo(44.dp)
        val railBounds = compose.onNodeWithTag("chat-minimap-sidebar").getUnclippedBoundsInRoot()
        assertTrue(railBounds.bottom - railBounds.top <= 480.dp)
        compose.onNodeWithTag("chat-minimap-dismiss").assertDoesNotExist()
        assertEquals(DrawerValue.Closed, drawer.currentValue)
        compose.onNodeWithTag("chat-minimap-map").performTouchInput {
            click(Offset(width / 2f, height * 0.02f))
        }
        compose.waitForIdle()
        assertTrue(navigated)
        assertTrue("The oldest messages are at the high indices of the reverse list", list.firstVisibleItemIndex >= 18)
        compose.onNodeWithTag("outside-action").performTouchInput { click() }
        assertTrue("Outside controls must remain usable while the sidebar is open", outsideClicked)
        compose.onNodeWithTag("chat-minimap-sidebar").assertDoesNotExist()

        compose.onNodeWithTag("chat-minimap-edge").performClick()
        compose.onNodeWithTag("chat-minimap-sidebar").performTouchInput {
            swipe(Offset(5f, height / 2f), Offset(width - 2f, height / 2f), 300)
        }
        compose.onNodeWithTag("chat-minimap-sidebar").assertDoesNotExist()
        assertEquals(DrawerValue.Closed, drawer.currentValue)
    }

    @Test fun emptyConversationDoesNotExposeNavigator() {
        compose.setContent { MaterialTheme {
            ConversationMinimapSideBar("empty", emptyList(), LazyListState(), 0, 0, {}, {}, remember { ConversationMinimapSideBarState() })
        } }
        compose.onNodeWithTag("chat-minimap-edge").assertDoesNotExist()
        compose.onNodeWithTag("chat-minimap-sidebar").assertDoesNotExist()
    }

    @Test fun changingConversationsClosesPreviousMinimap() {
        var conversation by mutableStateOf("first")
        val list = LazyListState()
        val messages = listOf(StoredMessage("message", ChatRole.USER, listOf(MessageVersion(content = "Hello"))))
        compose.setContent { MaterialTheme {
            val minimap = remember(conversation) { ConversationMinimapSideBarState() }
            ConversationMinimapSideBar(conversation, messages, list, 0, 0, {}, {}, minimap)
        } }
        compose.onNodeWithTag("chat-minimap-edge").performClick()
        compose.onNodeWithTag("chat-minimap-sidebar").assertIsDisplayed()
        compose.runOnIdle { conversation = "second" }
        compose.onNodeWithTag("chat-minimap-sidebar").assertDoesNotExist()
        compose.onNodeWithTag("chat-minimap-edge").assertIsDisplayed()
    }


    @Test fun railAndEdgeStayBetweenAppBarAndResizingInputPanel() {
        var inputHeight by mutableStateOf(140.dp)
        val state = ConversationMinimapSideBarState()
        val messages = listOf(StoredMessage("message", ChatRole.USER, listOf(MessageVersion(content = "Hello"))))
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
                    {}, {}, state
                )
            }
        } }
        fun assertInsideGap(tag: String) {
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val appBar = compose.onNodeWithTag("minimap-test-appbar").getUnclippedBoundsInRoot()
            val input = compose.onNodeWithTag("minimap-test-input").getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= appBar.bottom + 8.dp)
            assertTrue(bounds.bottom <= input.top - 8.dp)
        }
        assertInsideGap("chat-minimap-edge")
        compose.onNodeWithTag("chat-minimap-edge").performClick()
        assertInsideGap("chat-minimap-sidebar")
        compose.runOnIdle { inputHeight = 360.dp }
        assertInsideGap("chat-minimap-sidebar")
    }

}
