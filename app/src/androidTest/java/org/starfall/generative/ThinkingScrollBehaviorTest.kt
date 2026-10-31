package org.starfall.generative

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.rememberLiveThinkingScrollState

class ThinkingScrollBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeThinkingScrollFollowsMeasuredGrowthAndStopsWhenInactive() {
        compose.mainClock.autoAdvance = false
        var text by mutableStateOf("Starting")
        var active by mutableStateOf(true)
        lateinit var scroll: ScrollState
        compose.setContent { MaterialTheme {
            scroll = rememberLiveThinkingScrollState(active)
            Box(Modifier.width(240.dp).height(48.dp).verticalScroll(scroll)) { Text(text) }
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, scroll.value) }
        compose.runOnIdle { text = (1..8).joinToString("\n") { "Thinking line $it" } }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue("New content should cause a native scroll", scroll.value > 0)
            // The native ScrollState jumps directly to the newly measured bottom.
            // LiveThinkingBlock separately animates its drawn offset for visual smoothness.
            assertEquals("Native scroll should follow actual layout growth", scroll.maxValue, scroll.value)
        }
        compose.runOnIdle { text += "\nA newer line\nAnother new line" }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(scroll.maxValue, scroll.value) }
        var stoppedAt = 0
        compose.runOnIdle { active = false; stoppedAt = scroll.value }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { text += "\nAn inactive block should not auto-scroll" }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(stoppedAt, scroll.value) }
    }
}
