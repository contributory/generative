package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.BottomSheetsLink
import org.starfall.generative.features.home.BottomSheetsLinkState
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.ui.LocalToolControls
import org.starfall.generative.shared.ui.ToolControls

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class QuickActionsSheetTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun LinkedQuickActionsHost(controls: ToolControls) {
        MaterialTheme {
            val sheets = remember { BottomSheetsLinkState().apply { openAddMenu() } }
            CompositionLocalProvider(LocalToolControls provides controls) {
                BottomSheetsLink(
                    sheets = sheets,
                    mediaKind = null,
                    onMediaKindChange = {},
                    onCancelEdit = {},
                    onShowSkillsMenu = {},
                    providers = emptyList(),
                    providerGroups = emptyList(),
                    selectedProviderId = "",
                    selectedModelName = "",
                    conversationReasoningEffort = null,
                    onSetReasoningEffort = {},
                    onSelectModel = { _, _ -> },
                    onSetThinkingPassback = { _, _, _ -> },
                    onSetModelReasoningEffort = { _, _, _ -> },
                    onSetModelTools = { _, _, _ -> },
                    onEditModel = { _, _ -> },
                    onEditProvider = {},
                    modelPickerCollapsedGroups = emptySet(),
                    modelPickerCollapsedProviders = emptySet(),
                    onModelPickerCollapsedGroupsChange = {},
                    onModelPickerCollapsedProvidersChange = {},
                    dynamicModelsMap = emptyMap(),
                    mediaProviders = emptyList(),
                    mediaProviderId = "",
                    mediaModelId = "",
                    onMediaModelChange = { _, _ -> },
                    attachments = emptyList(),
                    onAttachmentsChange = {}
                )
            }
        }
    }

    @Test
    fun switchingBetweenQuickActionsAndLinkedSheetsAnimatesSlideAndHeightInOneSheet() {
        compose.setContent {
            LinkedQuickActionsHost(ToolControls(settings = ToolSettings()))
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        val initialWindowNode = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        val sheetNodeId = initialWindowNode.id
        assertTrue(initialWindowNode.boundsInRoot.height > 0f)

        // Navigate forward to MCP Connectors (shorter empty state): same sheet shrinks and slides left.
        compose.onNodeWithText("MCP Connectors").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(100)

        val midShrinkNode = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        assertEquals("Sheet must stay open instead of closing and reopening", sheetNodeId, midShrinkNode.id)
        val enteringLeftPx = with(compose.density) {
            compose.onNodeWithText("No MCP Servers").getUnclippedBoundsInRoot().left.toPx()
        }
        assertTrue("Incoming sub-sheet content should slide in from the right", enteringLeftPx > 40f)

        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("No MCP Servers").assertIsDisplayed()

        // Navigate back to QuickActions: same sheet expands and slides right.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(100)

        val midExpandNode = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        assertEquals(sheetNodeId, midExpandNode.id)
        val returningLeftPx = with(compose.density) {
            compose.onNodeWithText("Photos").getUnclippedBoundsInRoot().left.toPx()
        }
        assertTrue("Returning QuickActions content should slide in from the left", returningLeftPx < 0f)

        compose.mainClock.advanceTimeBy(350)
        assertEquals(sheetNodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)

        // Feature Tools also transitions within the same sheet instance.
        compose.onNodeWithText("Feature Tools").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(450)
        assertEquals(sheetNodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)
        compose.onNodeWithText("Image Generation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(450)
        assertEquals(sheetNodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)
        compose.onNodeWithText("Photos").assertIsDisplayed()
    }

    @Test
    fun nestedSheetNavigationRemainsInTheSameMaterialSheet() {
        val servers = (1..6).map { index ->
            McpInfo(id = "mcp-$index", name = "Connector", url = "https://example.test/$index")
        }
        compose.setContent {
            LinkedQuickActionsHost(ToolControls(servers = servers, settings = ToolSettings()))
        }
        compose.waitForIdle()
        val nodeId = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id

        compose.onNodeWithText("MCP Connectors").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(450)
        assertEquals(nodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(450)
        assertEquals(nodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)
        compose.onNodeWithText("Photos").assertIsDisplayed()
    }
}
