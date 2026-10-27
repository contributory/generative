package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.speech.ui.SpeechServicesScreen
import org.starfall.generative.shared.model.SpeechService

class SpeechServicesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingSpeechServiceOpensEditorWithoutSelectingIt() {
        var selectedId: String? = null
        compose.setContent {
            MaterialTheme {
                SpeechServicesScreen(
                    speechServices = listOf(SpeechService("voice", "Voice")),
                    providers = emptyList(),
                    selectedSpeechServiceId = null,
                    onSetTtsProvider = { selectedId = it },
                    onSaveService = {},
                    onDeleteService = {},
                    onReorderServices = {},
                    onTestVoice = { _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Voice").performClick()
        compose.onNodeWithText("Edit Speech Service").assertIsDisplayed()
        compose.runOnIdle { assertNull(selectedId) }
    }

    @Test
    fun speechServiceContextMenuCanSetTheTtsProvider() {
        var selectedId: String? = null
        compose.setContent {
            MaterialTheme {
                SpeechServicesScreen(
                    speechServices = listOf(
                        SpeechService("system", "System", provider = "system"),
                        SpeechService("voice", "Voice", provider = "provider", modelId = "tts")
                    ),
                    providers = emptyList(),
                    selectedSpeechServiceId = null,
                    onSetTtsProvider = { selectedId = it },
                    onSaveService = {},
                    onDeleteService = {},
                    onReorderServices = {},
                    onTestVoice = { _, _ -> },
                    onBack = {}
                )
            }
        }

        val menus = compose.onAllNodesWithContentDescription("More options")
        menus.assertCountEquals(2)
        menus[1].performClick()
        compose.onNodeWithText("Set TTS Provider").performClick()
        compose.runOnIdle { assertEquals("voice", selectedId) }
    }
}
