package org.starfall.generative.features.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** JVM/Robolectric Compose tests for the real manually opened sandbox screen. */
@RunWith(AndroidJUnit4::class)
class LauncherDragDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(onBack: () -> Unit = {}) {
        compose.setContent { MaterialTheme { LauncherDragTestScreen(onBack) } }
        compose.waitForIdle()
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun exists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun center(tag: String): Offset =
        node(tag).fetchSemanticsNode().boundsInRoot.center -
            compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft

    /** The host (rather than the disposable tile) owns the gesture. */
    private fun press(tag: String) {
        val start = center(tag)
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
    }

    private fun move(to: Offset, settleMs: Long = 350) {
        compose.onRoot().performTouchInput { moveTo(to) }
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
        node("launcher_item_folder-alpha").performClick()
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
        node("launcher_item_folder-alpha").performClick()
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
        move(destination, settleMs = 650)
        node("launcher_folder_panel").assertExists()
        node("launcher_drag_status").assertTextContains("Source: Tile 1", substring = true)
        release()
        node("launcher_drag_result").assertTextContains("Dropped on", substring = true)
        // The drop is committed into the folder, not a duplicate workspace item.
        node("launcher_folder_grid")
            .assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 1")))
    }

    @Test fun folderMemberCanExitToWorkspaceWhileTheSameGestureRemainsActive() {
        show()
        node("launcher_item_folder-alpha").performClick()
        val folderBounds = node("launcher_folder_panel").fetchSemanticsNode().boundsInRoot
        val workspaceBounds = node("launcher_workspace").fetchSemanticsNode().boundsInRoot
        val rootOrigin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        val workspacePoint = Offset(
            workspaceBounds.center.x,
            (folderBounds.bottom + 80f).coerceAtMost(workspaceBounds.bottom - 32f)
        ) - rootOrigin
        press("launcher_item_Tile 19")
        move(workspacePoint, settleMs = 750)
        node("launcher_drag_status").assertTextContains("Source: Tile 19", substring = true)
        assertFalse("folder should close after delayed exit", exists("launcher_folder_panel"))
        release()
        node("launcher_drag_result").assertTextContains("Dropped on workspace", substring = true)
        node("launcher_item_folder-alpha").performClick()
        node("launcher_folder_grid")
            .assert(!hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }
}
