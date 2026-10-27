package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderPlacement
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.features.providers.list.ui.ProvidersScreen

/** Phone-style drag and drop: reorder, drop into a folder, drag out of an open folder. */
class ProviderDragBehaviorTest {
    @get:Rule val compose = createComposeRule()

    private val group = ProviderGroup("g", "Folder", sortOrder = 1)

    private fun setScreen(
        providers: List<ModelProvider>,
        writes: MutableList<ProviderPlacement> = mutableListOf()
    ) {
        compose.setContent {
            MaterialTheme {
                ProvidersScreen(
                    providers = providers,
                    providerGroups = listOf(group),
                    isGridView = true,
                    onOpenProviderEditor = { _, _ -> },
                    onDeleteProvider = {},
                    onReorderProviders = {},
                    onBack = {},
                    onPlaceProvider = {
                        writes += it
                        Result.success(Unit)
                    }
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun folderOpensAsFullScreenPageInsteadOfExpandingInsideTheGrid() {
        setScreen(
            listOf(
                ModelProvider("root", "Root", ModelApiType.OPENAI, baseUrl = "", sortOrder = 0),
                ModelProvider("member", "Member", ModelApiType.OPENAI, baseUrl = "", groupId = "g")
            )
        )

        compose.onNodeWithTag("provider_member", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("provider_group_g", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("provider_folder_screen_g", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("provider_folder_provider_member", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun droppingRootProviderOnFolderMovesItInWithoutOpeningThePage() {
        val writes = mutableListOf<ProviderPlacement>()
        setScreen(
            listOf(
                ModelProvider("root", "Root", ModelApiType.OPENAI, baseUrl = "", sortOrder = 0),
                ModelProvider("member", "Member", ModelApiType.OPENAI, baseUrl = "", groupId = "g")
            ),
            writes
        )
        val source = compose.onNodeWithTag("provider_root").fetchSemanticsNode().boundsInRoot.center
        val folder = compose.onNodeWithTag("provider_group_g").fetchSemanticsNode().boundsInRoot.center

        compose.onRoot().performTouchInput {
            down(source)
            advanceEventTime(700)
            moveTo(folder)
            advanceEventTime(100)
            up()
        }
        compose.waitForIdle()

        assertTrue(writes.single().groupId == "g")
        compose.onNodeWithTag("provider_folder_screen_g", useUnmergedTree = true).assertDoesNotExist()
    }
}
