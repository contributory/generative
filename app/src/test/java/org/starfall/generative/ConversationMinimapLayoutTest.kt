package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.*

class ConversationMinimapLayoutTest {
    private fun indices(vararg ids: String) = ids.withIndex().associate { it.value to it.index }

    @Test fun spanUsesRealTurnKeysAndTheReadableViewportOnly() {
        val visible = listOf(
            MinimapVisibleTurn("overscan", -200, 100),
            MinimapVisibleTurn("old", -20, 100),
            MinimapVisibleTurn("long", 80, 3000),
            MinimapVisibleTurn("boundary", 3080, 0))
        val span = minimapVisibleSpan(indices("old", "long", "new"), visible, 0, 200)!!
        assertEquals(0.2f, span.start, 1e-4f)
        assertEquals(1f + 120f / 3000f, span.end, 1e-4f)
    }

    @Test fun fractionInsideALongTurnMovesTheThumbContinuously() {
        val ids = indices("a", "b", "c", "d")
        val atTop = minimapVisibleSpan(ids, listOf(MinimapVisibleTurn("b", 0, 4000)), 0, 400)!!
        val scrolled = minimapVisibleSpan(ids, listOf(MinimapVisibleTurn("b", -2000, 4000)), 0, 400)!!
        assertTrue(scrolled.start > atTop.start)
        assertTrue(minimapThumb(4, scrolled, 0f).top > minimapThumb(4, atTop, 0f).top)
    }

    @Test fun contentGrowthOffScreenDoesNotMoveTheTurnPosition() {
        val ids = indices("old", "long", "new")
        for (height in listOf(300, 30000)) {
            val span = minimapVisibleSpan(ids, listOf(MinimapVisibleTurn("long", 0, height)), 0, 100)!!
            assertEquals(1f, span.start, 1e-4f)
        }
    }

    @Test fun thumbSizeIsTheVisibleShareAndNeverBelowTheMinimum() {
        val twoOfTen = MinimapSpan(3f, 5f)
        assertEquals(0.2f, minimapThumb(10, twoOfTen, 0f).height, 1e-4f)
        assertEquals(0.1f, minimapThumb(100, MinimapSpan(0f, 1f), 0.1f).height, 1e-4f)
        assertEquals(0.1f, minimapThumb(100, MinimapSpan(0f, 0.01f), 0.1f).height, 1e-4f)
        val everything = minimapThumb(3, MinimapSpan(0f, 3f), 0.1f)
        assertEquals(1f, everything.height, 1e-4f)
        assertEquals(0f, everything.top, 1e-4f)
    }

    @Test fun thumbReachesBothEndsOfTheTrack() {
        val first = minimapThumb(10, MinimapSpan(0f, 2f), 0.05f)
        val last = minimapThumb(10, MinimapSpan(8f, 10f), 0.05f)
        assertEquals(0f, first.top, 1e-4f)
        assertEquals(0f, first.progress, 1e-4f)
        assertEquals(1f, last.progress, 1e-4f)
        assertEquals(1f, last.top + last.height, 1e-4f)
    }

    @Test fun noSpanOrNoTurnsFillsTheTrack() {
        assertEquals(MinimapThumb(0f, 1f), minimapThumb(0, MinimapSpan(0f, 1f), 0.1f))
        assertEquals(MinimapThumb(0f, 1f), minimapThumb(5, null, 0.1f))
    }

    @Test fun draggingMapsProgressBackToATurnAndFraction() {
        assertEquals(MinimapPosition(0, 0f), minimapPosition(10, 2f, 0f))
        assertEquals(MinimapPosition(4, 0f), minimapPosition(10, 2f, 0.5f))
        val inside = minimapPosition(4, 0.25f, 0.9f)!!
        assertEquals(3, inside.index)
        assertEquals(0.375f, inside.fraction, 1e-3f)
    }

    @Test fun thumbAtTheBottomMeansTheEndOfTheLatestContent() {
        assertEquals(MinimapPosition(10, 0f), minimapPosition(10, 2.5f, 1f))
        // Even when the last turn is taller than the viewport.
        assertEquals(MinimapPosition(4, 0f), minimapPosition(4, 0.25f, 1f))
    }

    @Test fun positionAndThumbRoundTrip() {
        val span = MinimapSpan(2.4f, 3.9f)
        val thumb = minimapThumb(8, span, 0f)
        val target = minimapPosition(8, span.length, thumb.progress)!!
        assertEquals(span.start, target.index + target.fraction, 1e-3f)
    }

    @Test fun rejectsEmptyAndInvalidInput() {
        assertNull(minimapPosition(0, 1f, 0.5f))
        assertNull(minimapPosition(3, 1f, Float.NaN))
        assertNull(minimapPosition(3, Float.POSITIVE_INFINITY, 0.5f))
        val rows = listOf(MinimapVisibleTurn("deleted", 0, 100), MinimapVisibleTurn("new", 100, 100))
        assertNull(minimapVisibleSpan(indices("new"), rows, 0, 100))
        assertNotNull(minimapVisibleSpan(indices("new"), rows, 100, 200))
        assertNull(minimapVisibleSpan(indices("new"), rows, 200, 100))
    }
}
