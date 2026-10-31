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
import org.starfall.generative.features.providers.editor.ui.ModelTypeSelector
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.theme.GenerativeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-w400dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ModelTypeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modelTypeOpensFooterlessDialogAndSelectsImmediately() {
        var selected by mutableStateOf(ModelType.TEXT_GENERATION)
        var changes = 0
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                ModelTypeSelector(selected) { selected = it; changes++ }
            }
        }
        compose.onNodeWithTag("model-type-selector").assertHasClickAction()
        compose.onNodeWithText("Model Type").assertIsDisplayed()
        compose.onNodeWithText("Text Generation").assertIsDisplayed()
        compose.onNodeWithTag("model-type-selector").performClick()
        compose.onNodeWithTag("app-dialog").assertExists()
        compose.onNodeWithTag("app-dialog-buttons").assertDoesNotExist()
        compose.onNodeWithTag("model-type-text_generation").assertIsSelected()
        compose.onNodeWithTag("model-type-video_generation").performClick()
        compose.onNodeWithTag("app-dialog").assertDoesNotExist()
        compose.onNodeWithText("Video Generation").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ModelType.VIDEO_GENERATION, selected)
            assertEquals(1, changes)
        }
    }
}
