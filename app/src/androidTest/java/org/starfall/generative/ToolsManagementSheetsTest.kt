package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasToggleAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.home.shared.ui.FeatureToolsSheet
import org.starfall.generative.home.shared.ui.McpConnectorsSheet
import org.starfall.generative.home.shared.ui.QuickActionsSheet
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.ui.LocalToolControls
import org.starfall.generative.shared.ui.ToolControls

class ToolsManagementSheetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun quickActionsOpenFeatureToolsAndMcpConnectorsSeparately() {
        var featureToolsOpened = false
        var mcpConnectorsOpened = false
        compose.setContent {
            MaterialTheme {
                QuickActionsSheet(
                    onPickImage = {},
                    onPickDocument = {},
                    onTakePhoto = {},
                    onCreateImage = {},
                    onCreateVideo = {},
                    onOpenFeatureTools = { featureToolsOpened = true },
                    onOpenMcpConnectors = { mcpConnectorsOpened = true },
                    onOpenRemoteMachines = {},
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Feature Tools").performClick()
        compose.runOnIdle {
            assertTrue(featureToolsOpened)
            assertTrue(!mcpConnectorsOpened)
        }
        compose.onNodeWithText("MCP Connectors").performClick()
        compose.runOnIdle { assertTrue(mcpConnectorsOpened) }
    }

    @Test
    fun featureToolsSheetContainsBuiltInToolSwitches() {
        val updates = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalToolControls provides ToolControls(
                        settings = ToolSettings(),
                        setSystem = { name, config -> updates.add(name to config.enabled) },
                        setSendFile = { updates.add("send_file" to it) }
                    )
                ) {
                    FeatureToolsSheet(onDismiss = {})
                }
            }
        }

        compose.onNodeWithText("Feature Tools").assertIsDisplayed()
        compose.onNodeWithText("Image Generation").assertIsDisplayed()
        compose.onNodeWithText("Video Generation").assertIsDisplayed()
        compose.onNodeWithText("Send File").assertIsDisplayed()
        compose.onNodeWithText("MCP Connectors").assertDoesNotExist()
        compose.onAllNodes(hasToggleAction()).assertCountEquals(3)
        compose.onAllNodes(hasToggleAction())[0].performClick()
        compose.runOnIdle { assertEquals(listOf("generate_image" to true), updates) }
    }

    @Test
    fun mcpConnectorsSheetShowsOnlyConnectorManagement() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalToolControls provides ToolControls()) {
                    McpConnectorsSheet(onDismiss = {})
                }
            }
        }

        compose.onNodeWithText("MCP Connectors").assertIsDisplayed()
        compose.onNodeWithText("No MCP Servers").assertIsDisplayed()
        compose.onNodeWithText("Feature Tools").assertDoesNotExist()
    }
}
