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
                    modelPickerOpenRequest = 0,
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
        val quickActionsHeight = initialWindowNode.boundsInRoot.height
        val openingHeightDp = with(compose.density) { quickActionsHeight.toDp().value }
        assertTrue("Quick Actions should open at roughly half the screen height", openingHeightDp in 350f..440f)

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
        val mcpHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Sub-sheet should settle shorter than QuickActions", mcpHeight < quickActionsHeight - 80f)
        assertTrue(
            "Height should interpolate smoothly during contraction",
            midShrinkNode.boundsInRoot.height < quickActionsHeight - 10f &&
                midShrinkNode.boundsInRoot.height > mcpHeight + 10f
        )
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
        assertTrue(
            "Height should interpolate smoothly during expansion",
            midExpandNode.boundsInRoot.height > mcpHeight + 10f &&
                midExpandNode.boundsInRoot.height < quickActionsHeight - 10f
        )

        compose.mainClock.advanceTimeBy(350)
        val restoredHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertEquals(quickActionsHeight, restoredHeight, 2f)

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
    fun tallerLinkedSheetSmoothlyExpandsFromQuickActionsAndContractsOnBack() {
        val servers = (1..6).map { index ->
            McpInfo(id = "mcp-$index", name = "Connector $index", url = "https://example.test/$index")
        }
        compose.setContent {
            LinkedQuickActionsHost(ToolControls(servers = servers, settings = ToolSettings()))
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        val initialWindowNode = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        val sheetNodeId = initialWindowNode.id
        val quickActionsHeight = initialWindowNode.boundsInRoot.height

        compose.onNodeWithText("MCP Connectors").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(100)
        val midGrowHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height

        compose.mainClock.advanceTimeBy(350)
        val tallMcpHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertEquals(sheetNodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)
        assertTrue("Populated MCP sheet should expand taller than QuickActions", tallMcpHeight > quickActionsHeight + 80f)
        assertTrue(
            "Height should interpolate smoothly during expansion",
            midGrowHeight > quickActionsHeight + 10f && midGrowHeight < tallMcpHeight - 10f
        )

        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(100)
        val midShrinkBackHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Height should interpolate smoothly when contracting back to QuickActions",
            midShrinkBackHeight < tallMcpHeight - 10f && midShrinkBackHeight > quickActionsHeight + 10f
        )

        compose.mainClock.advanceTimeBy(350)
        val restoredHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertEquals(quickActionsHeight, restoredHeight, 2f)
    }
}
