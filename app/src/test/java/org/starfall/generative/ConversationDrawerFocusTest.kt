package org.starfall.generative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.drawer.ui.ConversationSideDrawer
import org.starfall.generative.shared.model.SidebarOrganization
import org.starfall.generative.shared.theme.GenerativeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w420dp-h900dp-xhdpi", application = LibraryTestApplication::class)
class ConversationDrawerFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun openingDrawerShowsSearchButtonAndOnlyOpensSearchAfterTap() {
        var isOpen by mutableStateOf(false)

        compose.setContent {
            GenerativeTheme(themeMode = "LIGHT", amoledMode = false, dynamicColor = false) {
                ConversationSideDrawer(
                    conversations = emptyList(),
                    currentConversationId = null,
                    isOpen = isOpen,
                    organization = SidebarOrganization(),
                    onUpdateOrganization = {},
                    onDeleteConversations = {},
                    onSelectConversation = {},
                    onNewChat = {},
                    onRenameConversation = { _, _ -> },
                    onDeleteConversation = {},
                    onNavigateToSettings = {},
                    onCloseDrawer = {}
                )
            }
        }

        isOpen = true
        compose.waitForIdle()
        // At rest, Search, Menu and New chat share the same 52dp height; Search and New chat are 52dp squares.
        compose.onNodeWithTag("conversation_drawer_search_button")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(52.dp)
            .assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("conversation_drawer_menu_button")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("conversation_drawer_new_chat")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(52.dp)
            .assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("conversation_drawer_search").assertDoesNotExist()

        compose.onNodeWithTag("conversation_drawer_search_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("conversation_drawer_search").assertIsFocused()
        compose.onNodeWithTag("conversation_drawer_search_button").assertDoesNotExist()
        compose.onNodeWithTag("conversation_drawer_menu_button").assertDoesNotExist()
        compose.onNodeWithTag("conversation_drawer_new_chat")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(42.dp)
    }
}
