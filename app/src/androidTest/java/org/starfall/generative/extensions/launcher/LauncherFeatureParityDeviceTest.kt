package org.starfall.generative.extensions.launcher

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Migration gate for Provider/MCP/Speech-like grids with 48 stable-key entries.
 * Compare logs with ProviderDropLatencyDeviceTest on the SAME device and build.
 * This uses in-memory data only; no production repositories are accessed.
 */
@RunWith(AndroidJUnit4::class)
class LauncherFeatureParityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val original = (0 until 48).map { "item:$it" }
    private lateinit var state: Launcher.State<String>
    private lateinit var layout: Launcher.Layout<String>
    private val completedAt = AtomicLong()
    private var gridScroll: LazyGridState? = null
    private var completions = 0
    private var commits = 0

    private fun origin() = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
    private fun point(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - origin()

    private fun showGrid() {
        state = Launcher.State()
        layout = Launcher.Layout({ it }, mapOf("workspace" to original))
        completedAt.set(0L)
        completions = 0
        commits = 0
        compose.setContent {
            with(Launcher) {
                val grid = rememberLazyGridState()
                SideEffect { gridScroll = grid }
                Host(state, Modifier.fillMaxSize(),
                    style = Launcher.Style(landingDurationMillis = 160),
                    preview = { Text(it.item.key, Modifier.testTag("launcher-shadow")) }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), state = grid,
                        modifier = Modifier.fillMaxSize().autoScroll(state, grid).testTag("launcher-grid"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(layout.preview.getValue("workspace"), key = { it }) { id ->
                            Box(
                                Modifier.fillMaxWidth().height(76.dp).animateItem()
                                    .dragSource(state, id, id,
                                        onDragStart = { check(layout.begin(id)) },
                                        onDragEnd = { outcome ->
                                            completedAt.compareAndSet(0L, SystemClock.elapsedRealtimeNanos())
                                            completions++
                                            if (outcome.accepted) { layout.commit(); commits++ }
                                            else layout.cancel()
                                        })
                                    .itemDropTarget(state, key = "slot:$id",
                                        acceptsReorder = { it.key != id },
                                        onReorderDrop = { layout.hover("workspace", id, after = false); true })
                                    .background(Color.Gray).testTag(id)
                            ) { Text(id) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showList() {
        state = Launcher.State()
        layout = Launcher.Layout({ it }, mapOf("workspace" to original))
        completedAt.set(0L)
        completions = 0
        commits = 0
        compose.setContent {
            with(Launcher) {
                val scroll = rememberLazyListState()
                Host(state, Modifier.fillMaxSize(), preview = {
                    Text(it.item.key, Modifier.testTag("launcher-shadow"))
                }) {
                    LazyColumn(
                        state = scroll,
                        modifier = Modifier.fillMaxSize().autoScroll(state, scroll).testTag("launcher-list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(layout.preview.getValue("workspace"), key = { it }) { id ->
                            Box(Modifier.fillMaxWidth().height(68.dp).animateItem()
                                .dragSource(state, id, id,
                                    onDragStart = { check(layout.begin(id)) },
                                    onDragEnd = { outcome ->
                                        completedAt.compareAndSet(0L, SystemClock.elapsedRealtimeNanos())
                                        completions++
                                        if (outcome.accepted) { layout.commit(); commits++ }
                                        else layout.cancel()
                                    })
                                .itemDropTarget(state, "slot:$id",
                                    acceptsReorder = { it.key != id },
                                    onReorderDrop = { layout.hover("workspace", id, after = false); true })
                                .background(Color.Gray).testTag(id)
                            ) { Text(id) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun drag(from: Int, to: Int, surface: String = "launcher-grid") {
        compose.onNodeWithTag(surface).performScrollToIndex(from)
        compose.waitForIdle()
        val source = point("item:$from")
        val destination = point("item:$to")
        Log.i("LauncherMigrationDebug", "before from=$from to=$to source=$source dest=$destination first=${gridScroll?.firstVisibleItemIndex}")
        compose.onRoot().performTouchInput { down(source) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("item:$from", state.session?.item?.key)
            assertEquals(0, commits)
        }
        val afterActivation = point("item:$to")
        Log.i("LauncherMigrationDebug", "after pickup from=$from to=$to dest=$afterActivation first=${gridScroll?.firstVisibleItemIndex} drift=${(afterActivation-destination).getDistance()}")
        compose.onRoot().performTouchInput { moveTo(destination) }
        compose.waitForIdle()
        Log.i("LauncherMigrationDebug", "after move from=$from to=$to hover=${state.hoveredTargetKey} first=${gridScroll?.firstVisibleItemIndex}")
        compose.runOnIdle {
            assertEquals("slot:item:$to", state.hoveredTargetKey)
            assertEquals("No persistence before release", 0, commits)
        }
    }

    private fun releaseAndMeasure(from: Int, to: Int) {
        val expected = original.toMutableList().apply {
            val moved = removeAt(indexOf("item:$from"))
            add(indexOf("item:$to"), moved)
        }
        var callbackFrame = -1
        var shadowGoneFrame = -1
        val startNanos = SystemClock.elapsedRealtimeNanos()
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performTouchInput { up() }
            for (frame in 0..60) {
                if (frame > 0) compose.mainClock.advanceTimeByFrame()
                if (callbackFrame < 0 && completedAt.get() != 0L) callbackFrame = frame
                if (shadowGoneFrame < 0 &&
                    compose.onAllNodesWithTag("launcher-shadow", useUnmergedTree = true)
                        .fetchSemanticsNodes().isEmpty()) shadowGoneFrame = frame
                if (callbackFrame >= 0 && shadowGoneFrame >= 0) break
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        val callbackMs = if (completedAt.get() != 0L)
            (completedAt.get() - startNanos) / 1_000_000.0 else -1.0
        Log.i("LauncherMigrationGate", "count=48 from=$from to=$to " +
            "callbackFrames=$callbackFrame shadowGoneFrames=$shadowGoneFrame callbackWallMs=$callbackMs")
        compose.runOnIdle {
            assertTrue("Commit must not wait for the landing animation", callbackFrame in 0..2)
            assertTrue("Drag overlay must eventually disappear", shadowGoneFrame >= 0)
            assertEquals(1, commits)
            assertEquals(1, completions)
            assertNull(state.session)
            assertEquals(expected, layout.preview.getValue("workspace"))
            assertEquals(48, layout.preview.getValue("workspace").distinct().size)
        }
    }

    @Test fun startOfLongGridCompletesDropImmediately() { showGrid(); drag(2, 4); releaseAndMeasure(2, 4) }
    @Test fun middleOfLongGridCompletesDropImmediately() { showGrid(); drag(22, 24); releaseAndMeasure(22, 24) }
    @Test fun endOfLongGridDoesNotAccumulateDropLatency() { showGrid(); drag(44, 46); releaseAndMeasure(44, 46) }
    @Test fun providerModelLikeLongListUsesTheSameDropAndScrollContract() {
        showList(); drag(44, 46, surface = "launcher-list"); releaseAndMeasure(44, 46)
    }

    @Test fun cancellingDragRollsBackWithoutPersisting() {
        showGrid()
        drag(22, 24)
        compose.runOnIdle {
            assertTrue("Preview must move before cancellation", layout.hover("workspace", "item:24", after = false))
            assertNotEquals(original, layout.preview.getValue("workspace"))
            assertEquals("Preview is not persistence", 0, commits)
        }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(0, commits)
            assertEquals(original, layout.preview.getValue("workspace"))
            assertNull(state.session)
        }
    }

    @Test fun groupingPolicyCanBeChangedDuringDragWithoutRemountingHost() {
        val local = Launcher.State<String>()
        var allowJoin by mutableStateOf(false)
        var joins = 0
        var reorders = 0
        var finishes = 0
        compose.setContent {
            with(Launcher) {
                Host(local, Modifier.fillMaxSize(),
                    activation = Launcher.ActivationMode.LongPress(250),
                    style = Launcher.Style(pickupScale = 1.08f, landingDurationMillis = 100),
                    preview = {}
                ) {
                    Box(Modifier.size(90.dp).dragSource(local, "source", "source",
                        onDragEnd = { finishes++ }).testTag("source")) { Text("Source") }
                    Box(Modifier.offset(x = 150.dp, y = 20.dp).size(140.dp)
                        .itemDropTarget(local, "folder",
                            policy = Launcher.DropPolicy(
                                joinHitTest = if (allowJoin) Launcher.Collision.Center(0.7f, 0.7f) else null),
                            onReorderDrop = { reorders++; true },
                            onJoinDrop = if (allowJoin) ({ joins++; true }) else null)
                        .testTag("folder")) { Text("Folder") }
                }
            }
        }
        compose.waitForIdle()
        val from = point("source")
        val to = point("folder")
        compose.onRoot().performTouchInput { down(from) }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle { assertNull(local.session) }
        compose.mainClock.advanceTimeBy(220)
        compose.runOnIdle { assertEquals("source", local.session?.item?.key) }
        compose.onRoot().performTouchInput { moveTo(to) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("folder", local.hoveredTargetKey)
            assertEquals(0, joins)
            allowJoin = true
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("folder:join", local.hoveredTargetKey) }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, joins); assertEquals(0, reorders)
            assertEquals(1, finishes); assertNull(local.session)
        }
    }
}
