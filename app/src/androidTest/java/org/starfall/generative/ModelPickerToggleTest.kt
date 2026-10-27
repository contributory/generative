package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.shared.model.*
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderConfiguration
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.model.ModelPickerSheet

class ModelPickerToggleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectedModelHidesOnCollapseAndReasoningCanBeDisabledAfterExpansion() {
        val config = ModelConfiguration(supportsThinking = true)
        val provider = ModelProvider("p", "Primary", ModelApiType.OPENAI, baseUrl = "",
            groupId = "folder", config = ProviderConfiguration(modelIds = listOf("alpha", "beta"),
                modelConfigs = mapOf("alpha" to config)))
        var effort: String? = "high"
        compose.setContent {
            var currentEffort by remember { mutableStateOf(effort) }
            MaterialTheme {
                ModelPickerSheet(providers = listOf(provider), providerGroups = listOf(ProviderGroup("folder", "Work")),
                    collapsedGroupIdsState = setOf("folder"), collapsedProviderIdsState = setOf("p"),
                    selectedProviderId = "p", selectedModelId = "alpha", conversationReasoningEffort = currentEffort,
                    onSelectModel = { _, _ -> }, onSetReasoningEffort = { effort = it; currentEffort = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("model-picker-provider_p").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-model_p_alpha").assertDoesNotExist()
        compose.onNodeWithTag("model-picker-model_p_beta").assertDoesNotExist()
        compose.onNodeWithTag("reasoning-slider").assertDoesNotExist()
        compose.onNodeWithTag("model-picker-provider_p").performClick()
        compose.onNodeWithTag("model-picker-model_p_alpha").assertIsDisplayed()
        compose.onNodeWithTag("reasoning-slider").assertIsEnabled()
        compose.onNodeWithTag("reasoning-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
            it(0f)
        }
        compose.runOnIdle { assertEquals("none", effort); assertTrue(config.supportsThinking) }
        compose.onNodeWithTag("reasoning-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
            it(4f)
        }
        compose.runOnIdle { assertEquals("high", effort) }
        compose.onNodeWithTag("reasoning-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
            it(1f)
        }
        compose.runOnIdle { assertEquals(null, effort) }
        compose.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test fun folderTabsScrollTheSameListAndSearchFiltersSections() {
        val group = ProviderGroup("folder", "Work", 0)
        fun provider(id: String, groupId: String?, models: List<String>) = ModelProvider(
            id = id, name = "Provider $id", type = ModelApiType.OPENAI, baseUrl = "",
            groupId = groupId, config = ProviderConfiguration(modelIds = models)
        )
        compose.setContent {
            MaterialTheme {
                ModelPickerSheet(
                    providers = listOf(provider("grouped", group.id, (0..12).map { "alpha-$it" }),
                        provider("root", null, listOf("gamma"))),
                    providerGroups = listOf(group), selectedProviderId = "", selectedModelId = "",
                    conversationReasoningEffort = null, onSelectModel = { _, _ -> },
                    onSetReasoningEffort = {}, onDismiss = {}
                )
            }
        }
        compose.onNodeWithTag("model-picker-tab_default").assertDoesNotExist()
        compose.onNodeWithTag("model-picker-tab_folder_folder").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-tab_ungrouped").performClick()
        compose.onNodeWithTag("model-picker-group_ungrouped").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-model_root_gamma").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-tab_folder_folder").performClick()
        compose.onNodeWithTag("model-picker-group_folder").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-provider_grouped").performClick()
        compose.onNodeWithTag("model-picker-model_grouped_alpha-0").assertDoesNotExist()
        compose.onNodeWithTag("model-picker-tab_ungrouped").performClick()
        compose.onNodeWithTag("model-picker-tab_folder_folder").performClick()
        compose.onNodeWithTag("model-picker-model_grouped_alpha-0").assertDoesNotExist()
        compose.onNodeWithTag("model-picker-provider_grouped").performClick()
        compose.onNodeWithTag("model-picker-model_grouped_alpha-0").assertIsDisplayed()

        compose.onNodeWithTag("model-picker-search-button").performClick()
        compose.onNodeWithTag("model-picker-search").performTextInput("gamma")
        compose.onNodeWithTag("model-picker-tab_folder_folder").assertDoesNotExist()
        compose.onNodeWithTag("model-picker-tab_ungrouped").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-model_root_gamma").assertIsDisplayed()
    }

    @Test fun searchToolbarStaysAtTheBottomWhenFocusChanges() {
        val provider = ModelProvider("p", "Primary", ModelApiType.OPENAI, baseUrl = "",
            config = ProviderConfiguration(modelIds = listOf("alpha", "beta")))
        lateinit var focusManager: androidx.compose.ui.focus.FocusManager
        compose.setContent {
            focusManager = LocalFocusManager.current
            MaterialTheme {
                ModelPickerSheet(
                    providers = listOf(provider), selectedProviderId = "", selectedModelId = "",
                    conversationReasoningEffort = null, onSelectModel = { _, _ -> },
                    onSetReasoningEffort = {}, onDismiss = {}
                )
            }
        }
        fun assertFooterBelowList() {
            val listBottom = compose.onNodeWithTag("model-picker-list").getUnclippedBoundsInRoot().bottom
            val footerTop = compose.onNodeWithTag("model-picker-toolbar").getUnclippedBoundsInRoot().top
            assertTrue("Search toolbar must be below the scrollable model list", footerTop >= listBottom)
        }
        assertFooterBelowList()
        compose.onNodeWithTag("model-picker-search-button").performClick()
        compose.onNodeWithTag("model-picker-search").assertIsFocused().performTextInput("alpha")
        assertFooterBelowList()
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        // Focus may leave before the system finishes animating the IME closed.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("model-picker-search-button").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("model-picker-search-button").assertIsDisplayed()
        assertFooterBelowList()
    }

    @Test fun searchLosesFocusOnOutsideTapAndImeAction() {
        val provider = ModelProvider("p", "Primary", ModelApiType.OPENAI, baseUrl = "",
            config = ProviderConfiguration(modelIds = listOf("alpha", "beta")))
        compose.setContent {
            MaterialTheme {
                ModelPickerSheet(
                    providers = listOf(provider), selectedProviderId = "", selectedModelId = "",
                    conversationReasoningEffort = null, onSelectModel = { _, _ -> },
                    onSetReasoningEffort = {}, onDismiss = {}
                )
            }
        }

        compose.onNodeWithTag("model-picker-search-button").performClick()
        compose.onNodeWithTag("model-picker-search").assertIsFocused().performTextInput("alpha")
        compose.onNodeWithTag("model-picker-provider_p").performClick()
        compose.onNodeWithTag("model-picker-search-button").assertIsDisplayed()

        compose.onNodeWithTag("model-picker-search-button").performClick()
        compose.onNodeWithTag("model-picker-search").assertIsFocused().performImeAction()
        compose.onNodeWithTag("model-picker-search-button").assertIsDisplayed()
        compose.onNodeWithTag("model-picker-model_p_alpha").assertIsDisplayed()
    }

    @Test fun togglingBranchesKeepsTheViewportInsteadOfReturningToTheSelectedModel() {
        val group = ProviderGroup("folder", "Work")
        fun provider(id: String, groupId: String? = null, models: List<String> = listOf("model-$id")) =
            ModelProvider(id, "Provider $id", ModelApiType.OPENAI, baseUrl = "", groupId = groupId,
                config = ProviderConfiguration(modelIds = models))
        val providers = listOf(provider("first", group.id, (0..29).map { "model-$it" })) +
            (0..19).map { provider("middle-$it") } + provider("selected")
        compose.setContent { MaterialTheme {
            ModelPickerSheet(providers = providers, providerGroups = listOf(group),
                selectedProviderId = "selected", selectedModelId = "model-selected",
                conversationReasoningEffort = null, onSelectModel = { _, _ -> }, onSetReasoningEffort = {}, onDismiss = {})
        } }
        compose.onNodeWithTag("model-picker-model_selected_model-selected").assertIsDisplayed()
        val list = compose.onNodeWithTag("model-picker-list")
        list.performScrollToNode(hasTestTag("model-picker-provider_first"))
        repeat(2) {
            compose.onNodeWithTag("model-picker-provider_first").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("model-picker-provider_first").assertIsDisplayed()
        }
    }

    @Test fun sheetHeightStaysFixedWhenProvidersCollapseAndFolderTabChanges() {
        val group = ProviderGroup("folder", "Work")
        fun provider(id: String, groupId: String? = null, models: List<String> = listOf("model-$id")) =
            ModelProvider(
                id = id,
                name = "Provider $id",
                type = ModelApiType.OPENAI,
                baseUrl = "",
                groupId = groupId,
                config = ProviderConfiguration(modelIds = models)
            )

        val providers = listOf(
            provider("grouped", group.id, (0..12).map { "model-$it" }),
            provider("root", null, (0..12).map { "root-model-$it" })
        )

        compose.setContent {
            MaterialTheme {
                ModelPickerSheet(
                    providers = providers,
                    providerGroups = listOf(group),
                    selectedProviderId = "grouped",
                    selectedModelId = "model-0",
                    conversationReasoningEffort = null,
                    onSelectModel = { _, _ -> },
                    onSetReasoningEffort = {},
                    onDismiss = {}
                )
            }
        }

        val sheet = compose.onNodeWithTag("bottom-sheet-window")
        val initialHeight = sheet.fetchSemanticsNode().boundsInRoot.height

        compose.onNodeWithTag("model-picker-provider_grouped").performClick()
        compose.waitForIdle()
        assertEquals(initialHeight, sheet.fetchSemanticsNode().boundsInRoot.height, 1f)

        compose.onNodeWithTag("model-picker-provider_grouped").performClick()
        compose.waitForIdle()
        assertEquals(initialHeight, sheet.fetchSemanticsNode().boundsInRoot.height, 1f)

        compose.onNodeWithTag("model-picker-tab_folder_folder").performClick()
        compose.waitForIdle()
        assertEquals(initialHeight, sheet.fetchSemanticsNode().boundsInRoot.height, 1f)

        compose.onNodeWithTag("model-picker-tab_ungrouped").performClick()
        compose.waitForIdle()
        assertEquals(initialHeight, sheet.fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    @Test fun lateDynamicDiscoveryAppearsWithoutReopeningThePicker() {
        val models = mutableStateMapOf<String, List<String>>()
        compose.setContent { MaterialTheme {
            ModelPickerSheet(providers = listOf(ModelProvider("local", "Local", ModelApiType.OLLAMA, baseUrl = "")),
                selectedProviderId = "", selectedModelId = "", dynamicModelsMap = models.toMap(),
                conversationReasoningEffort = null, onSelectModel = { _, _ -> }, onSetReasoningEffort = {}, onDismiss = {})
        } }
        compose.runOnIdle { models["local"] = listOf("discovered-model") }
        compose.onNodeWithTag("model-picker-model_local_discovered-model").assertIsDisplayed()
    }

}
