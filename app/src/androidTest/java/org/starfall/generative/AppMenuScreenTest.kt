package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.starfall.generative.navigation.RetainedNavHost
import org.starfall.generative.features.home.chat.ui.ChatAppBar

class AppMenuScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
                                onThemeChange = {}, onAmoledChange = {},
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
                        onNavigateToCustomization = { destinations.add("Customization") },
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

    /** Real back-stack transitions with the same chat pop callback as AppNavigation. */
    private fun showMenuOverChat(): NavHostController {
        lateinit var navigation: NavHostController
        compose.setContent {
            MaterialTheme {
                navigation = rememberNavController()
                RetainedNavHost(navigation, startDestination = "chat") {
                    composable("chat") {
                        Box(Modifier.fillMaxSize().testTag("chat-destination")) { Text("Existing chat") }
                    }
                    composable("features") {
                        AppMenuScreen(onNavigateToChat = {
                            navigation.popBackStack("chat", inclusive = false)
                        })
                    }
                }
            }
        }
        compose.runOnIdle { navigation.navigate("features") }
        compose.onNodeWithTag("app-menu-root").assertIsDisplayed()
        return navigation
    }

    @Test fun backIconPopsMenuToExistingChat() {
        val navigation = showMenuOverChat()
        val back = compose.onNodeWithTag("app-menu-back").assertIsDisplayed()
        back.assertHasClickAction()
        val backBounds = back.fetchSemanticsNode().boundsInRoot
        val menuBounds = compose.onNodeWithTag("app-menu-root").fetchSemanticsNode().boundsInRoot
        assertTrue("Back icon must be in the menu's upper-left corner",
            backBounds.left < menuBounds.width * 0.2f && backBounds.top < menuBounds.height * 0.2f)
        back.performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat-destination").assertIsDisplayed()
        compose.runOnIdle { assertEquals("chat", navigation.currentDestination?.route) }
    }

    @Test fun rightSwipePopsMenuButVerticalLeftwardAndShortSwipesDoNot() {
        val navigation = showMenuOverChat()
        val menu = compose.onNodeWithTag("app-menu-root").assertIsDisplayed()

        // Vertical list scrolling and opposite-direction swipes must not navigate.
        menu.performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.7f), Offset(width * 0.5f, height * 0.3f), 250)
        }
        menu.performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.5f), Offset(width * 0.2f, height * 0.5f), 250)
        }
        menu.performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.35f, height * 0.5f), 250)
        }
        compose.runOnIdle { assertEquals("features", navigation.currentDestination?.route) }

        // Deliberate horizontal movement to the right pops only after release.
        menu.performTouchInput {
            swipe(Offset(width * 0.25f, height * 0.45f), Offset(width * 0.8f, height * 0.45f), 300)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-destination").assertIsDisplayed()
        compose.runOnIdle { assertEquals("chat", navigation.currentDestination?.route) }
    }

    private fun scrollListTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    @Test fun featuresLandingGroupsAndRoutesEveryFeatureItem() {
        val destinations = mutableListOf<String>()
        showFeatures(destinations)
        val headings = listOf(
            R.string.common_configuration, R.string.settings_group_external_services,
            R.string.settings_group_data_caching, R.string.common_system
        )
        headings.forEach { id ->
            val label = context.getString(id)
            scrollListTo(label)
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        val items = listOf(
            R.string.customization_title to "Customization",
            R.string.settings_chat_options to "Chat Options",
            R.string.common_feature_tools to "Feature Tools",
            R.string.providers_title to "Model Providers",
            R.string.common_mcp_connectors to "MCP Connectors",
            R.string.speech_services_title to "Speech Services",
            R.string.remote_machines_title to "Remote Machines",
            R.string.settings_storage to "Media Storage",
            R.string.icon_settings_title to "Icons & Trademarks",
            R.string.settings_category_app_settings to "General Settings",
            R.string.settings_category_app_data to "Data Controls",
            R.string.settings_category_about to "About"
        )
        items.forEach { (labelId, _) ->
            val label = context.getString(labelId)
            scrollListTo(label)
            compose.onNodeWithText(label).performClick()
        }
        compose.runOnIdle { assertEquals(items.map { it.second }, destinations) }
    }

    @Test fun aboutContainsUpdatesAndBackReturnsToFeatures() {
        val destinations = mutableListOf<String>()
        showFeatures(destinations, showAboutPage = true)
        compose.onNodeWithText(context.getString(R.string.settings_software_update)).assertDoesNotExist()
        val about = context.getString(R.string.settings_category_about)
        scrollListTo(about)
        compose.onNodeWithText(about).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_software_update)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_check_for_updates)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_checking_releases)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_license)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.common_back)).performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), destinations) }
    }

    @Test fun preferenceRowTogglesOnce() {
        val updates = mutableListOf<Boolean>()
        showFeatures(autoScroll = { updates.add(it) }, showSettingsPage = true)
        val settings = context.getString(R.string.settings_category_app_settings)
        scrollListTo(settings)
        compose.onNodeWithText(settings).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_tab_preferences)).performClick()
        compose.onNodeWithText(context.getString(R.string.settings_auto_scroll)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true), updates) }
    }

    @Test fun chatToolbarOpensDrawerOnLeftAndSettingsOnRight() {
        var drawer = 0
        var settings = 0
        compose.setContent { MaterialTheme { ChatAppBar("Selected model", "Provider", { drawer++ }, {}, { settings++ }) } }
        val left = compose.onNodeWithContentDescription(context.getString(R.string.chat_open_side_navigation))
        val right = compose.onNodeWithContentDescription(context.getString(R.string.chat_open_app_menu))
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

        compose.onNodeWithContentDescription(context.getString(R.string.chat_open_temporary_chat))
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.sidebar_new_chat)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, temporaryOpened)
            assertEquals(0, regularOpened)
        }
    }
}
