package org.starfall.generative

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.navigation.AppDestination
import org.starfall.generative.navigation.CHAT_PAGE
import org.starfall.generative.navigation.MENU_PAGE
import org.starfall.generative.navigation.homePagerSwipeEnabled
import org.starfall.generative.navigation.homePagerGestureRouting
import org.starfall.generative.features.home.chat.ui.ConversationMinimapSideBar
import org.starfall.generative.features.home.chat.ui.ConversationMinimapSideBarState
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage

/** Exercises the native Pager gestures alongside the real Material 3 drawer mechanism. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w480dp-h960dp-xhdpi")
class HomePagerGestureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun minimapEdgeSwipeWinsAgainstPagerWithoutExpandingHitTarget() {
        checkRightEdgeRouting(hasMessages = true)
    }

    @Test
    fun emptyChatEdgeBlocksPagerAndStillAllowsDrawer() {
        checkRightEdgeRouting(hasMessages = false)
    }

    private fun checkRightEdgeRouting(hasMessages: Boolean) {
        val minimap = ConversationMinimapSideBarState()
        val messages = if (hasMessages) listOf(StoredMessage("m", ChatRole.USER, listOf(MessageVersion(content = "Hello")))) else emptyList()
        lateinit var pager: PagerState
        lateinit var drawer: DrawerState
        compose.setContent { MaterialTheme {
            pager = rememberPagerState(initialPage = CHAT_PAGE, pageCount = { 2 })
            drawer = rememberDrawerState(initialValue = DrawerValue.Closed)
            val barBottom = with(LocalDensity.current) { 80.dp.roundToPx() }
            val drawerEdge = with(LocalDensity.current) { 32.dp.roundToPx() }
            val listState = androidx.compose.runtime.remember { LazyListState() }
            var pagerEnabled by androidx.compose.runtime.remember { mutableStateOf(true) }
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = pager.currentPage == CHAT_PAGE,
                drawerContent = { ModalDrawerSheet { Text("Conversations") } }
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().homePagerGestureRouting(
                        pager.currentPage == CHAT_PAGE, barBottom, drawerEdge
                    ) { pagerEnabled = it },
                    userScrollEnabled = pagerEnabled
                ) { page ->
                    if (page == CHAT_PAGE) {
                        Box(Modifier.fillMaxSize().testTag("edge-test-chat")) {
                            ConversationMinimapSideBar(
                                "chat", messages, listState, barBottom, barBottom,
                                {}, {}, minimap
                            )
                        }
                    } else Box(Modifier.fillMaxSize().testTag("menu-root"))
                }
            }
        } }
        compose.onNodeWithTag("chat-minimap-edge").performTouchInput {
            swipe(Offset(width - 2f, height / 2f), Offset(-140f, height / 2f), 300)
        }
        compose.waitForIdle()
        assertEquals("Only a nonempty chat opens Minimap", hasMessages, minimap.isOpen)
        assertEquals("Minimap swipe must not switch Pager", CHAT_PAGE, pager.settledPage)
        assertEquals("Minimap swipe must not open SideDrawer", DrawerValue.Closed, drawer.currentValue)

        if (!hasMessages) {
            compose.onNodeWithTag("edge-test-chat").performTouchInput {
                swipe(Offset(1f, height / 2f), Offset(width * 0.8f, height / 2f), 300)
            }
            compose.waitForIdle()
            assertTrue("Empty chat must still allow swiping open the SideDrawer", drawer.isOpen)
            assertEquals(CHAT_PAGE, pager.settledPage)
        }
    }

    @Test
    fun chatBodyOpensDrawerAndOnlyAppbarSwipesToMenu() {
        var menuRoute by mutableStateOf(AppDestination.FEATURES.route)
        var pagerGestureAllowed by mutableStateOf(true)
        lateinit var pager: PagerState
        lateinit var drawer: DrawerState
        lateinit var scope: CoroutineScope
        compose.setContent {
            pager = rememberPagerState(initialPage = CHAT_PAGE, pageCount = { 2 })
            drawer = rememberDrawerState(initialValue = DrawerValue.Closed)
            scope = rememberCoroutineScope()
            val barHeightPx = with(LocalDensity.current) { 80.dp.roundToPx() }
            val drawerEdgePx = with(LocalDensity.current) { 32.dp.roundToPx() }
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = pager.currentPage == CHAT_PAGE,
                drawerContent = { ModalDrawerSheet { Text("Conversations") } }
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().homePagerGestureRouting(
                        pager.currentPage == CHAT_PAGE, barHeightPx, drawerEdgePx
                    ) { pagerGestureAllowed = it },
                    beyondViewportPageCount = 1,
                    userScrollEnabled = homePagerSwipeEnabled(pager.settledPage, menuRoute) && pagerGestureAllowed
                ) { page ->
                    if (page == CHAT_PAGE) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().testTag("chat-body"))
                            Box(
                                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(80.dp)
                                    .testTag("chat-appbar")
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().testTag("menu-root"))
                    }
                }
            }
        }

        compose.onNodeWithTag("chat-body").performTouchInput {
            swipe(
                start = Offset(1f, size.height * 0.5f),
                end = Offset(size.width * 0.8f, size.height * 0.5f),
                durationMillis = 300
            )
        }
        compose.waitForIdle()
        assertTrue("Chat edge swipe should open the SideDrawer", drawer.isOpen)
        assertEquals(CHAT_PAGE, pager.settledPage)

        compose.runOnIdle { scope.launch { drawer.close() } }
        compose.waitForIdle()

        compose.onNodeWithTag("chat-body").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("Chat body must not switch pages", CHAT_PAGE, pager.settledPage)

        // Changing direction within ONE continuous body gesture must not bypass the lock.
        compose.onNodeWithTag("chat-body").performTouchInput {
            val y = size.height * 0.5f
            swipe(curve = { t ->
                val phase = t.toFloat() / 400f
                val x = if (phase < 0.5f) size.width * (0.5f + phase * 0.8f)
                        else size.width * (0.9f - (phase - 0.5f) * 1.6f)
                Offset(x, y)
            }, durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals("Reversing a Chat-body drag must not open Menu", CHAT_PAGE, pager.settledPage)

        compose.runOnIdle { scope.launch { drawer.close() } }
        compose.waitForIdle()

        // SideDrawer must open from the AppBar edge too, not just from Chat content.
        compose.onNodeWithTag("chat-appbar").performTouchInput {
            swipe(
                start = Offset(1f, size.height * 0.5f),
                end = Offset(size.width * 0.8f, size.height * 0.5f),
                durationMillis = 300
            )
        }
        compose.waitForIdle()
        assertTrue("Chat AppBar edge swipe should open the SideDrawer", drawer.isOpen)
        assertEquals(CHAT_PAGE, pager.settledPage)

        compose.runOnIdle { scope.launch { drawer.close() } }
        compose.waitForIdle()

        // Right-first AppBar drags belong to Drawer until release, even after reversing.
        compose.onNodeWithTag("chat-appbar").performTouchInput {
            val y = size.height * 0.5f
            swipe(curve = { t ->
                val phase = t.toFloat() / 400f
                val x = if (phase < 0.5f) size.width * (0.5f + phase * 0.8f)
                        else size.width * (0.9f - (phase - 0.5f) * 1.6f)
                Offset(x, y)
            }, durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals("Reversing a right-first AppBar drag must not open Menu", CHAT_PAGE, pager.settledPage)
        compose.runOnIdle { scope.launch { drawer.close() } }
        compose.waitForIdle()

        compose.onNodeWithTag("chat-appbar").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("Chat AppBar must use the real HorizontalPager gesture", MENU_PAGE, pager.settledPage)

        menuRoute = AppDestination.PROVIDERS.route
        compose.waitForIdle()
        compose.onNodeWithTag("menu-root").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals("Nested Menu screen cannot swipe to Chat", MENU_PAGE, pager.settledPage)

        menuRoute = AppDestination.FEATURES.route
        compose.waitForIdle()
        compose.onNodeWithTag("menu-root").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals("Menu root should swipe to Chat", CHAT_PAGE, pager.settledPage)
    }
}
