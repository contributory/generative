package org.starfall.generative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.home.drawer.ui.ConversationSideDrawer
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
        compose.onNodeWithTag("conversation_drawer_search_button")
            .assertIsDisplayed()
        compose.onNodeWithTag("conversation_drawer_search").assertDoesNotExist()

        compose.onNodeWithTag("conversation_drawer_search_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("conversation_drawer_search").assertIsFocused()
    }
}
