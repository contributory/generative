package org.starfall.generative

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
import org.starfall.generative.features.providers.editor.ui.ProviderApiTypeSelector
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.theme.GenerativeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ProviderApiTypeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun apiTypeIsAnActionRowWithSelectedSubtitleAndFooterlessDialog() {
        var selected by mutableStateOf(ModelApiType.OPENAI_RESPONSES)
        val selections = mutableListOf<ModelApiType>()
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                ProviderApiTypeSelector(selected) {
                    selected = it
                    selections += it
                }
            }
        }

        compose.onNodeWithText("API Type").assertIsDisplayed()
        compose.onNodeWithText("OpenAI Responses").assertIsDisplayed()
        compose.onNodeWithTag("provider-type-selector").assertHasClickAction().performClick()
        compose.onNodeWithTag("app-dialog").assertExists()
        compose.onNodeWithTag("app-dialog-buttons").assertDoesNotExist()
        compose.onNodeWithTag("provider-api-type-openai_responses").assertIsSelected()
        compose.onNodeWithTag("provider-api-type-anthropic").performClick()

        compose.onNodeWithTag("app-dialog").assertDoesNotExist()
        compose.onNodeWithText("Anthropic Messages").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(ModelApiType.ANTHROPIC), selections) }
    }

    @Test fun choosingCurrentApiTypeClosesDialogWithoutChangingTheType() {
        var selected by mutableStateOf(ModelApiType.OPENAI)
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                ProviderApiTypeSelector(selected) { selected = it }
            }
        }
        compose.onNodeWithTag("provider-type-selector").performClick()
        compose.onNodeWithTag("provider-api-type-openai").performClick()
        compose.onNodeWithTag("app-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(ModelApiType.OPENAI, selected) }
    }
}
