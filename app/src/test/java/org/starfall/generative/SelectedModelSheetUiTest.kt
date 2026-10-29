package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.shared.ui.SelectedModelOverviewContent
import org.starfall.generative.features.home.shared.ui.SelectedModelOverviewSheet
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class SelectedModelSheetUiTest {
    @get:Rule val compose = createComposeRule()
    private val provider = ModelProvider("p", "Primary", ModelApiType.OPENAI, baseUrl = "")

    @Test fun selectedModelSheetOpensPickerFromItsAction() {
        var opened = 0
        compose.setContent {
            MaterialTheme {
                SelectedModelOverviewSheet(
                    provider = provider, folderName = null, modelId = "alpha",
                    config = ModelConfiguration(supportsThinking = false),
                    conversationReasoningEffort = null, onSetReasoningEffort = {},
                    onOpenModelPicker = { opened++ }, onDismiss = {}
                )
            }
        }
        compose.onNodeWithTag("open-model-picker-button").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun modelOptionsStartCollapsedAndToggleOnlyConfiguredTool() {
        var configuration by mutableStateOf(
            ModelConfiguration(supportsThinking = false, modelTools = listOf("web_search", "file_search"))
        )
        compose.setContent {
            MaterialTheme {
                SelectedModelOverviewContent(
                    provider = provider, folderName = null, modelId = "alpha",
                    config = configuration, conversationReasoningEffort = null,
                    previewTier = 0, onPreviewTierChange = {}, onSetReasoningEffort = {},
                    onSetModelTools = { configuration = configuration.copy(enabledModelTools = it) },
                    onOpenModelPicker = {}
                )
            }
        }
        compose.onNodeWithTag("model-options-toggle").assertIsDisplayed()
        compose.onNodeWithTag("model-tool-switch-web_search").assertDoesNotExist()
        compose.onNodeWithTag("model-options-toggle").performClick()
        compose.onNodeWithTag("model-tool-switch-web_search").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("web_search"), configuration.enabledModelTools) }
        compose.onNodeWithTag("model-options-toggle").performClick()
        compose.onNodeWithTag("model-tool-switch-web_search").assertDoesNotExist()
    }
}
