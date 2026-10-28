package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.speech.ui.SpeechEditor
import org.starfall.generative.shared.model.SpeechService

class SpeechEditorDefaultsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun defaultNameTracksEngineAndAllCloudEnginesUseSharedKeyVisibility() {
        compose.setContent { MaterialTheme {
            SpeechEditor(SpeechService("new", "Custom TTS"), true, emptyList(), false,
                onTest = { _, _ -> }, onDismiss = {}, onSave = {})
        } }
        listOf("ElevenLabs", "Fish Audio", "Cartesia").forEach { engine ->
            compose.onNodeWithTag("speech-engine-selector").performScrollTo().performClick()
            compose.onNodeWithText(engine).performClick()
            compose.onNodeWithTag("speech-service-name").assertTextContains(engine)
            compose.onNodeWithContentDescription("Show API key").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Hide API key").assertExists()
        }
        compose.onNodeWithTag("speech-service-name").performScrollTo().performTextReplacement("My narrator")
        compose.onNodeWithTag("speech-engine-selector").performScrollTo().performClick()
        compose.onNodeWithText("ElevenLabs").performClick()
        compose.onNodeWithTag("speech-service-name").assertTextContains("My narrator")
        compose.onNodeWithTag("speech-engine-selector").performScrollTo().performClick()
        compose.onNodeWithText("Android System TTS").performClick()
        compose.onNodeWithTag("speech-service-name").assertTextContains("My narrator")
    }
    @Test fun sentenceCountDefaultsToTenAndIsSavedWithTheService() {
        val provider = org.starfall.generative.shared.model.provider.ModelProvider("p", "Provider",
            org.starfall.generative.shared.model.api.ModelApiType.OPENAI, baseUrl = "https://example.test/v1",
            config = org.starfall.generative.shared.model.provider.ProviderConfiguration(modelConfigs = mapOf(
                "tts" to org.starfall.generative.shared.model.language.ModelConfiguration(
                    modelType = org.starfall.generative.shared.model.language.ModelType.TEXT_TO_SPEECH))))
        var saved: SpeechService? = null
        compose.setContent { MaterialTheme {
            SpeechEditor(SpeechService("s", "Voice", provider = "p", modelId = "tts"), false, listOf(provider), false,
                onTest = { _, _ -> }, onDismiss = {}, onSave = { saved = it })
        } }
        compose.onNodeWithTag("speech-chunk-size").performScrollTo().assertTextContains("10")
            .performTextReplacement("6")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(6, saved?.sentencesPerChunk) }
    }

}
