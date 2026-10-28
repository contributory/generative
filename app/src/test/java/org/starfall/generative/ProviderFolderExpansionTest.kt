package org.starfall.generative

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.features.providers.list.ui.FolderCloseDurationMillis
import org.starfall.generative.features.providers.list.ui.FolderOpenDurationMillis
import org.starfall.generative.features.providers.list.ui.folderExpansionRect

/** Ensures folder transitions stay anchored to the tapped card at a 30%-width starting size. */
class ProviderFolderExpansionTest {
    @Test
    fun opensFromThirtyPercentWidthAtTheActualFolderCenterAndFillsTheHost() {
        // The folder may be anywhere in the root list, including after scrolling.
        val card = Rect(100f, 200f, 300f, 400f)
        val full = Rect(0f, 0f, 400f, 800f)
        val collapsed = Rect(140f, 240f, 260f, 360f)
        assertEquals(collapsed, folderExpansionRect(card, 400f, 800f, 0f))
        assertEquals(Rect(70f, 120f, 330f, 580f), folderExpansionRect(card, 400f, 800f, 0.5f))
        assertEquals(full, folderExpansionRect(card, 400f, 800f, 1f))
    }

    @Test
    fun closingReversesIntoTheSamePositionWithoutEnlargingSmallTiles() {
        val card = Rect(20f, 120f, 100f, 200f)
        // The actual card is already narrower than 30% of the viewport.
        assertEquals(card, folderExpansionRect(card, 400f, 800f, 0f))
        assertEquals(Rect(0f, 0f, 400f, 800f), folderExpansionRect(null, 400f, 800f, 0f))
        assertEquals(Rect(0f, 0f, 400f, 800f), folderExpansionRect(card, 400f, 800f, 1f))
    }

    @Test
    fun openingDurationIsTwentyPercentLongerWhileClosingDurationIsUnchanged() {
        assertEquals(600, FolderOpenDurationMillis)
        assertEquals(450, FolderCloseDurationMillis)
    }
}
