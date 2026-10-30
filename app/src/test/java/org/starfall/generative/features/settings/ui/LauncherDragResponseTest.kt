package org.starfall.generative.features.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi")
class LauncherDragResponseTest {
    @get:Rule val compose = createComposeRule()
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun origin() = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
    private fun order() = node("launcher_workspace").fetchSemanticsNode().config[LauncherWorkspaceOrder]
    private fun show() { compose.setContent { MaterialTheme { LauncherDragTestScreen {} } }; compose.waitForIdle() }
    private fun pick(id: String = "Tile 1") {
        val point = node("launcher_item_$id").fetchSemanticsNode().boundsInRoot.center - origin()
        compose.onRoot().performTouchInput { down(point) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
    }
    private fun move(point: Offset) { compose.onRoot().performTouchInput { moveTo(point - origin()) }; compose.waitForIdle() }
    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun sample(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try { block() } finally {
            compose.onRoot().performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    @Test fun defaultReorderStartsWithin160MsInsteadOfTheLauncher650MsPreset() {
        show()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot.center
        pick()
        sample {
            move(target); step(80)
            assertEquals(listOf("folder-alpha", "Tile 1", "Tile 2"), order().take(3))
            step(80)
            assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), order().take(3))
        }
    }

    @Test fun approachingANeighborThroughItsGapMakesRoomWithoutCrossingIntoTheCard() {
        show()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        pick()
        sample {
            move(Offset(target.left - 2f, target.center.y)); step(160)
            assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), order().take(3))
        }
    }

    @Test fun aFolderMemberFartherThanTheConfiguredProximityCannotMoveWorkspaceItems() {
        show()
        node("launcher_item_folder-alpha").performClick()
        val panel = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspace = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        val before = order()
        pick("Tile 19")
        sample {
            move(Offset(workspace.left + 1f, (panel.bottom + 80f).coerceAtMost(workspace.bottom - 32f)))
            step(300)
            assertEquals(before, order())
            assertFalse(order().contains("Tile 19"))
        }
    }

    @Test fun settingTheDelayToZeroAppliesOnTheNextFramesWithoutADwellWait() {
        show()
        node("launcher_options").performClick()
        node("launcher_reorder_delay_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("Close").performClick()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot.center
        pick()
        sample {
            move(target); step(32)
            assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), order().take(3))
        }
    }

    @Test fun crossingTheCenterAfterANoopHoverUpdatesThePreviewWhileStillHolding() {
        show()
        node("launcher_options").performClick()
        node("launcher_reorder_on_entry").performClick()
        compose.onNodeWithText("Close").performClick()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        pick()
        sample {
            move(Offset(target.center.x - 2f, target.center.y)); step(160)
            assertEquals(listOf("folder-alpha", "Tile 1", "Tile 2"), order().take(3))
            move(Offset(target.center.x + 2f, target.center.y)); step(160)
            assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), order().take(3))
        }
    }
}
