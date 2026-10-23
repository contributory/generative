package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.home.BottomSheetsLink
import org.starfall.generative.home.BottomSheetsLinkState
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderConfiguration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class ModelSheetTransitionTest {
    @get:Rule val compose = createComposeRule()

    private val provider = ModelProvider(
        "p", "Provider", ModelApiType.OPENAI, baseUrl = "",
        config = ProviderConfiguration(modelConfigs = mapOf("m1" to ModelConfiguration(displayName = "Model One")))
    )

    @Composable
    private fun Host(sheets: BottomSheetsLinkState) {
        MaterialTheme {
            BottomSheetsLink(
                sheets = sheets,
                mediaKind = null,
                onMediaKindChange = {},
                onCancelEdit = {},
                providers = listOf(provider),
                providerGroups = emptyList(),
                selectedProviderId = "p",
                selectedModelName = "m1",
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
                modelPickerSelectedTab = "default",
                onModelPickerSelectedTabChange = {},
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

    @Test
    fun switchingBetweenSelectedModelAndPickerSlidesAndResizesInOneSheet() {
        compose.setContent {
            val sheets = remember { BottomSheetsLinkState().apply { openSelectedModelOverview() } }
            Host(sheets)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        val initial = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        val sheetNodeId = initial.id
        val overviewHeight = initial.boundsInRoot.height

        compose.onNodeWithTag("open-model-picker-button").performClick()
        compose.mainClock.advanceTimeBy(100)
        val mid = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        assertEquals("The sheet must stay open instead of closing and reopening", sheetNodeId, mid.id)
        val enteringLeftPx = with(compose.density) {
            compose.onNodeWithTag("model-picker-list").getUnclippedBoundsInRoot().left.toPx()
        }
        assertTrue("The picker should slide in from the right", enteringLeftPx > 40f)

        compose.mainClock.advanceTimeBy(350)
        val pickerHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertTrue("The picker page is taller than the overview", pickerHeight > overviewHeight + 80f)
        assertTrue(
            "Height should interpolate while sliding",
            mid.boundsInRoot.height > overviewHeight - 1f && mid.boundsInRoot.height < pickerHeight - 10f
        )
        compose.onNodeWithTag("open-selected-model-button").assertIsDisplayed()

        compose.onNodeWithTag("open-selected-model-button").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(sheetNodeId, compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().id)
        compose.mainClock.advanceTimeBy(350)
        val restored = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode()
        assertEquals(sheetNodeId, restored.id)
        assertEquals(overviewHeight, restored.boundsInRoot.height, 2f)
        compose.onNodeWithTag("open-model-picker-button").assertIsDisplayed()
    }
}
