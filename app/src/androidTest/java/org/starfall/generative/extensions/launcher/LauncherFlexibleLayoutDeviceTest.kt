package org.starfall.generative.extensions.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Real lazy layouts: no fixed row height, guessed column count or index-based hit regions. */
@RunWith(AndroidJUnit4::class)
class LauncherFlexibleLayoutDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = LauncherDragDropState<String>()
    private val layout = LauncherReorderLayout({ it }, mapOf("items" to listOf("a", "b", "c", "d")))
    private var grid by mutableStateOf(false)
    private var columns by mutableIntStateOf(2)
    private var commits = 0
    private var completions = 0
    private var hoverEnabled = false
    private lateinit var listScroll: LazyListState

    @Composable private fun Tile(id: String, modifier: Modifier) {
        val height = when (id) { "a" -> 64.dp; "b" -> 112.dp; "c" -> 80.dp; else -> 96.dp }
        Box(modifier.fillMaxWidth().height(height)
            .launcherDragSource(state, id, id,
                onDragStart = { check(layout.begin(id)) },
                onDragEnd = {
                    completions++
                    if (it.accepted) { layout.commit(); commits++ } else layout.cancel()
                })
            .launcherDropTarget(state, "slot:$id", hitTest = LauncherHitTest.VisualCenter,
                accepts = { it.key != id },
                onDrop = { layout.hover("items", id, after = false); true })
            .background(if (id == "a") Color.Red else Color.Blue).testTag("tile:$id"))
    }

    private fun show() {
        compose.setContent {
            LauncherDragDropHost(state, Modifier.fillMaxSize().background(Color.White),
                preview = { Box(Modifier.fillMaxSize().background(Color.Green).testTag("shadow")) }) {
                if (hoverEnabled) LauncherHoverEffect(state, delayMillis = 250) { target, _ ->
                    layout.hover("items", target.removePrefix("slot:"), after = false)
                }
                if (grid) {
                    LazyVerticalGrid(columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize().testTag("grid"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(20.dp)) {
                        items(layout.preview.getValue("items"), key = { it }) { Tile(it, Modifier.animateItem()) }
                    }
                } else {
                    listScroll = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().launcherDragAutoScroll(state, listScroll).testTag("list"),
                        state = listScroll,
                        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(layout.preview.getValue("items"), key = { it }) { Tile(it, Modifier.animateItem()) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pick(): Any {
        val source = compose.onNodeWithTag("tile:a").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { down(source.center - compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        return state.session!!.identity
    }

    private fun dropBeforeC() {
        val destination = compose.onNodeWithTag("tile:c").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { moveTo(destination - compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("slot:c", state.hoveredTargetKey) }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(state.session)
            assertNull(state.settlement)
            assertEquals(listOf("b", "a", "c", "d"), layout.preview["items"])
            assertEquals(1, commits)
        }
    }

    @Test fun listWithUnequalRowHeightsDropsAtTheMeasuredRow() {
        show()
        val a = compose.onNodeWithTag("tile:a").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("tile:b").fetchSemanticsNode().boundsInRoot
        assertTrue(b.height > a.height)
        pick()
        dropBeforeC()
    }

    @Test fun switchingGridToListDuringDragPreservesIdentityAndOriginalAnchor() {
        grid = true
        show()
        val identity = pick()
        val anchor = state.session!!.sourceBounds
        compose.runOnIdle { grid = false }
        compose.waitForIdle()
        compose.onNodeWithTag("list").assertExists()
        compose.runOnIdle {
            assertSame(identity, state.session!!.identity)
            assertEquals(anchor, state.session!!.sourceBounds)
            assertEquals(anchor.center, state.session!!.visualCenter)
        }
        dropBeforeC()
    }

    @Test fun changingColumnCountAndThenSwitchingListToGridKeepsDropCoordinatesCurrent() {
        show()
        val identity = pick()
        compose.runOnIdle { grid = true; columns = 3 }
        compose.waitForIdle()
        compose.runOnIdle { columns = 2 }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(identity, state.session!!.identity) }
        dropBeforeC()
    }
    @Test fun noDestinationKeepsTheOriginalSlotAndHidesTheEntireSource() {
        show()
        val original = listOf("a", "b", "c", "d").associateWith {
            compose.onNodeWithTag("tile:$it").fetchSemanticsNode().boundsInRoot
        }
        pick()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { moveTo(Offset(root.width / 2, root.height - 100)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(state.hoveredTargetKey)
            assertEquals(listOf("a", "b", "c", "d"), layout.preview["items"])
        }
        for (id in listOf("b", "c", "d")) {
            assertEquals("siblings must retain their measured slots", original[id],
                compose.onNodeWithTag("tile:$id").fetchSemanticsNode().boundsInRoot)
        }
        // A screenshot checks actual rendering, including decoration, rather than semantic visibility.
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val sourcePoint = original.getValue("a").center - root.topLeft
        assertEquals(Color.White, pixels[sourcePoint.x.toInt(), sourcePoint.y.toInt()])
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, commits)
            assertEquals(1, completions)
            assertNull(state.session)
            assertNull(state.settlement)
            assertEquals(listOf("a", "b", "c", "d"), layout.preview["items"])
        }
        compose.onNodeWithTag("shadow").assertDoesNotExist()
    }

    @Test fun validHoverReordersSiblingsButNativeCancellationRestoresEverySlot() {
        hoverEnabled = true
        show()
        pick()
        val destination = compose.onNodeWithTag("tile:c").fetchSemanticsNode().boundsInRoot.center
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        compose.onRoot().performTouchInput { moveTo(destination - origin) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("b", "a", "c", "d"), layout.preview["items"])
            assertEquals(0, completions)
        }
        val b = compose.onNodeWithTag("tile:b").fetchSemanticsNode().boundsInRoot
        val c = compose.onNodeWithTag("tile:c").fetchSemanticsNode().boundsInRoot
        val d = compose.onNodeWithTag("tile:d").fetchSemanticsNode().boundsInRoot
        assertTrue("siblings must stay ordered without overlap", b.bottom <= c.top && c.bottom <= d.top)
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("a", "b", "c", "d"), layout.preview["items"])
            assertEquals(0, commits)
            assertEquals(1, completions)
            assertNull(state.settlement)
        }
    }

    @Test fun offCenterTouchKeepsShadowAnchorAndCommitsBeforeLandingCompletes() {
        show()
        val source = compose.onNodeWithTag("tile:a").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val start = source.topLeft + Offset(12f, 16f)
        compose.onRoot().performTouchInput { down(start - root.topLeft) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        val destination = compose.onNodeWithTag("tile:c").fetchSemanticsNode().boundsInRoot.center
        // Move the artwork center onto the target while keeping the original touch registration.
        val pointer = destination - (source.center - start)
        compose.onRoot().performTouchInput { moveTo(pointer - root.topLeft) }
        compose.waitForIdle()
        compose.runOnIdle {
            val drag = state.session!!
            assertEquals(start - source.topLeft, drag.pointer - drag.shadowBounds.topLeft)
            assertEquals("slot:c", state.hoveredTargetKey)
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput { up() }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals(1, commits)
                assertEquals(1, completions)
                assertNull(state.session)
                assertNotNull("landing must be independent of storage completion", state.settlement)
            }
            compose.mainClock.advanceTimeBy(400)
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(state.settlement); assertEquals(1, completions) }
    }

    @Test fun secondPointerCancelsWithoutCommittingOrLeakingTheShadow() {
        show()
        pick()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(1, Offset(root.width / 2, root.height / 2))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(state.session)
            assertNull(state.settlement)
            assertEquals(0, commits)
            assertEquals(1, completions)
            assertEquals(listOf("a", "b", "c", "d"), layout.preview["items"])
        }
        compose.onRoot().performTouchInput { cancel() }
    }

    @Test fun lazyListAutoScrollSurvivesSourceDisposalAndStopsAfterCancel() {
        val initial = listOf("a", "b", "c", "d") + (1..40).map { "extra:$it" }
        layout.update(mapOf("items" to initial))
        show()
        val identity = pick()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput { moveTo(Offset(root.width / 2, root.height - 12)) }
            repeat(40) {
                compose.mainClock.advanceTimeBy(32)
                compose.waitForIdle()
            }
            compose.runOnIdle {
                assertTrue("the real lazy list must scroll past the disposed source", listScroll.firstVisibleItemIndex > 0)
                assertSame(identity, state.session!!.identity)
            }
            compose.onRoot().performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val stopped = listScroll.firstVisibleItemIndex to listScroll.firstVisibleItemScrollOffset
            compose.mainClock.advanceTimeBy(300)
            compose.runOnIdle {
                assertEquals(stopped, listScroll.firstVisibleItemIndex to listScroll.firstVisibleItemScrollOffset)
                assertEquals(initial, layout.preview["items"])
                assertEquals(0, commits)
                assertEquals(1, completions)
                assertNull(state.session)
                assertNull(state.settlement)
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

}
