package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.speech.ui.SpeechModelField
import org.starfall.generative.integrations.speech.SpeechSynthesisModel
import org.starfall.generative.shared.model.SpeechEngine
import org.starfall.generative.shared.model.SpeechService

/** Model defaults must come from each engine's fetched catalog, not a fixed model ID. */
class SpeechModelFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectsFirstFetchedModelForEachStandaloneEngine() {
        var engine by mutableStateOf(SpeechEngine.ELEVENLABS)
        var apiKey by mutableStateOf("")
        var modelId by mutableStateOf<String?>(null)
        val firstIds = SpeechEngine.entries.filter { it.standalone }.associateWith { "first-${it.name}" }
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("new", "Voice", engine = engine, apiKey = apiKey, modelId = modelId),
                    onChange = { modelId = it },
                    fetchModels = { service ->
                        listOf(
                            SpeechSynthesisModel(firstIds.getValue(service.engine), "First"),
                            SpeechSynthesisModel("second-${service.engine.name}", "Second")
                        )
                    }
                )
            }
        }
        compose.onNodeWithTag("speech-model-id").assertTextEquals("")
        compose.runOnIdle { apiKey = "my-api-key" }
        firstIds.forEach { (nextEngine, first) ->
            compose.runOnIdle { engine = nextEngine; modelId = null }
            compose.waitUntil(5_000) { modelId == first }
            compose.onNodeWithTag("speech-model-id").assertTextEquals(first)
        }
    }

    @Test fun doesNotReplaceSavedOrManuallyEnteredModels() {
        var modelId by mutableStateOf<String?>("saved-model")
        var apiKey by mutableStateOf("first-key")
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val first = CompletableDeferred<List<SpeechSynthesisModel>>()
        val second = CompletableDeferred<List<SpeechSynthesisModel>>()
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("existing", "Voice", engine = SpeechEngine.CARTESIA,
                        apiKey = apiKey, modelId = modelId),
                    onChange = { modelId = it },
                    fetchModels = { service ->
                        if (service.apiKey == "first-key") {
                            firstStarted.complete(Unit)
                            first.await()
                        } else {
                            secondStarted.complete(Unit)
                            second.await()
                        }
                    }
                )
            }
        }
        compose.waitUntil(5_000) { firstStarted.isCompleted }
        compose.runOnIdle { first.complete(listOf(SpeechSynthesisModel("first-from-api", "First"))) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("saved-model", modelId) }

        // A key change clears the previous account's choice in SpeechEditor.
        compose.runOnIdle { modelId = null; apiKey = "second-key" }
        compose.waitUntil(5_000) { secondStarted.isCompleted }
        compose.onNodeWithTag("speech-model-id").performTextReplacement("manual-model")
        compose.runOnIdle { second.complete(listOf(SpeechSynthesisModel("second-from-api", "Second"))) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("manual-model", modelId) }
    }

    @Test fun aChangedApiKeyIgnoresThePreviousCatalogResult() {
        var apiKey by mutableStateOf("first-key")
        var modelId by mutableStateOf<String?>(null)
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstResult = CompletableDeferred<List<SpeechSynthesisModel>>()
        val secondResult = CompletableDeferred<List<SpeechSynthesisModel>>()
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("new", "Voice", engine = SpeechEngine.FISH_AUDIO,
                        apiKey = apiKey, modelId = modelId),
                    onChange = { modelId = it },
                    fetchModels = { service ->
                        if (service.apiKey == "first-key") {
                            firstStarted.complete(Unit)
                            firstResult.await()
                        } else {
                            secondStarted.complete(Unit)
                            secondResult.await()
                        }
                    }
                )
            }
        }
        compose.waitUntil(5_000) { firstStarted.isCompleted }
        compose.runOnIdle { apiKey = "second-key" }
        compose.waitUntil(5_000) { secondStarted.isCompleted }
        compose.runOnIdle {
            firstResult.complete(listOf(SpeechSynthesisModel("stale-model", "Stale")))
            secondResult.complete(listOf(SpeechSynthesisModel("new-model", "New")))
        }
        compose.waitUntil(5_000) { modelId == "new-model" }
        compose.onNodeWithTag("speech-model-id").assertTextEquals("new-model")
    }
}
