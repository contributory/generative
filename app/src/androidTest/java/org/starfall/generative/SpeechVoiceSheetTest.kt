package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.speech.ui.SpeechVoiceSheet
import org.starfall.generative.integrations.speech.SpeechVoice
import org.starfall.generative.integrations.speech.VoicePage
import org.starfall.generative.shared.model.SpeechEngine
import org.starfall.generative.shared.model.SpeechService

class SpeechVoiceSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun allEnginesSearchPageSelectIdsAndPreserveManualSelectionOnFailure() {
        var service by mutableStateOf(SpeechService("s", "Voice", engine = SpeechEngine.ELEVENLABS, voice = "saved-id"))
        var chosen: SpeechVoice? = null
        compose.setContent { MaterialTheme {
            SpeechVoiceSheet(service, { chosen = it }, {}, fetchVoices = { current, query, cursor, _ ->
                if (query == "fail") error("Offline")
                val id = "${current.engine.name}-${cursor ?: "first"}"
                VoicePage(listOf(SpeechVoice(id, "$query Voice $id")), if (cursor == null) "next" else null)
            })
        } }
        for (engine in SpeechEngine.entries.filter { it.standalone }) {
            compose.runOnIdle { service = service.copy(engine = engine); chosen = null }
            val first = " Voice ${engine.name}-first"
            compose.waitUntil(5000) { compose.onAllNodesWithText(first).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Load more").performClick()
            compose.onNodeWithText(" Voice ${engine.name}-next").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("${engine.name}-next", chosen!!.id) }
            compose.onNode(hasSetTextAction()).performTextReplacement("warm")
            compose.waitUntil(5000) { compose.onAllNodesWithText("warm Voice ${engine.name}-first").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction()).performTextReplacement("fail")
            compose.waitUntil(5000) { compose.onAllNodesWithText("Offline").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals("saved-id", service.voice) }
        }
    }
}
