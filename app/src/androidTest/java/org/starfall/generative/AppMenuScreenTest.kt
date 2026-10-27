package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.about.ui.AboutScreen
import org.starfall.generative.features.menu.ui.AppMenuScreen
import org.starfall.generative.features.settings.ui.AppSettings
import org.starfall.generative.features.settings.ui.MainAppbar
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.navigation.AppNavigation
import org.starfall.generative.features.home.chat.ui.ChatAppBar

class AppMenuScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Mirrors the AppNavigation settings destinations while allowing tests to capture navigation. */
    private fun showFeatures(
        destinations: MutableList<String> = mutableListOf(),
        autoScroll: (Boolean) -> Unit = {},
        showSettingsPage: Boolean = false,
        showAboutPage: Boolean = false
    ) {
        compose.setContent {
            MaterialTheme {
                var page by remember { mutableStateOf("features") }
                when (page) {
                    "AppSettings" -> Scaffold(
                        topBar = {
                            MainAppbar(
                                title = stringResource(R.string.settings_category_app_settings),
                                onBack = { page = "features" }
                            )
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            AppSettings(
                                appPreferences = AppPreferences(),
                                onThemeChange = {}, onAmoledChange = {}, onDynamicColorChange = {}, onColorSchemeChange = {},
                                onHideStatusBarChange = {}, onWordWrapModeChange = {}, onWordWrapColumnChange = {},
                                onCodePreviewEnabledChange = {}, onCodeBlockDisplayModeChange = {}, onCodeBlockMaxLinesChange = {},
                                onCodeBlockIncludeUndefinedChange = {}, onMessageFontSizeChange = {}, onMessageFontSizeCustomChange = {},
                                onMessageFontFamilyChange = {}, onContinueLastConversationChange = {}, onPersistSelectedModelChange = {},
                                onAutoScrollChange = autoScroll, onEnableVibrationChange = {}, onDebugModeChange = {}, onLatexModeChange = {},
                                onTtsReadCodeBlocksChange = {}, onToolRoundLimitEnabledChange = {}, onToolRoundLimitChange = {},
                                onToolCallStrategyChange = {}, onToolCallConcurrentLimitChange = {}, onScreenWakelockModeChange = {},
                                onAppLanguageChange = { _, _ -> }
                            )
                        }
                    }
                    "About" -> Scaffold(
                        topBar = {
                            MainAppbar(
                                title = stringResource(R.string.settings_category_about),
                                onBack = { page = "features" }
                            )
                        }
                    ) { padding ->
                        AboutScreen(
                            modifier = Modifier
                                .padding(padding)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    else -> AppMenuScreen(
                        onNavigateToChatOptions = { destinations.add("Chat Options") },
                        onNavigateToToolsEditor = { destinations.add("Feature Tools") },
                        onNavigateToProviders = { destinations.add("Model Providers") },
                        onNavigateToMcp = { destinations.add("MCP Connectors") },
                        onNavigateToSpeech = { destinations.add("Speech Services") },
                        onNavigateToRemoteMachines = { destinations.add("Remote Machines") },
                        onNavigateToStorage = { destinations.add("Media Storage") },
                        onNavigateToAppSettings = {
                            if (showSettingsPage) page = "AppSettings" else destinations.add("General Settings")
                        },
                        onNavigateToIcons = { destinations.add("Icons & Trademarks") },
                        onNavigateToAppData = { destinations.add("Data Controls") },
                        onNavigateToAbout = {
                            if (showAboutPage) page = "About" else destinations.add("About")
                        }
                    )
                }
            }
        }
    }

    private fun scrollListTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    @Test fun featuresLandingGroupsAndRoutesEveryFeatureItem() {
        val destinations = mutableListOf<String>()
        showFeatures(destinations)
        val headings = listOf("Configuration", "External Services", "Data & Caching", "System")
        headings.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Default & Tools").assertDoesNotExist()
        val items = listOf(
            "Chat Options", "Feature Tools", "Model Providers", "MCP Connectors", "Speech Services",
            "Remote Machines", "Media Storage", "Icons & Trademarks", "General Settings", "Data Controls", "About"
        )
        items.forEach { scrollListTo(it); compose.onNodeWithText(it).performClick() }
        compose.runOnIdle { assertEquals(items, destinations) }
    }

    @Test fun aboutContainsUpdatesAndBackReturnsToFeatures() {
        val destinations = mutableListOf<String>()
        showFeatures(destinations, showAboutPage = true)
        compose.onNodeWithText("Software Update").assertDoesNotExist()
        scrollListTo("About")
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Software Update").assertIsDisplayed()
        compose.onNodeWithText("Check for updates").assertIsDisplayed()
        compose.onNodeWithText("Checking GitHub Releases...").assertDoesNotExist()
        compose.onNodeWithText("License").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), destinations) }
    }

    @Test fun preferenceRowTogglesOnce() {
        val updates = mutableListOf<Boolean>()
        showFeatures(autoScroll = { updates.add(it) }, showSettingsPage = true)
        scrollListTo("General Settings")
        compose.onNodeWithText("General Settings").performClick()
        compose.onNodeWithText("Preferences").performClick()
        compose.onNodeWithText("Auto scroll").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true), updates) }
    }

    @Test fun chatToolbarOpensDrawerOnLeftAndSettingsOnRight() {
        var drawer = 0
        var settings = 0
        compose.setContent { MaterialTheme { ChatAppBar("Selected model", "Provider", { drawer++ }, {}, { settings++ }) } }
        val left = compose.onNodeWithContentDescription("Open side navigation")
        val right = compose.onNodeWithContentDescription("Settings")
        assertTrue(left.fetchSemanticsNode().boundsInRoot.left < right.fetchSemanticsNode().boundsInRoot.left)
        left.performClick()
        right.performClick()
        compose.runOnIdle { assertEquals(1, drawer); assertEquals(1, settings) }
    }

    @Test fun emptyChatToolbarShowsTemporaryChatAction() {
        var temporaryOpened = 0
        var regularOpened = 0
        compose.setContent {
            MaterialTheme {
                ChatAppBar("Selected model", "Provider", {}, { regularOpened++ }, {},
                    isTemporaryChat = true, onOpenTemporaryChat = { temporaryOpened++ })
            }
        }

        compose.onNodeWithContentDescription("Open temporary chat")
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithContentDescription("New chat").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, temporaryOpened)
            assertEquals(0, regularOpened)
        }
    }
}
