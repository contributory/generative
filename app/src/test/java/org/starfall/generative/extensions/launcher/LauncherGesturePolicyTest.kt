package org.starfall.generative.extensions.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi")
class LauncherGesturePolicyTest {
    @get:Rule val compose = createComposeRule()
    private fun point(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center -
        compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft

    @Test fun clickableFolderStillTapsAndCanStartALongPressDrag() {
        val state = LauncherDragDropState<String>()
        var clicks = 0
        var ends = 0
        compose.setContent {
            LauncherDragDropHost(state, Modifier.fillMaxSize(), preview = {}) {
                Box(Modifier.size(100.dp).launcherDragSource(state, "folder", "folder", onDragEnd = { ends++ })
                    .clickable { clicks++ }.testTag("folder"))
            }
        }
        val start = point("folder")
        compose.onRoot().performTouchInput { click(start) }
        compose.runOnIdle { assertEquals(1, clicks); assertNull(state.session) }
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.runOnIdle { assertEquals("folder", state.session?.item?.key) }
        compose.onRoot().performTouchInput { moveTo(start + Offset(100f, 100f)); cancel() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, ends); assertEquals(1, clicks); assertNull(state.settlement) }
    }

    @Test fun handleStartsAfterSlopBeforeTheLazyContainerCanConsumeTheDrag() {
        val state = LauncherDragDropState<String>()
        var clicks = 0
        var scrollOffset = 0
        var ends = 0
        compose.setContent {
            val scroll = rememberScrollState()
            SideEffect { scrollOffset = scroll.value }
            val handleWidth = with(LocalDensity.current) { 30.dp.toPx() }
            LauncherDragDropHost(state, Modifier.fillMaxSize(), preview = {}, activation = LauncherDragActivation.AfterSlop) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Box(Modifier.fillMaxWidth().height(100.dp).launcherDragSource(state, "row", "row",
                        handleBounds = Rect(0f, 0f, handleWidth, Float.MAX_VALUE), onDragEnd = { ends++ })
                        .clickable { clicks++ }.testTag("row"))
                    Spacer(Modifier.height(2000.dp))
                }
            }
        }
        val row = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        val body = Offset(row.right - 20f, row.center.y) - origin
        compose.onRoot().performTouchInput { click(body) }
        compose.runOnIdle { assertEquals(1, clicks); assertNull(state.session) }
        val handle = Offset(row.left + 10f, row.center.y) - origin
        compose.onRoot().performTouchInput { down(handle); moveTo(handle + Offset(0f, 120f)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("row", state.session?.item?.key); assertEquals(0, scrollOffset) }
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, ends); assertEquals(1, clicks) }
    }

    @Test fun customLongPressTimeoutAndEarlyReleaseRespectClickOwnership() {
        val state = LauncherDragDropState<String>()
        var clicks = 0
        compose.setContent {
            LauncherDragDropHost(state, Modifier.fillMaxSize(), preview = {},
                activation = LauncherDragActivation.LongPress(200)) {
                Box(Modifier.size(100.dp).launcherDragSource(state, "item", "item")
                    .clickable { clicks++ }.testTag("item"))
            }
        }
        val start = point("item")
        compose.onRoot().performTouchInput { click(start) }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertNull(state.session) }
        compose.mainClock.advanceTimeBy(150)
        compose.runOnIdle { assertEquals("item", state.session?.item?.key) }
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, clicks); assertNull(state.session) }
    }
}
