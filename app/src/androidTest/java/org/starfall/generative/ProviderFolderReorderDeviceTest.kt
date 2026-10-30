package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.features.providers.list.ui.ProvidersScreen
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderRootOrderItem

/**
 * Contract for folder-to-folder displacement in the real ProvidersScreen.
 * Unlike the launcher demo, this screen supports groups as root-grid items.
 * Every scenario contains ONLY folders: providers must not be needed to
 * create a reorder target. The target folder must yield during hover,
 * before persistence on release; cancellation must restore the original order.
 *
 * These are regression specifications for the missing feature, not claims
 * that the current implementation already passes.
 */
@RunWith(AndroidJUnit4::class)
class ProviderFolderReorderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val rootWrites = mutableListOf<List<ProviderRootOrderItem>>()
    private val folders = (0 until 6).map { index ->
        ProviderGroup("folder-$index", "Folder $index", sortOrder = index)
    }

    private fun showFolderOnlyScreen(count: Int = 4, grid: Boolean = true) {
        rootWrites.clear()
        compose.setContent {
            MaterialTheme {
                ProvidersScreen(
                    providers = emptyList(),
                    providerGroups = folders.take(count),
                    isGridView = grid,
                    onOpenProviderEditor = { _, _ -> },
                    onDeleteProvider = {},
                    onReorderProviders = {},
                    onReorderRootItems = { rootWrites += it },
                    onBack = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private fun folderCenter(index: Int): Offset =
        compose.onNodeWithTag("provider_group_folder-$index", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center

    private fun rootPoint(point: Offset): Offset =
        point - compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft

    private fun startFolder(index: Int) {
        val position = rootPoint(folderCenter(index))
        compose.onRoot().performTouchInput { down(position) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
    }

    private fun hoverAt(point: Offset) {
        compose.onRoot().performTouchInput { moveTo(rootPoint(point)) }
        // Let the item's placement animation settle; the finger stays down.
        compose.mainClock.advanceTimeBy(650)
        compose.waitForIdle()
    }

    private fun release() {
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    private fun cancelDrag() {
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
    }

    private fun assertFolderAt(index: Int, expected: Offset) {
        val actual = folderCenter(index)
        assertEquals("Folder $index must yield horizontally", expected.x, actual.x, 4f)
        assertEquals("Folder $index must yield vertically", expected.y, actual.y, 4f)
    }

    private fun assertSavedOrder(vararg indices: Int) {
        assertEquals("A completed folder reorder must persist once", 1, rootWrites.size)
        assertEquals(indices.map { "folder-$it" }, rootWrites.single().map { it.id })
        assertTrue(rootWrites.single().all { it.isGroup })
    }

    @Test fun gridAdjacentFoldersYieldDuringHoverWithoutDraggingToEnd() {
        showFolderOnlyScreen()
        val firstSlot = folderCenter(0)
        val secondSlot = folderCenter(1)

        startFolder(0)
        hoverAt(secondSlot)

        assertFolderAt(1, firstSlot)
        assertTrue("Hover must not persist a reorder", rootWrites.isEmpty())
        release()
        assertSavedOrder(1, 0, 2, 3)
    }

    @Test fun gridFoldersAlsoYieldWhenDraggingBackward() {
        showFolderOnlyScreen()
        val firstSlot = folderCenter(0)
        val secondSlot = folderCenter(1)

        startFolder(1)
        hoverAt(firstSlot)

        assertFolderAt(0, secondSlot)
        assertTrue(rootWrites.isEmpty())
        release()
        assertSavedOrder(1, 0, 2, 3)
    }

    @Test fun gridFolderYieldsAcrossRowsWithoutRequiringTheLastSlot() {
        showFolderOnlyScreen(count = 6)
        val firstSlot = folderCenter(0)
        val secondSlot = folderCenter(1)
        val thirdSlot = folderCenter(2)

        startFolder(0)
        hoverAt(thirdSlot)

        assertFolderAt(1, firstSlot)
        assertFolderAt(2, secondSlot)
        assertTrue(rootWrites.isEmpty())
        release()
        assertSavedOrder(1, 2, 0, 3, 4, 5)
    }

    @Test fun listFoldersYieldDuringHoverExactlyLikeListProviders() {
        showFolderOnlyScreen(grid = false)
        val firstSlot = folderCenter(0)
        val secondSlot = folderCenter(1)

        startFolder(0)
        hoverAt(secondSlot)

        assertFolderAt(1, firstSlot)
        assertTrue(rootWrites.isEmpty())
        release()
        assertSavedOrder(1, 0, 2, 3)
    }

    @Test fun cancelAfterFolderDisplacementRestoresEverySlotAndWritesNothing() {
        showFolderOnlyScreen()
        val original = (0 until 4).map(::folderCenter)

        startFolder(0)
        hoverAt(original[1])
        assertFolderAt(1, original[0])
        cancelDrag()

        original.forEachIndexed { index, point -> assertFolderAt(index, point) }
        assertTrue("Cancel must not persist root order", rootWrites.isEmpty())
    }

    @Test fun stationaryFolderLongPressDoesNotMoveAnyNeighborOrPersist() {
        showFolderOnlyScreen()
        val original = (0 until 4).map(::folderCenter)

        startFolder(0)
        // Picking up a folder is not a reorder until it actually moves.
        (1 until 4).forEach { assertFolderAt(it, original[it]) }
        cancelDrag()

        original.forEachIndexed { index, point -> assertFolderAt(index, point) }
        assertTrue(rootWrites.isEmpty())
    }
}
