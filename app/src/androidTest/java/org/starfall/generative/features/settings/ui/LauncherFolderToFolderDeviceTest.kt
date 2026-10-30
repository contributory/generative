package org.starfall.generative.features.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Developer Settings demo: two actual folder tiles and separate folder contents. */
@RunWith(AndroidJUnit4::class)
class LauncherFolderToFolderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() {
        compose.setContent { MaterialTheme { LauncherDragTestScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    private fun order(): List<String> =
        compose.onNodeWithTag("launcher_workspace", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(LauncherWorkspaceOrder).orEmpty()

    private fun center(id: String): Offset =
        compose.onNodeWithTag("launcher_item_$id", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center -
            compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft

    private fun dragBetaOntoAlpha() {
        val destination = center("folder-alpha")
        compose.onRoot().performTouchInput { down(center("folder-beta")) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { moveTo(destination) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
    }

    @Test fun secondFolderYieldsToFirstFolderDuringActiveDrag() {
        show()
        assertEquals(listOf("folder-alpha", "Tile 1", "Tile 2"), order().take(3))
        dragBetaOntoAlpha()
        assertEquals("Both folders must reorder before finger release",
            "folder-beta", order().first())
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("folder-beta", order().first())
        compose.onNodeWithTag("launcher_item_folder-beta", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("launcher_folder_grid", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 25")))
    }

    @Test fun cancelledFolderToFolderReorderRestoresDemoWorkspace() {
        show()
        val original = order()
        dragBetaOntoAlpha()
        assertEquals("folder-beta", order().first())
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals("Folder drag cancellation restores original demo layout", original, order())
    }

    @Test fun betaFolderMembersCanReorderJustLikeAlphaMembers() {
        show()
        compose.onNodeWithTag("launcher_item_folder-beta", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val firstPosition = center("Tile 25")
        val secondPosition = center("Tile 26")
        compose.onRoot().performTouchInput { down(firstPosition) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { moveTo(secondPosition) }
        compose.mainClock.advanceTimeBy(850)
        compose.waitForIdle()
        val yieldedPosition = center("Tile 26")
        assertEquals("Beta's first slot must be vacated during hover", firstPosition.x, yieldedPosition.x, 5f)
        assertEquals(firstPosition.y, yieldedPosition.y, 5f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("launcher_folder_grid", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 25")))
    }

    @Test fun originalAlphaFolderStillOpensAndKeepsItsMembers() {
        show()
        compose.onNodeWithTag("launcher_item_folder-alpha", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("launcher_folder_grid", useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag("launcher_item_Tile 19")))
    }
}
