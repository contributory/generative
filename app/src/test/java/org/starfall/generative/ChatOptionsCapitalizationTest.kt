package org.starfall.generative

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.chatoptions.ui.HelperSettingsSheet
import org.starfall.generative.features.chatoptions.ui.PromptEditor
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.theme.GenerativeTheme

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ChatOptionsCapitalizationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chatOptionsTextFields_requestSentenceCapitalizationFromIme() {
        var lastInputType = 0
        var showHelperSheet by mutableStateOf(false)

        compose.setContent {
            InterceptPlatformTextInput(
                interceptor = { request, _ ->
                    val editorInfo = EditorInfo()
                    request.createInputConnection(editorInfo)
                    lastInputType = editorInfo.inputType
                    awaitCancellation()
                }
            ) {
                GenerativeTheme(darkTheme = false, amoledMode = false) {
                    if (!showHelperSheet) {
                        PromptEditor(
                            prompt = null,
                            onPreview = {},
                            onBack = {}
                        )
                    } else {
                        HelperSettingsSheet(
                            name = "title_generation",
                            config = SystemToolConfig(),
                            appPreferences = AppPreferences(),
                            onSave = {},
                            onAutoSummaryEnabledChange = {},
                            onSummarySuggestionEnabledChange = {},
                            onAutoSummaryPercentChange = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        // 1. Prompt Editor: prompt name field in TopAppBar
        compose.onNodeWithTag("prompt_name_input").performClick()
        compose.waitForIdle()
        assertNotEquals(
            "Prompt name input should request sentence capitalization",
            0,
            lastInputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )

        // 2. Prompt Editor: prompt content field
        lastInputType = 0
        compose.onNodeWithTag("prompt_content_input").performClick()
        compose.waitForIdle()
        assertNotEquals(
            "Prompt content input should request sentence capitalization",
            0,
            lastInputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )

        // 3. Helper Settings Sheet: helper instruction prompt field
        lastInputType = 0
        showHelperSheet = true
        compose.waitForIdle()
        compose.onNodeWithTag("helper_prompt_input").performClick()
        compose.waitForIdle()
        assertNotEquals(
            "Helper instruction prompt input should request sentence capitalization",
            0,
            lastInputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
    }
}
