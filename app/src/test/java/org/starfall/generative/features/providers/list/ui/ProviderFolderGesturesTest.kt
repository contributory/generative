package org.starfall.generative.features.providers.list.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFolderGesturesTest {
    private val viewport = Rect(0f, 0f, 400f, 900f)
    private val header = Rect(0f, 24f, 400f, 140f)

    private fun dismiss(from: Offset, to: Offset) = folderDismissSwipe(
        from, to, viewport, header, edgeWidth = 56f, minDistance = 56f
    )

    @Test fun swipingInFromEitherSideDismisses() {
        assertTrue(dismiss(Offset(30f, 450f), Offset(120f, 450f)))
        assertTrue(dismiss(Offset(370f, 450f), Offset(280f, 450f)))
    }

    @Test fun headerSupportsHorizontalAndDownwardSwipes() {
        assertTrue(dismiss(Offset(200f, 70f), Offset(300f, 75f)))
        assertTrue(dismiss(Offset(200f, 70f), Offset(100f, 75f)))
        assertTrue(dismiss(Offset(200f, 70f), Offset(200f, 190f)))
    }

    @Test fun ordinaryScrollingAndOppositeDirectionDoNotDismiss() {
        assertFalse(dismiss(Offset(200f, 500f), Offset(200f, 280f)))
        assertFalse(dismiss(Offset(200f, 500f), Offset(300f, 500f)))
        assertFalse(dismiss(Offset(30f, 450f), Offset(-60f, 450f)))
        assertFalse(dismiss(Offset(370f, 450f), Offset(460f, 450f)))
        assertFalse(dismiss(Offset(30f, 450f), Offset(75f, 450f)))
        assertFalse(dismiss(Offset(30f, 450f), Offset(130f, 600f)))
        assertFalse(dismiss(Offset(200f, 70f), Offset(200f, -60f)))
    }
}
