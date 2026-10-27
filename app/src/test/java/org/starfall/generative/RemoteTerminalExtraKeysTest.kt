package org.starfall.generative

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.model.remote.TerminalInputMode
import org.starfall.generative.shared.terminal.RemoteTerminalBridge
import org.starfall.generative.shared.theme.GenerativeTheme
import org.starfall.generative.shared.ui.terminal.TerminalExtraKeys

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class RemoteTerminalExtraKeysTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun extraKeysKeepTerminalControlsAndPlaceUpAboveTheOtherArrows() {
        val bridge = RemoteTerminalBridge(
            ApplicationProvider.getApplicationContext<Context>(),
            TerminalInputMode.REMOTE
        )
        try {
            compose.setContent {
                GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                    TerminalExtraKeys(bridge)
                }
            }

            listOf("ESC", "TAB", "CTRL", "ALT", "HOME", "END", "PGUP", "PGDN")
                .forEach { compose.onNodeWithText(it).assertExists() }
            listOf("|", "~", "-", "/", "\\", "_", "$", "&", "*", "<", ">")
                .forEach { compose.onNodeWithText(it).assertDoesNotExist() }

            val up = compose.onNodeWithText("↑").getUnclippedBoundsInRoot()
            val left = compose.onNodeWithText("←").getUnclippedBoundsInRoot()
            val down = compose.onNodeWithText("↓").getUnclippedBoundsInRoot()
            val right = compose.onNodeWithText("→").getUnclippedBoundsInRoot()
            assertTrue(up.bottom <= down.top)
            assertEquals(down.center.x, up.center.x, 1f)
            assertEquals(left.center.y, down.center.y, 1f)
            assertEquals(right.center.y, down.center.y, 1f)
            assertTrue(left.right <= down.left)
            assertTrue(down.right <= right.left)
        } finally {
            bridge.close()
        }
    }
}
