package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.tools.ui.MusicToolSettings
import org.starfall.generative.features.tools.ui.MediaRequestProfileEditor
import org.starfall.generative.features.tools.ui.MediaRequestChainSettings
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.media.*

class MusicProfilesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun profile() = MediaRequestProfile("music", "My track", chain = MediaRequestChain(
        steps = listOf(MediaRequestStep(endpoint = "https://music.example/compose")),
        finalResponse = MediaFinalResponse(type = MediaResponseType.AUDIO)))

    @Test fun toolHasOneProfileAndToggleWithoutModelOrModeSelectors() {
        var config by mutableStateOf(SystemToolConfig(musicRequestProfiles = mapOf("music" to profile())))
        var opened = false
        compose.setContent { MaterialTheme { MusicToolSettings(config, { config = it }, { opened = true }) } }
        compose.onNodeWithTag("music-profile-picker").performClick()
        compose.onNodeWithText("My track").performClick()
        compose.runOnIdle { assertEquals("music", config.activeMusicRequestProfileId) }
        compose.onNodeWithTag("music-enabled").performClick()
        compose.runOnIdle { assertTrue(config.enabled) }
        compose.onNodeWithContentDescription("Music Profiles").performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText("Default").assertDoesNotExist()
        compose.onNodeWithText("API Type").assertDoesNotExist()
        compose.onNodeWithText("Select model").assertDoesNotExist()
    }
    @Test fun musicEditorStartsAtCustomChainAndAutosavesWithoutGlobalSave() {
        var saved: MediaRequestProfile? = null
        compose.setContent { MaterialTheme {
            MediaRequestProfileEditor(provider = null, profile = profile(), video = false, kind = MediaKind.MUSIC,
                onAutoSave = { saved = it }, onBack = {})
        } }
        compose.onNodeWithText("Default").assertDoesNotExist()
        compose.onNodeWithText("Custom").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithTag("media-profile-name-field").performTextReplacement("New track")
        compose.waitUntil(5000) { saved?.name == "New track" }
        compose.runOnIdle { assertEquals(MediaGenerationMode.CUSTOM, saved!!.mode) }
    }
    @Test fun emptyMusicLibraryHasNoBuiltInDefault() {
        compose.setContent { MaterialTheme {
            MediaRequestChainSettings(provider = null, config = SystemToolConfig(), video = false, kind = MediaKind.MUSIC,
                onAutoSave = { _, _ -> }, onOpenProfile = {}, onAddProfile = {}, onBack = {})
        } }
        compose.onNodeWithText("Music Profiles").assertIsDisplayed()
        compose.onNodeWithText("Default").assertDoesNotExist()
    }
}
