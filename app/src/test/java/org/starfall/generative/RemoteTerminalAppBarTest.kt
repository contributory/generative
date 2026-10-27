package org.starfall.generative

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.model.remote.TerminalInputMode
import org.starfall.generative.shared.terminal.RemoteTerminalBridge
import org.starfall.generative.shared.theme.GenerativeTheme
import org.starfall.generative.shared.ui.terminal.TerminalAppBar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class RemoteTerminalAppBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun inputModeOpensMenuAndImeIsAnIndependentAppBarToggle() {
        val bridge = RemoteTerminalBridge(ApplicationProvider.getApplicationContext<Context>(), TerminalInputMode.REMOTE)
        var modeChanges = 0
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                TerminalAppBar(
                    bridge = bridge,
                    onToggleSessions = {},
                    onDisconnect = {},
                    canUseRemoteInput = true,
                    onInputModeChange = { bridge.setInputMode(it); modeChanges++ },
                    onToggleIme = { bridge.updateImeEnabled(!bridge.imeEnabled) },
                    onToggleForceMouse = { bridge.forceMouse = !bridge.forceMouse },
                    fontDp = 14,
                    onFontChange = {}
                )
            }
        }
        compose.onNodeWithContentDescription("Terminal input mode: Remote").performClick()
        compose.runOnIdle { assertEquals(0, modeChanges) }
        compose.onNodeWithText("Local").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(TerminalInputMode.LOCAL, bridge.inputMode) }
        compose.onNodeWithContentDescription("Enable IME composition").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(TerminalInputMode.LOCAL, bridge.inputMode) }
        compose.onNodeWithContentDescription("Terminal input mode: Local").performClick()
        compose.onNodeWithText("Remote").performClick()
        compose.onNodeWithContentDescription("Enable IME composition").assertIsOn()
        compose.onNodeWithContentDescription("Show or hide terminal sessions").assertIsDisplayed()
        bridge.close()
    }

    @Test
    fun disconnectAlsoClosesTheScreenWithoutAnActiveSession() {
        var closed = false
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                TerminalAppBar(
                    bridge = null,
                    onToggleSessions = {},
                    onDisconnect = { closed = true },
                    canUseRemoteInput = true,
                    onInputModeChange = {},
                    onToggleIme = {},
                    onToggleForceMouse = {},
                    fontDp = 14,
                    onFontChange = {}
                )
            }
        }
        compose.onNodeWithContentDescription("Disconnect and close terminal").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(true, closed) }
    }
}
