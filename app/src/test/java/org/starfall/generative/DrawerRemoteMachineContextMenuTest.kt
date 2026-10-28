package org.starfall.generative

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.drawer.ui.DrawerQuickShortcuts
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.theme.GenerativeTheme
import org.starfall.generative.shared.ui.LocalToolControls
import org.starfall.generative.shared.ui.ToolControls

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi", application = LibraryTestApplication::class)
class DrawerRemoteMachineContextMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun menuKeepsSwitchFirstAndOpensTerminalToolsAndEditor() {
        val machine = RemoteMachine("machine-1", "Staging")
        var enabled by mutableStateOf(true)
        var edits: RemoteMachine? = null
        var terminal: RemoteMachine? = null
        var toggleCount = 0

        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalToolControls provides ToolControls(
                        machines = listOf(machine),
                        settings = ToolSettings(enabledMachines = mapOf(machine.id to enabled)),
                        setMachine = { id, value ->
                            assertEquals(machine.id, id)
                            enabled = value
                            toggleCount++
                        }
                    )
                ) {
                    DrawerQuickShortcuts(
                        onOpenMediaStorage = {},
                        onOpenRemoteMachines = {},
                        onEditRemoteMachine = { edits = it },
                        onOpenTerminal = { terminal = it },
                        onSaveMachineTools = {},
                        maxExpandedHeight = 400.dp
                    )
                }
            }
        }

        compose.onNodeWithText("Remote").performClick()
        val row = compose.onNodeWithTag("drawer_machine_row_machine-1")
        row.assertIsDisplayed().assert(!hasClickAction())
        row.performTouchInput { click(androidx.compose.ui.geometry.Offset(10f, center.y)) }
        compose.onNodeWithText("Machine Tools").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, toggleCount)
            assertEquals(null, edits)
        }

        compose.onNodeWithTag("drawer_machine_options_machine-1").performClick()
        compose.onNodeWithText("Disable").assertIsDisplayed()
        val machineSwitch = compose.onNodeWithTag("drawer_machine_switch_machine-1", useUnmergedTree = true)
        machineSwitch.assertIsOn()
        val switchBounds = machineSwitch.fetchSemanticsNode().boundsInRoot
        val terminalBounds = compose.onNodeWithText("Open terminal").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The switch stays above all menu actions", switchBounds.bottom <= terminalBounds.top)
        compose.onNodeWithText("Tools").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(machine, edits) }

        compose.onNodeWithTag("drawer_machine_options_machine-1").performClick()
        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithText("Machine Tools").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("drawer_machine_options_machine-1").performClick()
        compose.onNodeWithText("Disable").performClick()
        compose.runOnIdle {
            assertFalse(enabled)
            assertEquals(1, toggleCount)
        }
        machineSwitch.assertIsOff()
        // Toggling leaves the menu open, and the switch itself is interactive.
        machineSwitch.performClick()
        compose.runOnIdle {
            assertTrue(enabled)
            assertEquals(2, toggleCount)
        }
        machineSwitch.assertIsOn()
        compose.onNodeWithText("Open terminal").performClick()
        compose.runOnIdle {
            assertEquals(machine, terminal)
            assertEquals(2, toggleCount)
        }
        compose.onNodeWithText("Open terminal").assertDoesNotExist()
    }
}
