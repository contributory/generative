package org.starfall.generative.features.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JVM/Robolectric Compose tests for the real manually opened sandbox screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi")
class LauncherDragTestScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(onBack: () -> Unit = {}) {
        compose.setContent { MaterialTheme { LauncherDragTestScreen(onBack) } }
        compose.waitForIdle()
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun exists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun center(tag: String): Offset = node(tag).fetchSemanticsNode().boundsInRoot.center

    private fun openFolder() {
        // Edge auto-scroll may legitimately move the icon offscreen after a folder exit.
        node("launcher_workspace").performScrollToIndex(0)
        node("launcher_item_folder-alpha").performClick()
    }

    /** The host (rather than the disposable tile) owns the gesture. */
    private fun press(tag: String) {
        val start = center(tag)
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
    }

    private fun move(to: Offset, settleMs: Long = 350) {
        compose.onRoot().performTouchInput { moveTo(to) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(settleMs)
        compose.waitForIdle()
    }

    private fun release() {
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test fun tapFolderOpensClosesAndResetRestoresSample() {
        show()
        assertFalse(exists("launcher_folder_panel"))
        openFolder()
        node("launcher_folder_panel").assertExists()
        node("launcher_item_Tile 19").assertExists()
        compose.onNodeWithText("Close").performClick()
        assertFalse(exists("launcher_folder_panel"))
        node("launcher_reset").performClick()
        node("launcher_drag_result").assertTextContains("Layout reset", substring = true)
        node("launcher_item_Tile 1").assertExists()
    }

    @Test fun longPressCapturesTileNotNeighboringFolderAndCancelClearsSession() {
        show()
        press("launcher_item_Tile 1")
        node("launcher_drag_status").assertTextContains("Tile 1", substring = true)
        node("launcher_drag_result").assertTextContains("Dragging Tile 1", substring = true)
        // The sibling folder's key must never replace the source key.
        node("launcher_drag_status").assertTextContains("Source: Tile 1", substring = true)
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        node("launcher_drag_status").assertTextContains("Source: none", substring = true)
        node("launcher_drag_result").assertTextContains("layout restored", substring = true)
    }

    @Test fun cancelledGestureRestoresLayoutAndDoesNotMoveFolderMember() {
        show()
        openFolder()
        press("launcher_item_Tile 19")
        node("launcher_drag_status").assertTextContains("Source: Tile 19", substring = true)
        // Cancellation must not commit even when a drop target exists.
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        node("launcher_drag_result").assertTextContains("layout restored", substring = true)
        node("launcher_item_Tile 19").assertExists()
    }

    @Test fun hoveringOnFolderOpensItWithoutChangingTheDraggedSource() {
        show()
        val destination = center("launcher_item_folder-alpha")
        press("launcher_item_Tile 1")
        move(destination, settleMs = 1100)
        node("launcher_folder_panel").assertExists()
        node("launcher_drag_status").assertTextContains("Source: Tile 1", substring = true)
        release()
        node("launcher_drag_result").assertTextContains("Dropped on", substring = true)
        // The drop is committed into the folder, not a duplicate workspace item.
        node("launcher_folder_grid")
            .assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 1")))
    }

    @Test fun cancellingAfterFolderExitRestoresMembershipInTheClosedFolder() {
        show()
        openFolder()
        val panel = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspace = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        press("launcher_item_Tile 19")
        move(Offset(workspace.center.x, (panel.bottom + 80f).coerceAtMost(workspace.bottom - 32f)),
            settleMs = 750)
        assertFalse(exists("launcher_folder_panel"))
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        node("launcher_drag_result").assertTextContains("layout restored", substring = true)
        node("launcher_workspace").assert(!hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
        openFolder()
        node("launcher_folder_grid").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }

    @Test fun quickDropAddsToFolderWithoutWaitingForSpringOpen() {
        show()
        val folder = center("launcher_item_folder-alpha")
        press("launcher_item_Tile 1")
        compose.onRoot().performTouchInput { moveTo(folder) }
        compose.waitForIdle()
        assertFalse(exists("launcher_folder_panel"))
        release()
        assertFalse("quick drop should add without opening", exists("launcher_folder_panel"))
        node("launcher_drag_result").assertTextContains("Dropped on folder-icon", substring = true)
        openFolder()
        node("launcher_folder_grid").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 1")))
    }

    @Test fun workspaceItemCanEnterAndLeaveFolderInOneGesture() {
        show()
        val folderIcon = center("launcher_item_folder-alpha")
        press("launcher_item_Tile 1")
        move(folderIcon, settleMs = 1100)
        node("launcher_folder_panel").assertExists()
        val panel = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspace = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        move(Offset(workspace.center.x, (panel.bottom + 80f).coerceAtMost(workspace.bottom - 32f)),
            settleMs = 750)
        assertFalse("an incoming item must also be able to leave the folder", exists("launcher_folder_panel"))
        node("launcher_drag_status").assertTextContains("Source: Tile 1", substring = true)
        release()
        node("launcher_workspace").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 1")))
        openFolder()
        node("launcher_folder_grid").assert(!hasAnyDescendant(hasTestTag("launcher_item_Tile 1")))
    }

    @Test fun folderMemberCanExitToWorkspaceWhileTheSameGestureRemainsActive() {
        show()
        openFolder()
        val folderBounds = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspaceBounds = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        val workspacePoint = Offset(
            workspaceBounds.center.x,
            (folderBounds.bottom + 80f).coerceAtMost(workspaceBounds.bottom - 32f)
        )
        press("launcher_item_Tile 19")
        move(workspacePoint, settleMs = 750)
        node("launcher_drag_status").assertTextContains("Source: Tile 19", substring = true)
        assertFalse("folder should close after delayed exit", exists("launcher_folder_panel"))
        release()
        // A child insertion slot is more specific than the workspace background.
        node("launcher_drag_result").assertTextContains("workspace", substring = true)
        node("launcher_workspace").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
        openFolder()
        node("launcher_folder_grid")
            .assert(!hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }
    @Test fun folderCanBeDraggedWithoutOpeningAndKeepsAllItsMembers() {
        show()
        val initial = center("launcher_item_folder-alpha")
        val destination = center("launcher_item_Tile 4")
        press("launcher_item_folder-alpha")
        node("launcher_drag_status").assertTextContains("Source: folder-alpha", substring = true)
        assertFalse(exists("launcher_folder_panel"))
        move(destination, settleMs = 850)
        release()
        assertFalse(exists("launcher_folder_panel"))
        org.junit.Assert.assertTrue(center("launcher_item_folder-alpha").y > initial.y)
        openFolder()
        node("launcher_folder_grid").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
        node("launcher_folder_grid").assert(!hasAnyDescendant(hasTestTag("launcher_item_folder-alpha")))
    }

    @Test fun folderEdgeIsAReorderSlotInsteadOfAnEntryHitbox() {
        show()
        val folder = node("launcher_item_folder-alpha").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 1")
        move(Offset(folder.left + folder.width * 0.1f, folder.center.y) - origin, settleMs = 16)
        node("launcher_drag_status").assertTextContains("slot:workspace:folder-alpha", substring = true)
        compose.mainClock.advanceTimeBy(1100)
        compose.waitForIdle()
        assertFalse("folder edges must not trigger spring-open", exists("launcher_folder_panel"))
        release()
        node("launcher_workspace").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 1")))
        org.junit.Assert.assertTrue(center("launcher_item_Tile 1").x < center("launcher_item_folder-alpha").x)
    }

    @Test fun foldersCanAlsoBeReorderedInTheDemoListLayout() {
        show()
        node("launcher_toggle_layout").performClick()
        val destination = center("launcher_item_Tile 2")
        press("launcher_item_folder-alpha")
        move(destination, settleMs = 850)
        release()
        assertFalse(exists("launcher_folder_panel"))
        org.junit.Assert.assertTrue(center("launcher_item_folder-alpha").y > center("launcher_item_Tile 2").y)
        openFolder()
        node("launcher_folder_grid").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }

    @Test fun folderExitMakesRoomOnTheWorkspaceBeforeTheFingerIsReleased() {
        show()
        openFolder()
        val panel = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspace = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        val inWorkspace = hasAnyAncestor(hasTestTag("launcher_workspace")) and
            SemanticsMatcher("workspace tile") {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
                    ?.startsWith("launcher_item_Tile ") == true
            }
        fun positions() = compose.onAllNodes(inWorkspace, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }
            .associate { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] to it.boundsInRoot }
        val before = positions()
        press("launcher_item_Tile 19")
        compose.mainClock.autoAdvance = false
        try {
            move(Offset(workspace.center.x, (panel.bottom + 80f).coerceAtMost(workspace.bottom - 32f)) - origin,
                settleMs = 300)
            repeat(3) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            // Sample before the 400 ms folder exit; edge scrolling must not run to completion.
            node("launcher_drag_status").assertTextContains("Source: Tile 19", substring = true)
            val order = node("launcher_workspace").fetchSemanticsNode().config[LauncherWorkspaceOrder]
            org.junit.Assert.assertTrue("preview must insert near visible siblings: $order",
                order.indexOf("Tile 19") in 0 until order.lastIndex)
            val preview = positions()
            org.junit.Assert.assertTrue("visible siblings must move or be displaced outside the viewport",
                before.any { (key, bounds) -> preview[key] != bounds })
        } finally {
            compose.mainClock.autoAdvance = true
        }
        release()
        node("launcher_workspace").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
        openFolder()
        node("launcher_folder_grid").assert(!hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }

    @Test fun quickFolderExitDropClosesThePanelAndUsesAVisibleSlot() {
        show()
        openFolder()
        val panel = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspace = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 19")
        move(Offset(workspace.center.x, (panel.bottom + 80f).coerceAtMost(workspace.bottom - 32f)) - origin,
            settleMs = 16)
        release()
        assertFalse("a workspace drop must close the folder even before the exit timer", exists("launcher_folder_panel"))
        node("launcher_workspace").assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
        openFolder()
        node("launcher_folder_grid").assert(!hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }

    private fun workspaceOrder() = node("launcher_workspace").fetchSemanticsNode().config[LauncherWorkspaceOrder]

    @Test fun ordinaryItemReordersFromTheNearEdgeWithoutCrossingItsCenter() {
        show()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 1")
        move(Offset(target.left + target.width * 0.1f, target.center.y) - origin, settleMs = 850)
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), workspaceOrder().take(3))
        assertFalse(exists("launcher_folder_panel"))
        release()
    }

    @Test fun ordinaryItemReordersBackwardFromTheFarHalfOfItsNeighbor() {
        show()
        val target = node("launcher_item_Tile 1").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 2")
        move(Offset(target.left + target.width * 0.9f, target.center.y) - origin, settleMs = 850)
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), workspaceOrder().take(3))
        release()
    }

    @Test fun listRowsReorderFromTheirUpperEdgeWithoutAJoinDeadZone() {
        show()
        node("launcher_toggle_layout").performClick()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 1")
        move(Offset(target.center.x, target.top + target.height * 0.1f) - origin, settleMs = 850)
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), workspaceOrder().take(3))
        release()
    }

    @Test fun disablingFolderJoinMakesItsCenterAnOrdinaryReorderSlot() {
        show()
        node("launcher_options").performClick()
        node("launcher_folder_join_enabled").performClick()
        compose.onNodeWithText("Close").performClick()
        val destination = center("launcher_item_folder-alpha")
        press("launcher_item_Tile 1")
        move(destination, settleMs = 1100)
        assertFalse(exists("launcher_folder_panel"))
        org.junit.Assert.assertEquals(listOf("Tile 1", "folder-alpha", "Tile 2"), workspaceOrder().take(3))
        release()
    }

    @Test fun centerCrossingCanBeChosenAndAFastDropRechecksANoopHover() {
        show()
        node("launcher_options").performClick()
        node("launcher_reorder_on_entry").performClick()
        compose.onNodeWithText("Close").performClick()
        val target = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 1")
        move(Offset(target.left + target.width * 0.1f, target.center.y) - origin, settleMs = 850)
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 1", "Tile 2"), workspaceOrder().take(3))
        move(Offset(target.left + target.width * 0.9f, target.center.y) - origin, settleMs = 16)
        release()
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), workspaceOrder().take(3))
    }

    @Test fun quickDropOnTheSameNeighborAfterItRelocatesRechecksTheInsertion() {
        show()
        val first = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 1")
        move(Offset(first.left + first.width * 0.1f, first.center.y) - origin, settleMs = 850)
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1"), workspaceOrder().take(3))
        val relocated = node("launcher_item_Tile 2").fetchSemanticsNode().boundsInRoot
        move(Offset(relocated.left + relocated.width * 0.1f, relocated.center.y) - origin, settleMs = 16)
        release()
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 1", "Tile 2"), workspaceOrder().take(3))
    }

    @Test fun quickDropAcrossTheSameTargetsCenterUpdatesAnAlreadyAppliedPreview() {
        show()
        node("launcher_options").performClick()
        node("launcher_reorder_on_entry").performClick()
        compose.onNodeWithText("Close").performClick()
        val target = node("launcher_item_Tile 3").fetchSemanticsNode().boundsInRoot
        val origin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        press("launcher_item_Tile 1")
        move(Offset(target.left + target.width * 0.1f, target.center.y) - origin, settleMs = 850)
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 1", "Tile 3"), workspaceOrder().take(4))
        move(Offset(target.left + target.width * 0.9f, target.center.y) - origin, settleMs = 16)
        release()
        org.junit.Assert.assertEquals(listOf("folder-alpha", "Tile 2", "Tile 3", "Tile 1"), workspaceOrder().take(4))
    }

}
