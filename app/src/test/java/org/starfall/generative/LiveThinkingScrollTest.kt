package org.starfall.generative

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.ui.LiveThinkingBlock
import org.starfall.generative.features.home.chat.ui.rememberLiveThinkingScrollState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class LiveThinkingScrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: ScrollState
    private var height by mutableIntStateOf(80)
    private var active by mutableStateOf(true)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            state = rememberLiveThinkingScrollState(active)
            Box(Modifier.fillMaxWidth().height(120.dp).testTag("thinking-scroll")
                .verticalScroll(state)) {
                Box(Modifier.fillMaxWidth().height(height.dp))
            }
        } }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun assertAtEnd() = compose.runOnIdle {
        assertTrue("The content genuinely exceeds the preview", state.maxValue > 0)
        assertEquals(state.maxValue, state.value)
        assertFalse(state.isScrollInProgress)
    }

    @Test fun nativeFollowTracksSlowFastAndBurstyGrowthWithoutAnimation() {
        show()
        compose.runOnIdle { height = 600 }
        frames(3)
        assertAtEnd()
        repeat(5) {
            compose.runOnIdle { height += 24 }
            frames(30)
        }
        val beforeFastGrowth = state.value
        repeat(20) {
            compose.runOnIdle { height += 32 }
            frames(2)
            assertAtEnd()
        }
        compose.runOnIdle {
            assertTrue("Follow must progress during continuous growth, not only after a pause",
                state.value > beforeFastGrowth)
            height += 1200
        }
        frames(2)
        assertAtEnd()
        val settled = state.value
        frames(120)
        compose.runOnIdle {
            assertEquals("A tool wait has no continued motion", settled, state.value)
            assertFalse(state.isScrollInProgress)
        }
    }

    @Test fun slowSingleLineGrowthSnapsToEndWithoutRepeatedAnimation() {
        show()
        compose.runOnIdle { height = 600 }
        frames(90)
        assertAtEnd()
        repeat(12) { step ->
            compose.runOnIdle { height += if (step % 2 == 0) 12 else 24 }
            frames(2)
            compose.runOnIdle {
                assertEquals("Each short new line should follow without lag", state.maxValue, state.value)
                assertFalse("Short line growth must not launch an animation", state.isScrollInProgress)
            }
            frames(15) // A slow stream should stay still between line publications.
            assertAtEnd()
        }
        frames(120)
        assertAtEnd()
    }

    @Test fun resumingAtBottomDoesNotIssueAZeroDistanceScroll() {
        var enabled by mutableStateOf(false)
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            scrollState = rememberLiveThinkingScrollState(enabled)
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxWidth().height(120.dp).verticalScroll(scrollState)) {
                Spacer(Modifier.height(600.dp))
            }
        } }
        frames(2)
        compose.runOnIdle { scope.launch { scrollState.scrollTo(scrollState.maxValue) } }
        frames(3)
        compose.runOnIdle { assertEquals(scrollState.maxValue, scrollState.value) }

        val mutationStarted = CompletableDeferred<Unit>()
        val mutationInterrupted = CompletableDeferred<Unit>()
        lateinit var holdingScroll: Job
        compose.runOnIdle {
            holdingScroll = scope.launch {
                try {
                    scrollState.scroll(MutatePriority.Default) {
                        mutationStarted.complete(Unit)
                        awaitCancellation()
                    }
                } finally {
                    mutationInterrupted.complete(Unit)
                }
            }
        }
        frames(2)
        compose.runOnIdle {
            assertTrue("Native scroll mutation must start", mutationStarted.isCompleted)
            enabled = true
        }
        frames(4)
        compose.runOnIdle {
            assertFalse("Already at the bottom: auto-follow must not interrupt an active scroll",
                mutationInterrupted.isCompleted)
            assertEquals(scrollState.maxValue, scrollState.value)
            holdingScroll.cancel()
        }
        frames(2)
    }

    @Test fun nativeDragCancelsFollowUntilTheNextGrowth() {
        show()
        compose.runOnIdle { height = 1000 }
        frames(90)
        assertAtEnd()
        compose.runOnIdle { height = 2000 }
        frames(2)
        compose.onNodeWithTag("thinking-scroll").performTouchInput {
            swipe(Offset(width / 2f, height * 0.25f), Offset(width / 2f, height * 0.80f), 1_000)
        }
        frames(90)
        val detached = state.value
        compose.runOnIdle { assertTrue("User scrolling takes precedence", detached < state.maxValue) }
        frames(60)
        compose.runOnIdle { assertEquals("No automatic retry after drag", detached, state.value) }
        compose.runOnIdle { height += 200 }
        frames(90)
        assertAtEnd()
    }

    @Test fun stoppingFreezesNativeFollowAndReactivationCatchesUp() {
        show()
        compose.runOnIdle { height = 1000 }
        frames(3)
        compose.runOnIdle { active = false }
        frames(2)
        val frozen = state.value
        compose.runOnIdle { height = 2000 }
        frames(90)
        compose.runOnIdle {
            assertEquals("Inactive/expanded preview cannot keep scrolling", frozen, state.value)
            assertFalse(state.isScrollInProgress)
            active = true
        }
        frames(90)
        assertAtEnd()
    }

    @Test fun expandingStopsPreviewScrollAndCollapsingFollowsTheUpdatedContent() {
        var reasoning by mutableStateOf((1..12).joinToString("\n\n") { "Thinking paragraph $it." })
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            LiveThinkingBlock(reasoning, active = true, showHeader = false)
        } }
        frames(90)
        val preview = compose.onNodeWithTag("live-thinking-preview")
        preview.assert(hasScrollAction())
        preview.performClick()
        frames(60)
        preview.assert(!hasScrollAction())
        compose.runOnIdle {
            reasoning = (1..30).joinToString("\n\n") { "Thinking paragraph $it." }
        }
        frames(60)
        preview.assert(!hasScrollAction())
        preview.performClick()
        frames(90)
        preview.assert(hasScrollAction())
        val range = preview.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        assertEquals(range.maxValue(), range.value(), 0f)
    }

    @Test fun thinkingViewportRemainsFixedWhenItsTextScrolls() {
        var reasoning by mutableStateOf((1..30).joinToString("\n\n") { "Thinking paragraph $it." })
        compose.setContent { MaterialTheme {
            LiveThinkingBlock(reasoning, active = true, showHeader = false)
        } }
        val viewport = compose.onNodeWithTag("live-thinking-viewport")
        val preview = compose.onNodeWithTag("live-thinking-preview")
        val originalViewport = viewport.fetchSemanticsNode().boundsInRoot
        fun range() = preview.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        compose.waitUntil(10_000) { range().maxValue() > 0f && range().value() == range().maxValue() }
        preview.performTouchInput {
            swipe(Offset(width / 2f, height * 0.25f), Offset(width / 2f, height * 0.85f), 300)
        }
        compose.waitForIdle()
        assertTrue("The inner Thinking viewport must scroll", range().value() < range().maxValue())
        assertEquals("The fade viewport must not move with the text",
            originalViewport, viewport.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {
            reasoning = (1..50).joinToString("\n\n") { "Thinking paragraph $it." }
        }
        compose.waitForIdle()
        assertEquals("New streaming lines cannot resize the fade viewport",
            originalViewport, viewport.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun manualThinkingSwipePausesForThreeSecondsAndASecondGestureRestartsTheDelay() {
        var reasoning by mutableStateOf((1..30).joinToString("\n\n") { "Thinking paragraph $it." })
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { LiveThinkingBlock(reasoning, active = true, showHeader = false) } }
        frames(90)
        val preview = compose.onNodeWithTag("live-thinking-preview")
        fun range() = preview.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(range().maxValue(), range().value(), 0f)
        fun swipeUpwardsThroughHistory() = preview.performTouchInput {
            swipe(Offset(width / 2f, height * 0.25f), Offset(width / 2f, height * 0.85f), 300)
        }
        swipeUpwardsThroughHistory()
        frames(3)
        assertTrue("Manual drag should detach the preview from its end", range().value() < range().maxValue())
        compose.runOnIdle {
            reasoning = (1..60).joinToString("\n\n") { "Thinking paragraph $it." }
        }
        frames(60)
        assertTrue("Content growth cannot resume follow before 3s", range().value() < range().maxValue())
        compose.mainClock.advanceTimeBy(1500)
        frames(2)
        assertTrue("Still paused before the timeout", range().value() < range().maxValue())

        // A new touch within the preview must extend the quiet period.
        swipeUpwardsThroughHistory()
        frames(2)
        compose.mainClock.advanceTimeBy(2800)
        frames(2)
        assertTrue("Second gesture resets the 3-second timer", range().value() < range().maxValue())
        compose.mainClock.advanceTimeBy(400)
        frames(90)
        assertEquals("Follow automatically returns after 3 seconds with no interaction",
            range().maxValue(), range().value(), 0f)
    }

    @Test fun realThinkingPreviewFollowsWithoutRelocatingItsScrollableParent() {
        var reasoning by mutableStateOf((1..12).joinToString("\n\n") { "Thinking paragraph $it." })
        lateinit var outer: ScrollState
        compose.setContent { MaterialTheme {
            outer = rememberScrollState()
            Column(Modifier.fillMaxWidth().height(600.dp).verticalScroll(outer)) {
                Spacer(Modifier.height(800.dp))
                LiveThinkingBlock(reasoning, active = true, showHeader = false)
                Spacer(Modifier.height(800.dp))
            }
        } }
        fun assertFollowed() {
            compose.waitUntil(10_000) {
                val range = compose.onNodeWithTag("live-thinking-preview").fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange]
                range.maxValue() > 0f && range.value() == range.maxValue()
            }
            compose.runOnIdle { assertEquals("Preview follow must not scroll chat ancestors", 0, outer.value) }
        }
        assertFollowed()
        compose.runOnIdle {
            reasoning = (1..35).joinToString("\n\n") { "Thinking paragraph $it." }
        }
        assertFollowed()
    }
}
