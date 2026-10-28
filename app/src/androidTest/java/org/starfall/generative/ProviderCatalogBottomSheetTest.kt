package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.features.providers.editor.ui.ProviderModelCatalogSheet
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.DiscoveredModel
import org.starfall.generative.shared.model.provider.ModelProvider

@RunWith(AndroidJUnit4::class)
class ProviderCatalogBottomSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogFetchFailureShowsErrorContainerAndCopyAction() {
        val failure = "Catalog fetch failed"
        compose.setContent { MaterialTheme {
            ProviderModelCatalogSheet(
                provider = ModelProvider("catalog", "Catalog", ModelApiType.OPENAI, baseUrl = "https://example.test"),
                selectedModels = emptySet(),
                onFetchModels = { throw IllegalStateException(failure) },
                onCatalogLoaded = {}, onToggle = {}, onSetSelection = { _, _ -> }, onDismiss = {}
            )
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("provider-model-catalog-error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(failure).assertIsDisplayed()
        compose.onNodeWithTag("provider-model-catalog-copy-error").assertIsDisplayed().performClick()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test fun smallCatalogShrinksAfterStartingAtSeventyEightPercent() {
        val fetched = CompletableDeferred<List<DiscoveredModel>>()
        compose.setContent { MaterialTheme {
            ProviderModelCatalogSheet(
                provider = ModelProvider("catalog", "Catalog", ModelApiType.OPENAI, baseUrl = "https://example.test"),
                selectedModels = emptySet(),
                onFetchModels = { fetched.await() },
                onCatalogLoaded = {}, onToggle = {}, onSetSelection = { _, _ -> }, onDismiss = {}
            )
        } }
        compose.onNodeWithTag("bottom-sheet-window").assertIsDisplayed()
        val loadingHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        fetched.complete(listOf(DiscoveredModel("one", displayName = "Only model")))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Only model").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val loadedHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Short results must shrink the sheet from its initial 78%", loadedHeight < loadingHeight - 20f)
        compose.onNodeWithText("Only model").assertIsDisplayed()
        compose.onNodeWithTag("provider_model_catalog_search_button").assertIsDisplayed()
    }

    @Test fun largeCatalogExpandsToMaximumAfterLoading() {
        val fetched = CompletableDeferred<List<DiscoveredModel>>()
        compose.setContent { MaterialTheme {
            ProviderModelCatalogSheet(
                provider = ModelProvider("catalog", "Catalog", ModelApiType.OPENAI, baseUrl = "https://example.test"),
                selectedModels = emptySet(),
                onFetchModels = { fetched.await() },
                onCatalogLoaded = {}, onToggle = {}, onSetSelection = { _, _ -> }, onDismiss = {}
            )
        } }
        compose.onNodeWithTag("bottom-sheet-window").assertIsDisplayed()
        val loadingHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        fetched.complete((0 until 80).map { DiscoveredModel("model-$it", displayName = "Model $it") })
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Model 0").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val loadedHeight = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Long results must grow the sheet above its initial 78%", loadedHeight > loadingHeight + 20f)
        val list = compose.onNodeWithTag("provider-model-catalog-list").assertIsDisplayed()
        list.performScrollToIndex(79)
        list.performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        val sheetBounds = compose.onNodeWithTag("bottom-sheet-window").fetchSemanticsNode().boundsInRoot
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        assertEquals("Catalog list must extend to the sheet's bottom edge",
            sheetBounds.bottom, listBounds.bottom, 2f)
        val lastModel = compose.onNodeWithText("Model 79").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val bottomGap = with(compose.density) { 16.dp.toPx() }
        assertTrue("Last catalog model must have safe bottom spacing",
            lastModel.bottom <= listBounds.bottom - bottomGap + 2f)
    }

    @Test fun catalogOpensInBottomSheetWithTopToolbarAndOnDemandSearch() {
        var selected by mutableStateOf(emptySet<String>())
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                ProviderModelCatalogSheet(
                    provider = ModelProvider("catalog", "Catalog", ModelApiType.OPENAI, baseUrl = "https://example.test"),
                    selectedModels = selected,
                    onFetchModels = {
                        refreshes++
                        listOf(DiscoveredModel("alpha", displayName = "Alpha model"),
                            DiscoveredModel("beta", displayName = "Beta model"))
                    },
                    onCatalogLoaded = {},
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    onSetSelection = { ids, enabled ->
                        selected = if (enabled) selected + ids else selected - ids.toSet()
                    },
                    onDismiss = {}
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha model").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-bottom-sheet").assertIsDisplayed()
        compose.onNodeWithTag("bottom-sheet-drag-handle").assertExists()
        val toolbar = compose.onNodeWithTag("provider-model-catalog-toolbar")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("provider-model-catalog-list")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Catalog toolbar must be above the model list", toolbar.bottom <= list.top)
        compose.onNodeWithTag("provider_model_catalog_search_button").assertIsDisplayed()
        compose.onNodeWithTag("provider_model_catalog_search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Select all visible models").performClick()
        compose.runOnIdle { assertEquals(setOf("alpha", "beta"), selected) }
        compose.onNodeWithContentDescription("Deselect all visible models").performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), selected) }

        compose.onNodeWithTag("provider_model_catalog_search_button").performClick()
        compose.onNodeWithTag("provider_model_catalog_search").assertIsDisplayed()
        compose.onNodeWithTag("provider_model_catalog_search").performTextInput("alpha")
        compose.onNodeWithText("Alpha model").assertIsDisplayed()
        compose.onNodeWithText("Beta model").assertDoesNotExist()
        // Dismissing the IME must collapse the editor, not reset the search filter.
        compose.onNodeWithTag("provider_model_catalog_search").performImeAction()
        compose.onNodeWithTag("provider_model_catalog_search_button").assertIsDisplayed()
        compose.onNodeWithText("Alpha model").assertIsDisplayed()
        compose.onNodeWithText("Beta model").assertDoesNotExist()
        compose.onNodeWithTag("provider_model_catalog_search_button").performClick()
        compose.onNodeWithTag("provider_model_catalog_search").assertTextContains("alpha")
        compose.onNodeWithText("Beta model").assertDoesNotExist()
        compose.onNodeWithTag("provider_model_catalog_search").performTextClearance()
        compose.onNodeWithText("Beta model").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh models").performClick()
        compose.waitUntil(5_000) { refreshes >= 2 }
    }
}
