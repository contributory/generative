package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.features.providers.list.ui.ProvidersScreen
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderPlacement
import org.starfall.generative.shared.model.provider.ProviderRootOrderItem

/**
 * Runs with AndroidJUnitRunner on a real Android window, not Robolectric.
 * Keep the pointer held across composition changes to catch drag ownership/ghost jumps.
 */
@RunWith(AndroidJUnit4::class)
class ProviderDragBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val folder = ProviderGroup("g", "Folder", sortOrder = 10)
    private val otherFolder = ProviderGroup("h", "Other Folder", sortOrder = 11)

    private fun provider(id: String, group: String? = null, order: Int = 0) =
        ModelProvider(id, id, ModelApiType.OPENAI, baseUrl = "", groupId = group, sortOrder = order)

    private fun show(
        providers: List<ModelProvider>,
        groups: List<ProviderGroup> = listOf(folder),
        grid: Boolean = true,
        writes: MutableList<ProviderPlacement> = CopyOnWriteArrayList(),
        rootOrders: MutableList<List<ProviderRootOrderItem>> = CopyOnWriteArrayList()
    ) {
        compose.setContent {
            MaterialTheme {
                ProvidersScreen(
                    providers = providers,
                    providerGroups = groups,
                    isGridView = grid,
                    onOpenProviderEditor = { _, _ -> },
                    onDeleteProvider = {},
                    onReorderProviders = {},
                    onReorderRootItems = { rootOrders.add(it) },
                    onBack = {},
                    onPlaceProvider = { writes.add(it); Result.success(Unit) }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun exists(tag: String) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = node(tag).fetchSemanticsNode().boundsInRoot

    // TouchInjectionScope coordinates are local to onRoot, not necessarily screen coordinates.
    private fun point(tag: String): Offset =
        bounds(tag).center - compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft

    private fun hold(at: Offset, ghost: String = "dragged_provider") {
        compose.onRoot().performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitUntil(5_000) { exists(ghost) }
    }

    private fun move(to: Offset) {
        compose.onRoot().performTouchInput { moveTo(to) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
    }

    private fun release() {
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    private fun cancelGesture() {
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
    }

    private fun awaitWrite(writes: List<ProviderPlacement>): ProviderPlacement {
        compose.waitUntil(5_000) { writes.size == 1 }
        return writes.single()
    }

    private fun openFolder(id: String = "g") {
        node("provider_group_$id").performClick()
        compose.mainClock.advanceTimeBy(750)
        compose.waitUntil(5_000) { exists("provider_folder_provider_m1") }
        compose.waitForIdle()
    }

    @Test
    fun folderOpensAsFullScreenPageInsteadOfExpandingInsideTheGrid() {
        show(listOf(provider("root"), provider("m1", "g")))
        node("provider_m1").assertDoesNotExist()
        openFolder()
        node("provider_folder_screen_g").assertIsDisplayed()
        node("provider_folder_provider_m1").assertIsDisplayed()
    }

    @Test
    fun longPressLeftProviderDoesNotPickNeighborFolder() {
        show(listOf(provider("left", order = 9), provider("m1", "g")))
        hold(point("provider_left"))
        assertFalse("a nearby Folder must not be dragged", exists("dragged_group"))
        cancelGesture()
    }

    @Test
    fun longPressRightProviderDoesNotPickNeighborFolder() {
        show(listOf(provider("right", order = 11), provider("m1", "g")))
        hold(point("provider_right"))
        assertFalse("a nearby Folder must not be dragged", exists("dragged_group"))
        cancelGesture()
    }

    @Test
    fun listModeLongPressUsesTheTouchedProvider() {
        show(listOf(provider("right", order = 11), provider("m1", "g")), grid = false)
        hold(point("provider_right"))
        assertFalse(exists("dragged_group"))
        cancelGesture()
    }

    @Test
    fun droppingRootProviderOnFolderMovesItInWithoutOpeningThePage() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(listOf(provider("root", order = 0), provider("m1", "g")), writes = writes)
        val source = point("provider_root")
        val target = point("provider_group_g")
        hold(source)
        move(target)
        release()
        val placement = awaitWrite(writes)
        assertEquals("root", placement.providerId)
        assertEquals("g", placement.groupId)
        assertTrue(placement.groupOrders.getValue("g").contains("root"))
        node("provider_folder_screen_g").assertDoesNotExist()
    }

    @Test
    fun droppingRootProviderOnFolderAlsoWorksInListMode() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(listOf(provider("root", order = 0), provider("m1", "g")),
            grid = false, writes = writes)
        hold(point("provider_root"))
        move(point("provider_group_g"))
        release()
        assertEquals("g", awaitWrite(writes).groupId)
        node("provider_folder_screen_g").assertDoesNotExist()
    }

    @Test
    fun cancellingRootReorderRestoresOriginalPlacement() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(listOf(provider("a", order = 0), provider("b", order = 1),
            provider("m1", "g")), writes = writes)
        val initial = bounds("provider_a").center
        hold(point("provider_a"))
        move(point("provider_b"))
        assertTrue(writes.isEmpty())
        cancelGesture()
        assertTrue("cancel must not save root reordering", writes.isEmpty())
        val after = bounds("provider_a").center
        val tolerance = with(compose.density) { 12.dp.toPx() }
        assertEquals(initial.x, after.x, tolerance)
        assertEquals(initial.y, after.y, tolerance)
    }

    @Test
    fun reorderInsideOpenFolderPersistsOnlyAfterRelease() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(listOf(provider("m1", "g", 0), provider("m2", "g", 1)), writes = writes)
        openFolder()
        val start = point("provider_folder_provider_m1")
        val target = point("provider_folder_provider_m2")
        hold(start)
        move(target)
        assertTrue("preview must not persist while holding", writes.isEmpty())
        release()
        assertEquals(listOf("m2", "m1"), awaitWrite(writes).groupOrders.getValue("g"))
        node("provider_folder_screen_g").assertIsDisplayed()
    }

    @Test
    fun leavingFolderKeepsShadowAtFingerAndMovesProviderToRoot() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(listOf(provider("root", order = 0), provider("m1", "g")), writes = writes)
        // Capture a root-header target before the folder covers its semantics.
        val exit = point("add_provider_group")
        openFolder()
        val from = point("provider_folder_provider_m1")
        hold(from)
        val before = bounds("dragged_provider").center
        move(exit)
        compose.waitUntil(5_000) { !exists("provider_folder_screen_g") }
        assertTrue("the active drag shadow must outlive the folder", exists("dragged_provider"))
        val after = bounds("dragged_provider").center
        val tolerance = with(compose.density) { 12.dp.toPx() }
        assertEquals("shadow must not jump horizontally", exit.x - from.x, after.x - before.x, tolerance)
        assertEquals("shadow must not jump vertically", exit.y - from.y, after.y - before.y, tolerance)
        release()
        val placement = awaitWrite(writes)
        assertEquals("m1", placement.providerId)
        assertNull("member must leave the folder", placement.groupId)
        node("provider_m1").assertExists()
    }

    @Test
    fun draggingOutOfFolderAndCancellingRestoresMembershipWithoutWrites() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(listOf(provider("root", order = 0), provider("m1", "g")), writes = writes)
        val exit = point("add_provider_group")
        openFolder()
        hold(point("provider_folder_provider_m1"))
        move(exit)
        compose.waitUntil(5_000) { !exists("provider_folder_screen_g") }
        cancelGesture()
        assertTrue("a cancelled gesture must not be stored", writes.isEmpty())
        node("provider_m1").assertDoesNotExist()
        openFolder()
        node("provider_folder_provider_m1").assertIsDisplayed()
    }

    @Test
    fun draggingOutOfOneFolderIntoAnotherKeepsTheSameGesture() {
        val writes = CopyOnWriteArrayList<ProviderPlacement>()
        show(
            listOf(provider("root", order = 0), provider("m1", "g"), provider("peer", "h")),
            groups = listOf(folder, otherFolder), writes = writes
        )
        val exit = point("add_provider_group")
        openFolder()
        hold(point("provider_folder_provider_m1"))
        move(exit)
        compose.waitUntil(5_000) { !exists("provider_folder_screen_g") }
        // Ejecting the member inserts a new root tile, shifting Folder h's cell.
        // Use the target's current bounds after that layout change, not its old position.
        node("provider_group_h").assertIsDisplayed()
        move(point("provider_group_h"))
        release()
        val placement = awaitWrite(writes)
        assertEquals("m1", placement.providerId)
        assertEquals("h", placement.groupId)
        assertTrue(placement.groupOrders.getValue("h").contains("m1"))
        assertFalse(placement.groupOrders.getValue("g").contains("m1"))
        node("provider_folder_screen_h").assertDoesNotExist()
    }

    @Test
    fun longPressFolderDragsThatFolderNotAdjacentProvider() {
        val rootOrders = CopyOnWriteArrayList<List<ProviderRootOrderItem>>()
        show(listOf(provider("root", order = 0), provider("m1", "g")),
            rootOrders = rootOrders)
        hold(point("provider_group_g"), ghost = "dragged_group")
        assertFalse(exists("dragged_provider"))
        move(point("provider_root"))
        release()
        compose.waitUntil(5_000) { rootOrders.size == 1 }
        assertEquals("g", rootOrders.single().first().id)
    }
}
