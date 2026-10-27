package org.starfall.generative

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.features.providers.list.ui.folderExpansionRect

/** Ensures the animated page uses the exact clicked card as its source and destination. */
class ProviderFolderExpansionTest {
    @Test
    fun opensFromTheActualFolderBoundsAndFillsTheHost() {
        // The folder can be anywhere in a scrolled root list (not necessarily at the top).
        val card = Rect(185f, 440f, 350f, 590f)
        val full = Rect(0f, 0f, 420f, 960f)
        assertEquals(card, folderExpansionRect(card, 420f, 960f, 0f))
        assertEquals(Rect(92.5f, 220f, 385f, 775f), folderExpansionRect(card, 420f, 960f, 0.5f))
        assertEquals(full, folderExpansionRect(card, 420f, 960f, 1f))
    }

    @Test
    fun closingReversesIntoTheSameCardAndHandlesMissingCard() {
        val card = Rect(20f, 120f, 190f, 250f)
        assertEquals(card, folderExpansionRect(card, 400f, 800f, 0f))
        assertEquals(Rect(0f, 0f, 400f, 800f), folderExpansionRect(null, 400f, 800f, 0f))
        assertEquals(Rect(0f, 0f, 400f, 800f), folderExpansionRect(card, 400f, 800f, 1f))
    }
}
