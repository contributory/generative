package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.providers.list.logic.ProviderSearchField
import org.starfall.generative.features.providers.list.ui.ProviderScreenTopBar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w400dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ProviderSearchBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchExpandsOnTapAndCollapsesOnUnfocusWithoutLosingQuery() {
        var searching by mutableStateOf(false)
        var query by mutableStateOf("")
        lateinit var focus: FocusManager
        compose.setContent {
            val manager = LocalFocusManager.current
            SideEffect { focus = manager }
            MaterialTheme {
                ProviderScreenTopBar(
                    isGridView = false,
                    searching = searching,
                    searchQuery = query,
                    searchFields = setOf(ProviderSearchField.PROVIDER_NAME),
                    searchFilterOpen = false,
                    searchFocus = remember { FocusRequester() },
                    onSearchQueryChange = { query = it },
                    onSearchFieldsChange = {},
                    onSearchFilterOpenChange = {},
                    onBack = {}, onToggleGridView = {}, onAddProvider = {}, onCreateGroup = {},
                    onOpenSearch = { searching = true },
                    onCloseSearch = { searching = false }
                )
            }
        }
        compose.onNodeWithTag("provider_search").assertDoesNotExist()
        compose.onNodeWithTag("provider_search_expand").performClick()
        compose.onNodeWithTag("provider_search").performTextInput("Alpha")
        compose.runOnIdle { focus.clearFocus(force = true) }
        compose.onNodeWithTag("provider_search").assertDoesNotExist()
        compose.onNodeWithTag("provider_search_expand").performClick()
        compose.onNodeWithTag("provider_search").assertTextContains("Alpha")
        compose.onNodeWithTag("provider_search_clear").performClick()
        compose.runOnIdle { assertEquals("", query) }
    }
}
