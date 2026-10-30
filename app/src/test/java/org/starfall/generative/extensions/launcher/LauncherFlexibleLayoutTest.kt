package org.starfall.generative.extensions.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real lazy layouts: no fixed row height, guessed column count or index-based hit regions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi")
class LauncherFlexibleLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val state = LauncherDragDropState<String>()
    private val layout = LauncherReorderLayout({ it }, mapOf("items" to listOf("a", "b", "c", "d")))
    private var grid by mutableStateOf(false)
    private var columns by mutableIntStateOf(2)
    private var commits = 0

    @Composable private fun Tile(id: String, modifier: Modifier) {
        val height = when (id) { "a" -> 64.dp; "b" -> 112.dp; "c" -> 80.dp; else -> 96.dp }
        Box(modifier.fillMaxWidth().height(height)
            .launcherDragSource(state, id, id,
                onDragStart = { check(layout.begin(id)) },
                onDragEnd = {
                    if (it.accepted) { layout.commit(); commits++ } else layout.cancel()
                })
            .launcherDropTarget(state, "slot:$id", hitTest = LauncherHitTest.VisualCenter,
                accepts = { it.key != id },
                onDrop = { layout.hover("items", id, after = false); true })
            .testTag("tile:$id"))
    }

    private fun show() {
        compose.setContent {
            LauncherDragDropHost(state, Modifier.fillMaxSize(), preview = { Box(Modifier.fillMaxSize()) }) {
                if (grid) {
                    LazyVerticalGrid(columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize().testTag("grid"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(20.dp)) {
                        items(layout.preview.getValue("items"), key = { it }) { Tile(it, Modifier.animateItem()) }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("list"),
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
        compose.onRoot().performTouchInput { down(source.center) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        return state.session!!.identity
    }

    private fun dropBeforeC() {
        val destination = compose.onNodeWithTag("tile:c").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { moveTo(destination) }
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
}
