package org.starfall.generative

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.tools.ui.ToolsEditor
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.theme.GenerativeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class MediaProfileEditorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledToolWithoutModelStillOpensProfileLibrary() {
        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                ToolsEditor(
                    providers = emptyList(),
                    settings = ToolSettings(system = mapOf("generate_image" to SystemToolConfig(enabled = false))),
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithTag("media_profile_picker_generate_image").assertIsNotEnabled()
        compose.onNodeWithTag("media_profile_library_generate_image").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Image Profiles").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add").assertIsEnabled()
    }
}
