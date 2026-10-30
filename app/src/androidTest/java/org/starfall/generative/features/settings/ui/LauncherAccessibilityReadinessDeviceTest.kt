package org.starfall.generative.features.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility migration gate for the actual launcher sandbox, not a mocked semantics tree.
 *
 * The direct-manipulation implementation currently lacks semantic reorder operations.
 * A failing test here is a BLOCKER for replacing DnD in production; do not ignore it
 * or turn it into a negative assertion merely to make the test suite green.
 */
@RunWith(AndroidJUnit4::class)
class LauncherAccessibilityReadinessDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() {
        compose.setContent { MaterialTheme { LauncherDragTestScreen {} } }
        compose.waitForIdle()
    }

    @Test fun labelsRemainVisibleAndTilesHaveTouchFriendlyBounds() {
        show()
        compose.onNodeWithText("Tile 1", useUnmergedTree = true).assertIsDisplayed()
        val node = compose.onNodeWithTag("launcher_item_Tile 1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val minSizePx = 48f * compose.activity.resources.displayMetrics.density
        assertTrue("Touch targets must be at least 48dp wide", node.boundsInRoot.width >= minSizePx)
        assertTrue("Touch targets must be at least 48dp tall", node.boundsInRoot.height >= minSizePx)
    }

    @Test fun folderCanStillBeOpenedBySemanticClick() {
        show()
        compose.onNodeWithTag("launcher_item_folder-alpha", useUnmergedTree = true)
            .assertHasClickAction().performClick()
        compose.onNodeWithTag("launcher_folder_panel", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun screenReaderCanMoveATileWithoutPerformingATouchDrag() {
        show()
        val before = compose.onNodeWithTag("launcher_workspace", useUnmergedTree = true)
            .fetchSemanticsNode().config[LauncherWorkspaceOrder]
        val tile = compose.onNodeWithTag("launcher_item_Tile 1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val actions = tile.config[SemanticsActions.CustomActions]
        val move = actions.firstOrNull { action ->
            action.label.contains("move", ignoreCase = true) ||
                action.label.contains("reorder", ignoreCase = true)
        }
        assertNotNull(
            "BLOCKER: a TalkBack user must have a named reorder action without long-press/drag",
            move
        )
        compose.runOnIdle { assertTrue("Accessible move action must commit", move!!.action()) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("launcher_workspace", useUnmergedTree = true)
            .fetchSemanticsNode().config[LauncherWorkspaceOrder]
        assertNotEquals("Accessible reorder must change item order", before, after)
    }
}
