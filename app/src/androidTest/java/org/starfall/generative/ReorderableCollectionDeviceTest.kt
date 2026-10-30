package org.starfall.generative

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.shared.ui.lists.ReorderableCollection

/** Exercises the shared drag host used by Machines, Instructions, Skills and Media Profiles. */
@RunWith(AndroidJUnit4::class)
class ReorderableCollectionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun center(id: String): Offset =
        compose.onNodeWithTag("reorder:$id").fetchSemanticsNode().boundsInRoot.center -
            compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft

    private fun verifyReorder(columns: Int, locked: Boolean) {
        val order = mutableStateOf(listOf("pinned", "first", "second", "third"))
        val saved = CopyOnWriteArrayList<List<String>>()
        compose.setContent {
            MaterialTheme {
                ReorderableCollection(
                    items = order.value, key = { it },
                    columns = GridCells.Fixed(columns),
                    canReorder = { !locked || it != "pinned" },
                    onReorder = { saved += it; order.value = it },
                    preview = { Text(it) }
                ) { label, dragModifier ->
                    Surface(modifier = dragModifier.fillMaxWidth().testTag("reorder:$label"), onClick = {}) {
                        Text(label)
                    }
                }
            }
        }
        compose.waitForIdle()
        val from = center("first")
        val to = center("third")
        val root = compose.onRoot()
        root.performTouchInput { down(from) }
        compose.mainClock.advanceTimeBy(750)
        root.performTouchInput { moveTo(to) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("a drag must not write before release", saved.isEmpty()) }
        root.performTouchInput { up() }
        compose.waitUntil(5_000) { saved.size == 1 }
        compose.runOnIdle {
            assertEquals(listOf("pinned", "second", "third", "first"), saved.single())
        }
    }

    @Test fun gridDragReordersOnlyOnAcceptedDrop() = verifyReorder(columns = 2, locked = false)

    @Test fun listDragCanKeepAProtectedDefaultFixed() = verifyReorder(columns = 1, locked = true)
}
