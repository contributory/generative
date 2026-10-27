package org.starfall.generative

import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.*

class ConversationMinimapLayoutTest {
    private val spans = minimapSpans(listOf("old" to 100f, "long" to 300f, "new" to 100f))

    @Test fun mapsChronologicalPositionsToReversedListAndWithinMessageOffsets() {
        assertEquals(MinimapTarget("old", 2, 0f), minimapTarget(spans, -1f))
        assertEquals(MinimapTarget("new", 0, 1f), minimapTarget(spans, 2f))
        val middle = minimapTarget(spans, 0.5f)!!
        assertEquals("long", middle.id)
        assertEquals(1, middle.reverseIndex)
        assertEquals(0.5f, middle.fraction, 0.0001f)
        assertEquals("long", minimapTarget(spans, 0.2f)!!.id)
    }

    @Test fun handlesEmptyAndInvalidPositions() {
        assertTrue(minimapSpans(emptyList()).isEmpty())
        assertNull(minimapTarget(emptyList(), 0f))
        assertNull(minimapTarget(spans, Float.NaN))
        val invalid = minimapSpans(listOf("a" to Float.NaN, "b" to -5f))
        assertEquals(0.5f, invalid[0].end, 0f)
    }

    @Test fun viewportClipsReverseItemsAndIgnoresNonMessageRows() {
        val visible = listOf(
            MinimapVisibleItem("new", -40, 100),
            MinimapVisibleItem("long", 60, 300),
            MinimapVisibleItem("warning", 360, 80)
        )
        val viewport = minimapViewport(spans, visible, 0, 160)!!
        assertEquals(0.6f, viewport.start, 0.0001f)
        assertEquals(0.92f, viewport.endInclusive, 0.0001f)
    }

    @Test fun viewportTracksPositionWithinOneLongMessage() {
        val viewport = minimapViewport(spans, listOf(MinimapVisibleItem("long", -100, 300)), 0, 100)!!
        assertEquals(0.4f, viewport.start, 0.0001f)
        assertEquals(0.6f, viewport.endInclusive, 0.0001f)
        assertNull(minimapViewport(spans, emptyList(), 0, 100))
        assertNull(minimapViewport(spans, emptyList(), 100, 0))
    }

    @Test fun scrollCentersPointsInsideLongMessages() {
        assertEquals(900, minimapScrollOffset(2000, 0.5f, 200))
        assertEquals(1900, minimapScrollOffset(2000, 0f, 200))
        assertEquals(0, minimapScrollOffset(2000, 1f, 200))
    }

    @Test fun appendedOrRemovedMessagesResolveTheirCurrentReverseIndex() {
        val appended = minimapSpans(listOf("old" to 100f, "long" to 300f, "new" to 100f, "queued" to 100f))
        assertEquals(3, minimapTarget(appended, 0f)!!.reverseIndex)
        val deleted = minimapSpans(listOf("long" to 300f, "new" to 100f))
        assertEquals("long", minimapTarget(deleted, 0f)!!.id)
        assertEquals(1, minimapTarget(deleted, 0f)!!.reverseIndex)
    }
}
