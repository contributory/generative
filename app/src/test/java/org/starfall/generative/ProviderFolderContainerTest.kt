package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderPlacement
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.features.providers.list.ui.ProvidersScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h1000dp-xhdpi")
class ProviderFolderContainerTest {
    @get:Rule val compose = createComposeRule()

    private val group = ProviderGroup("g", "Folder", sortOrder = 1)
    private val providers = listOf(
        ModelProvider("root", "Root", ModelApiType.OPENAI, baseUrl = "", sortOrder = 0),
        ModelProvider("member-a", "Member A", ModelApiType.OPENAI, baseUrl = "", groupId = "g", sortOrder = 0),
        ModelProvider("member-b", "Member B", ModelApiType.OPENAI, baseUrl = "", groupId = "g", sortOrder = 1),
        ModelProvider("tail", "Tail", ModelApiType.OPENAI, baseUrl = "", sortOrder = 2)
    )

    private fun showFolder(
        source: List<ModelProvider> = providers,
        writes: MutableList<ProviderPlacement> = mutableListOf(),
        groups: List<ProviderGroup> = listOf(group)
    ) {
        compose.setContent {
            MaterialTheme {
                var editorOpened by remember { mutableStateOf(false) }
                var gridView by remember { mutableStateOf(true) }
                if (editorOpened) {
                    Text("Provider editor", modifier = androidx.compose.ui.Modifier.testTag("provider_editor"))
                } else {
                    ProvidersScreen(
                        providers = source,
                        providerGroups = groups,
                        isGridView = gridView,
                        onToggleGridView = { gridView = it },
                        onOpenProviderEditor = { _, _ -> editorOpened = true },
                        onDeleteProvider = {},
                        onReorderProviders = {},
                        onBack = {},
                        onPlaceProvider = { placement ->
                            writes += placement
                            Result.success(Unit)
                        }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun folderIsAStandaloneRootTileAndDoesNotExpandIntoTheMainGrid() {
        showFolder()

        compose.onNodeWithTag("provider_group_g", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_group_icon_g", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_member-a", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("provider_member-b", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("provider_root", useUnmergedTree = true).assertExists()
    }

    @Test
    fun clickingFolderOpensFullScreenPageWithItsOwnHeaderAndToolbar() {
        showFolder()

        compose.onNodeWithTag("provider_group_g", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("provider_folder_screen_g", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_folder_provider_member-a", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_folder_provider_member-b", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_folder_header_g", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_folder_header_icon_g", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_folder_header_name_g", useUnmergedTree = true).assertTextEquals("Folder")
        compose.onNodeWithTag("provider_folder_toolbar_g", useUnmergedTree = true).assertExists()
        val menuLeft = compose.onNodeWithContentDescription("More options", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        val addLeft = compose.onNodeWithTag("add_folder_provider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        assertTrue("Folder menu must be left of Add", menuLeft < addLeft)
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        val headerBottom = compose.onNodeWithTag("provider_folder_header_g").fetchSemanticsNode().boundsInRoot.bottom
        val gridTop = compose.onNodeWithTag("provider_folder_grid_g").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Provider list must start below the folder's own header", gridTop >= headerBottom)
    }

    @Test
    fun searchShowsMatchingMembersDirectlyWithoutFolders() {
        showFolder()
        compose.onNodeWithTag("provider_search_expand").performClick()
        compose.onNodeWithTag("provider_search").performTextInput("Member")
        compose.onNodeWithTag("provider_group_g").assertDoesNotExist()
        compose.onNodeWithTag("provider_member-a").assertExists()
        compose.onNodeWithTag("provider_member-b").assertExists()
        compose.onNodeWithTag("provider_root").assertDoesNotExist()
    }

    @Test
    fun commonAddButtonCreatesAProviderForTheOpenFolder() {
        showFolder()
        compose.onNodeWithTag("provider_group_g").performClick()
        compose.onNodeWithTag("add_folder_provider", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("provider_folder_screen_g").assertDoesNotExist()
    }

    @Test
    fun folderToolbarSwitchesBetweenGridAndListWithoutClosingTheFolder() {
        showFolder()
        compose.onNodeWithTag("provider_group_g").performClick()
        compose.onNodeWithContentDescription("Switch to list view").performClick()
        compose.onNodeWithTag("provider_folder_screen_g").assertExists()
        compose.onNodeWithContentDescription("Switch to grid view").assertExists()
        compose.onNodeWithContentDescription("Switch to grid view").performClick()
        compose.onNodeWithContentDescription("Switch to list view").assertExists()
    }

    @Test
    fun droppingAProviderOnFolderMovesItInWithoutOpeningTheFolder() {
        val writes = mutableListOf<ProviderPlacement>()
        showFolder(
            source = listOf(
                ModelProvider("source", "Source", ModelApiType.OPENAI, baseUrl = "", sortOrder = 0),
                ModelProvider("member", "Member", ModelApiType.OPENAI, baseUrl = "", groupId = "g", sortOrder = 0)
            ),
            writes = writes
        )
        val sourceCenter = compose.onNodeWithTag("provider_source").fetchSemanticsNode().boundsInRoot.center
        val folderCenter = compose.onNodeWithTag("provider_group_g").fetchSemanticsNode().boundsInRoot.center

        compose.onRoot().performTouchInput { down(sourceCenter) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performTouchInput { moveTo(folderCenter) }
        compose.mainClock.advanceTimeBy(200)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()

        assertEquals("g", writes.single().groupId)
        assertEquals(listOf("member", "source"), writes.single().groupOrders.getValue("g"))
        compose.onNodeWithTag("provider_folder_screen_g", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test
    fun nextFolderOpensBeforePreviousFolderCloseAnimationFinishes() {
        val sibling = ProviderGroup("sibling", "Sibling", sortOrder = 3)
        showFolder(groups = listOf(group, sibling))
        val siblingCenter = compose.onNodeWithTag("provider_group_sibling")
            .fetchSemanticsNode().boundsInRoot.center

        compose.onNodeWithTag("provider_group_g").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            click(Offset(rootBounds.width / 2f, rootBounds.height - 48f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().performTouchInput { click(siblingCenter) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("provider_folder_screen_sibling", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("provider_folder_screen_g", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("provider_folder_screen_g", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("provider_folder_screen_sibling", useUnmergedTree = true).assertExists()
    }

}
