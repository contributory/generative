package org.starfall.generative

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntil
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.starfall.generative.features.home.chat.ui.ChatRenderMetrics
import org.starfall.generative.features.home.chat.model.StreamTextSnapshot
import org.starfall.generative.features.home.chat.ui.markdown.ChunkedStreamingMarkdown
import org.starfall.generative.features.home.chat.ui.markdown.LocalMarkdownViewportState
import org.starfall.generative.features.home.chat.ui.markdown.MarkdownRenderer
import org.starfall.generative.features.home.chat.ui.markdown.MarkdownViewportState
import org.starfall.generative.features.home.chat.ui.markdown.StreamingMarkdownRenderer

@RunWith(AndroidJUnit4::class)
class MarkdownVirtualizationTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun resetMetrics() {
        ChatRenderMetrics.resetForTests()
    }

    @Test
    fun largeListOnlyMountsTheViewportWindowAndDisposesRowsWhenScrolledAway() {
        val itemCount = 180
        val markdown = (0 until itemCount).joinToString("\n") { "- List item $it" }
        val viewport = MarkdownViewportState()
        var scope: CoroutineScope? = null
        var scrollState: androidx.compose.foundation.lazy.LazyListState? = null
        var scrollOffsetPx = 1_500

        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current.density
                SideEffect {
                    scope = coroutineScope
                    scrollState = listState
                    scrollOffsetPx = (1_500 * density).roundToInt()
                }
                CompositionLocalProvider(LocalMarkdownViewportState provides viewport) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .onGloballyPositioned { viewport.update(it.boundsInRoot()) }
                    ) {
                        item(key = "markdown") { MarkdownRenderer(markdown) }
                    }
                }
            }
        }

        compose.waitUntil(10_000) {
            compose.onNodeWithText("List item 0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("List item 0").assertExists()
        compose.onNodeWithText("List item 179").assertDoesNotExist()
        val initialMounts = ChatRenderMetrics.snapshot().listItemMounts
        assertTrue("initial composition must be bounded by the viewport", initialMounts > 0L && initialMounts < itemCount.toLong())

        compose.runOnIdle {
            scope!!.launch { scrollState!!.scrollToItem(index = 0, scrollOffset = scrollOffsetPx) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("List item 40").fetchSemanticsNodes().isNotEmpty()
        }
        val afterScroll = ChatRenderMetrics.snapshot()
        assertTrue("off-window list item subtrees should be disposed", afterScroll.listItemDisposals > 0L)
        assertTrue("scrolling mounts only a subset of the list", afterScroll.listItemMounts < itemCount * 2L)
    }

    @Test
    fun largeTopLevelMarkdownOnlyMountsAndRetainsTheViewportWindow() {
        val blockCount = 180
        val markdown = (0 until blockCount).joinToString("\n\n") { "Paragraph block $it." }
        val viewport = MarkdownViewportState()
        var scope: CoroutineScope? = null
        var scrollState: androidx.compose.foundation.lazy.LazyListState? = null
        var scrollOffsetPx = 1_500

        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current.density
                SideEffect {
                    scope = coroutineScope
                    scrollState = listState
                    scrollOffsetPx = (1_500 * density).roundToInt()
                }
                CompositionLocalProvider(LocalMarkdownViewportState provides viewport) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .onGloballyPositioned { viewport.update(it.boundsInRoot()) }
                    ) {
                        item(key = "markdown-blocks") { MarkdownRenderer(markdown) }
                    }
                }
            }
        }

        compose.waitUntil(10_000) {
            ChatRenderMetrics.snapshot().topLevelBlockMounts in 1 until blockCount.toLong()
        }
        compose.onNodeWithText("Paragraph block 0.").assertExists()
        compose.onNodeWithText("Paragraph block 179.").assertDoesNotExist()

        compose.runOnIdle {
            scope!!.launch { scrollState!!.scrollToItem(index = 0, scrollOffset = scrollOffsetPx) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Paragraph block 40.").fetchSemanticsNodes().isNotEmpty()
        }
        val afterScroll = ChatRenderMetrics.snapshot()
        assertTrue("top-level subtrees outside the viewport must be disposed", afterScroll.topLevelBlockDisposals > 0L)
        assertTrue("the mounted set remains a viewport subset", afterScroll.topLevelBlockMounts < blockCount * 2L)
    }

    @Test
    fun largeTableRowsOnlyMountAndRetainTheViewportWindow() {
        val rowCount = 180
        val markdown = buildString {
            append("| Name | Value |\n| --- | --- |\n")
            repeat(rowCount) { row -> append("| Table row $row | Value $row |\n") }
        }
        val viewport = MarkdownViewportState()
        var scope: CoroutineScope? = null
        var scrollState: androidx.compose.foundation.lazy.LazyListState? = null
        var scrollOffsetPx = 1_500

        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current.density
                SideEffect {
                    scope = coroutineScope
                    scrollState = listState
                    scrollOffsetPx = (1_500 * density).roundToInt()
                }
                CompositionLocalProvider(LocalMarkdownViewportState provides viewport) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .onGloballyPositioned { viewport.update(it.boundsInRoot()) }
                    ) {
                        item(key = "markdown-table") { MarkdownRenderer(markdown) }
                    }
                }
            }
        }

        compose.waitUntil(10_000) {
            ChatRenderMetrics.snapshot().tableRowMounts in 1 until rowCount.toLong()
        }
        compose.onNodeWithText("Table row 0").assertExists()
        compose.onNodeWithText("Table row 179").assertDoesNotExist()

        compose.runOnIdle {
            scope!!.launch { scrollState!!.scrollToItem(index = 0, scrollOffset = scrollOffsetPx) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Table row 30").fetchSemanticsNodes().isNotEmpty()
        }
        val afterScroll = ChatRenderMetrics.snapshot()
        assertTrue("off-window table rows should be disposed", afterScroll.tableRowDisposals > 0L)
        assertTrue("only a subset of table body rows is mounted", afterScroll.tableRowMounts < rowCount * 2L)
    }

    @Test
    fun hugeParagraphAndCodeFenceOnlyExposeBoundedPreviewsInTheMessage() {
        val paragraph = "PARAGRAPH_PREVIEW_MARKER " + "p".repeat(22_000) + " PARAGRAPH_TAIL_MARKER"
        val code = "CODE_PREVIEW_MARKER\n" + "x".repeat(20_000) + "\nCODE_TAIL_MARKER"
        val codeFence = "```text\n$code\n```"

        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.fillMaxWidth().height(640.dp)) {
                    item(key = "large-paragraph") { MarkdownRenderer(paragraph) }
                    item(key = "large-code") { MarkdownRenderer(codeFence) }
                }
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("PARAGRAPH_PREVIEW_MARKER", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("CODE_PREVIEW_MARKER", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PARAGRAPH_TAIL_MARKER", substring = true).assertDoesNotExist()
        compose.onNodeWithText("CODE_TAIL_MARKER", substring = true).assertDoesNotExist()
    }

    @Test
    fun hugeHtmlBlockUsesABoundedPreviewInTheMessage() {
        val html = buildString {
            append("<section>\n<div>HTML_PREVIEW_MARKER ")
            append("h".repeat(22_000))
            append(" HTML_TAIL_MARKER</div>\n</section>")
        }

        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.fillMaxWidth().height(640.dp)) {
                    item(key = "large-html") { MarkdownRenderer(html) }
                }
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("HTML_PREVIEW_MARKER", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("HTML_TAIL_MARKER", substring = true).assertDoesNotExist()
    }

    @Test
    fun oversizedActiveBlockKeepsABoundedPreviewAndOffersLazyFullSource() {
        val stream = "STREAMING_HEAD_MARKER " + "x".repeat(13_900) +
            "STREAMING_TAIL_MARKER " + "x".repeat(3_900)
        val snapshot = StreamTextSnapshot.of(stream)

        compose.setContent {
            MaterialTheme {
                StreamingMarkdownRenderer(
                    content = "",
                    isStreaming = true,
                    streamSnapshot = snapshot,
                    streamGenerationId = 9L,
                    streamMessageId = "oversized-active-block"
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("STREAMING_TAIL_MARKER", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("STREAMING_HEAD_MARKER", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Expand").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("STREAMING_HEAD_MARKER", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun activeStreamUsesVirtualizedStableAstRowsAndDisposesScrolledRows() {
        val blockCount = 180
        val markdown = (0 until blockCount).joinToString("\n\n") { "Stream block $it." }
        val snapshot = StreamTextSnapshot.of(markdown)
        val viewport = MarkdownViewportState()
        var scope: CoroutineScope? = null
        var scrollState: androidx.compose.foundation.lazy.LazyListState? = null
        var scrollOffsetPx = 1_500

        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current.density
                SideEffect {
                    scope = coroutineScope
                    scrollState = listState
                    scrollOffsetPx = (1_500 * density).roundToInt()
                }
                CompositionLocalProvider(LocalMarkdownViewportState provides viewport) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .onGloballyPositioned { viewport.update(it.boundsInRoot()) }
                    ) {
                        item(key = "active-stream") {
                            StreamingMarkdownRenderer(
                                content = "",
                                isStreaming = true,
                                streamSnapshot = snapshot,
                                streamGenerationId = 1L,
                                streamMessageId = "active-message"
                            )
                        }
                    }
                }
            }
        }

        compose.waitUntil(10_000) {
            ChatRenderMetrics.snapshot().topLevelBlockMounts in 1 until blockCount.toLong()
        }
        compose.onNodeWithText("Stream block 0.").assertExists()
        compose.onNodeWithText("Stream block 179.").assertDoesNotExist()

        compose.runOnIdle {
            scope!!.launch { scrollState!!.scrollToItem(index = 0, scrollOffset = scrollOffsetPx) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Stream block 40.").fetchSemanticsNodes().isNotEmpty()
        }
        val afterScroll = ChatRenderMetrics.snapshot()
        assertTrue("active stable AST subtrees should be disposed after scrolling", afterScroll.topLevelBlockDisposals > 0L)
    }

    @Test
    fun frozenStreamingChunksAreVirtualizedInsteadOfKeptInAColumn() {
        val blockCount = 180
        val markdown = (0 until blockCount).joinToString("\n\n") { "Frozen block $it." }
        val viewport = MarkdownViewportState()
        var scope: CoroutineScope? = null
        var scrollState: androidx.compose.foundation.lazy.LazyListState? = null
        var scrollOffsetPx = 1_500

        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current.density
                SideEffect {
                    scope = coroutineScope
                    scrollState = listState
                    scrollOffsetPx = (1_500 * density).roundToInt()
                }
                CompositionLocalProvider(LocalMarkdownViewportState provides viewport) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .onGloballyPositioned { viewport.update(it.boundsInRoot()) }
                    ) {
                        item(key = "frozen-stream") { ChunkedStreamingMarkdown(markdown) }
                    }
                }
            }
        }

        compose.waitUntil(20_000) {
            val metrics = ChatRenderMetrics.snapshot()
            metrics.topLevelBlockMounts in 1 until blockCount.toLong() &&
                compose.onAllNodesWithText("Frozen block 179.").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Frozen block 0.").assertExists()
        compose.onNodeWithText("Frozen block 179.").assertDoesNotExist()

        compose.runOnIdle {
            scope!!.launch { scrollState!!.scrollToItem(index = 0, scrollOffset = scrollOffsetPx) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Frozen block 40.").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("off-window frozen chunk subtrees should be disposed", ChatRenderMetrics.snapshot().topLevelBlockDisposals > 0L)
    }
}
