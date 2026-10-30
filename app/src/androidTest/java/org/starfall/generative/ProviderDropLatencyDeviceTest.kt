package org.starfall.generative

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.features.mcp.list.ui.ServerListScreen
import org.starfall.generative.features.providers.list.ui.ProvidersScreen
import org.starfall.generative.shared.model.McpInfo
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.provider.ModelProvider

/** Physical-device benchmark. All 48 entries live in this test Activity; no app database is touched. */
@RunWith(AndroidJUnit4::class)
class ProviderDropLatencyDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun point(tag: String, byText: Boolean = false): Offset {
        val target = if (byText) compose.onNodeWithText(tag, useUnmergedTree = true)
            else compose.onNodeWithTag(tag, useUnmergedTree = true)
        return target.fetchSemanticsNode().boundsInRoot.center -
            compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
    }

    private fun measureRelease(kind: String, index: Int, completedAt: AtomicLong, ghostTag: String?) {
        val root = compose.onRoot()
        // Pause only AFTER the hover has settled, to count the drop frames without
        // allowing the test framework to fast-forward the animation to idle.
        compose.mainClock.autoAdvance = false
        val startedAt = SystemClock.elapsedRealtimeNanos()
        var finishedFrame = -1
        var ghostGoneFrame = -1
        try {
            root.performTouchInput { up() }
            for (frame in 1..180) {
                compose.mainClock.advanceTimeByFrame()
                if (finishedFrame < 0 && completedAt.get() != 0L) finishedFrame = frame
                if (ghostTag != null && ghostGoneFrame < 0 &&
                    compose.onAllNodesWithTag(ghostTag, useUnmergedTree = true)
                        .fetchSemanticsNodes().isEmpty()) ghostGoneFrame = frame
                if (finishedFrame >= 0 && (ghostTag == null || ghostGoneFrame >= 0)) break
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        val callbackAt = completedAt.get()
        val callbackMs = if (callbackAt == 0L) -1.0 else (callbackAt - startedAt) / 1_000_000.0
        Log.i("ProviderDropLatency", "kind=$kind index=$index count=48 " +
            "callbackFrames=$finishedFrame ghostGoneFrames=$ghostGoneFrame " +
            "callbackWallMs=${"%.2f".format(java.util.Locale.US, callbackMs)}")
        assertTrue("$kind at $index did not commit a valid drop", callbackAt != 0L)
        if (ghostTag != null) assertTrue("$kind at $index never removed the shadow", ghostGoneFrame >= 0)
    }

    private fun measureProvider(index: Int) {
        val entries = (0 until 48).map {
            ModelProvider("p$it", "Provider $it", ModelApiType.OPENAI, baseUrl = "", sortOrder = it)
        }
        val completedAt = AtomicLong()
        compose.setContent {
            MaterialTheme {
                ProvidersScreen(
                    providers = entries, isGridView = true,
                    onOpenProviderEditor = { _, _ -> }, onDeleteProvider = {},
                    onReorderProviders = {}, onBack = {},
                    onPlaceProviderImmediately = { completedAt.compareAndSet(0, SystemClock.elapsedRealtimeNanos()) }
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("provider_list").performScrollToIndex(index)
        compose.waitForIdle()
        val root = compose.onRoot()
        val from = point("provider_p$index")
        val to = point("provider_p${index + 2}")
        root.performTouchInput { down(from) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("dragged_provider", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        root.performTouchInput { moveTo(to) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        assertTrue("must not commit before release", completedAt.get() == 0L)
        measureRelease("providers", index, completedAt, "dragged_provider")
    }

    private fun measureMcp(index: Int) {
        val entries = (0 until 48).map { McpInfo("m$it", "MCP $it") }
        val completedAt = AtomicLong()
        compose.setContent {
            MaterialTheme {
                ServerListScreen(
                    mcpServers = entries, isGridView = true,
                    toolsCache = emptyMap(), toolErrors = emptyMap(), toolsLoading = emptySet(),
                    toolSettings = ToolSettings(),
                    onSetToolEnabled = { _, _, _ -> }, onSetMcpEnabled = { _, _ -> },
                    onDeleteMcpServer = {},
                    onReorderMcpServers = { completedAt.compareAndSet(0, SystemClock.elapsedRealtimeNanos()) },
                    onOpenEditor = { _, _ -> }, onBack = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        compose.waitForIdle()
        val root = compose.onRoot()
        val from = point("MCP $index", byText = true)
        val to = point("MCP ${index + 2}", byText = true)
        root.performTouchInput { down(from) }
        compose.mainClock.advanceTimeBy(750)
        root.performTouchInput { moveTo(to) }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        assertTrue("must not commit before release", completedAt.get() == 0L)
        measureRelease("mcp", index, completedAt, null)
    }

    @Test fun providersStart() = measureProvider(2)
    @Test fun providersMiddle() = measureProvider(22)
    @Test fun providersEnd() = measureProvider(44)
    @Test fun mcpStart() = measureMcp(2)
    @Test fun mcpMiddle() = measureMcp(22)
    @Test fun mcpEnd() = measureMcp(44)
}
