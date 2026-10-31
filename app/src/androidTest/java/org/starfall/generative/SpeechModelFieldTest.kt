package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.speech.ui.SpeechModelField
import org.starfall.generative.integrations.speech.SpeechSynthesisModel
import org.starfall.generative.shared.model.SpeechEngine
import org.starfall.generative.shared.model.SpeechService
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun savedModelFetchesOnlyWhenPickerIsOpenedAndReusesCatalog() {
        var modelId by mutableStateOf<String?>("saved-model")
        val requests = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("saved", "Voice", engine = SpeechEngine.ELEVENLABS,
                        apiKey = "saved-key", modelId = modelId),
                    onChange = { modelId = it },
                    fetchModels = {
                        requests.incrementAndGet()
                        listOf(SpeechSynthesisModel("catalog-first", "First from catalog"))
                    }
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, requests.get())
            assertEquals("saved-model", modelId)
        }
        compose.onNodeWithContentDescription("Refresh models").assertDoesNotExist()

        compose.onNodeWithTag("speech-model-dropdown").performClick()
        compose.waitUntil(5_000) { requests.get() == 1 }
        compose.onNodeWithText("First from catalog").assertExists()
        compose.onNodeWithTag("speech-model-option:catalog-first").assertExists()
        compose.onNodeWithTag("speech-model-menu").assertExists()
        compose.onNodeWithTag("app-bottom-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals("saved-model", modelId) }
        compose.onNodeWithText("First from catalog").performClick()
        compose.runOnIdle { assertEquals("catalog-first", modelId) }
        compose.onNodeWithTag("speech-model-dropdown").performClick()
        compose.onNodeWithTag("speech-model-option:catalog-first").assertExists()
        compose.onNodeWithContentDescription("Selected").assertExists()
        compose.runOnIdle { assertEquals(1, requests.get()) }
    }

    @Test fun allDedicatedEnginesUseExposedDropdownRatherThanBottomSheet() {
        val engine = mutableStateOf(SpeechEngine.ELEVENLABS)
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("dedicated", "Voice", engine = engine.value,
                        apiKey = "test-key", modelId = "saved-model"),
                    onChange = {},
                    fetchModels = { listOf(SpeechSynthesisModel("available-model", "Available")) }
                )
            }
        }
        SpeechEngine.entries.filter { it.standalone }.forEach { next ->
            compose.runOnIdle { engine.value = next }
            compose.onNodeWithTag("speech-model-dropdown").performClick()
            compose.onNodeWithTag("speech-model-menu").assertExists()
            compose.onNodeWithTag("app-bottom-sheet").assertDoesNotExist()
        }
    }

    @Test fun editableModelFieldAlsoOpensExposedDropdown() {
        var modelId by mutableStateOf<String?>("saved-model")
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("editable", "Voice", engine = SpeechEngine.SPEECHIFY,
                        apiKey = "test-key", modelId = modelId),
                    onChange = { modelId = it },
                    fetchModels = { listOf(SpeechSynthesisModel("catalog-model", "Catalog model")) }
                )
            }
        }
        compose.onNodeWithTag("speech-model-id").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("speech-model-option:catalog-model")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("speech-model-option:catalog-model").performClick()
        compose.runOnIdle { assertEquals("catalog-model", modelId) }
        compose.onNodeWithTag("speech-model-id").performTextReplacement("manual-model")
        compose.runOnIdle { assertEquals("manual-model", modelId) }
    }

    @Test fun emptyKeyShowsHelpfulMessageInsteadOfAnInfiniteSpinner() {
        var modelId by mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                SpeechModelField(
                    SpeechService("new", "Voice", engine = SpeechEngine.CARTESIA,
                        apiKey = "", modelId = modelId),
                    onChange = { modelId = it },
                    fetchModels = { error("Must not fetch without an API key") }
                )
            }
        }
        compose.onNodeWithTag("speech-model-dropdown").performClick()
        compose.onNodeWithTag("speech-model-menu").assertExists()
        compose.onNodeWithTag("app-bottom-sheet").assertDoesNotExist()
        compose.onNodeWithText(
            "Required by this speech engine. Stored securely on this device."
        ).assertExists()
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
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(firstStarted.isCompleted) }
        compose.onNodeWithTag("speech-model-dropdown").performClick()
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
