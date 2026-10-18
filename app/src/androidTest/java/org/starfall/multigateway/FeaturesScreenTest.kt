package org.starfall.multigateway

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
import org.starfall.multigateway.features.about.ui.AboutScreen
import org.starfall.multigateway.features.main.ui.FeaturesScreen
import org.starfall.multigateway.features.settings.ui.AppSettingsScreen
import org.starfall.multigateway.features.settings.ui.MainAppbar
import org.starfall.multigateway.shared.storage.preferences.AppPreferences
import org.starfall.multigateway.navigation.AppNavigation
import org.starfall.multigateway.home.chat.ui.ChatAppBar

class FeaturesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Mirrors the AppNavigation settings destinations: Features landing plus App Settings / About pages. */
    private fun showFeatures(destinations: MutableList<String> = mutableListOf(), autoScroll: (Boolean) -> Unit = {}) {
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
                            AppSettingsScreen(
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
                    else -> FeaturesScreen(
                        onNavigateToDefaultModels = { destinations.add("Default Models") },
                        onNavigateToProviders = { destinations.add("Providers") },
                        onNavigateToMcp = { destinations.add("MCP Manage") },
                        onNavigateToSpeech = { destinations.add("Speech Services") },
                        onNavigateToStorage = { destinations.add("Storage") },
                        onNavigateToAppSettings = { page = "AppSettings" },
                        onNavigateToAbout = { page = "About" }
                    )
                }
            }
        }
    }

    private fun scrollListTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    @Test fun featuresLandingRoutesEveryConfigurationItem() {
        val destinations = mutableListOf<String>()
        showFeatures(destinations)
        compose.onNodeWithText("Configuration").assertIsDisplayed()
        val items = listOf("Default & Tools", "Providers", "MCP Manage", "Speech Services", "Storage")
        items.forEach { scrollListTo(it); compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("System Settings").performScrollTo().assertIsDisplayed()
        val expected = listOf("Default Models", "Providers", "MCP Manage", "Speech Services", "Storage")
        compose.runOnIdle { assertEquals(expected, destinations) }
    }

    @Test fun aboutContainsUpdatesAndBackReturnsToFeatures() {
        val destinations = mutableListOf<String>()
        showFeatures(destinations)
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
        showFeatures(autoScroll = { updates.add(it) })
        scrollListTo("App Settings")
        compose.onNodeWithText("App Settings").performClick()
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
}
