package org.starfall.generative.shared.ui.layout

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCoverPositionTest {
    @Test fun coverIgnoresItsAnchorAndStartsAtTheWindowOrigin() {
        val position = FullWindowPopupPositionProvider.calculatePosition(
            anchorBounds = IntRect(120, 480, 360, 720),
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(1080, 2400)
        )

        assertEquals(IntOffset.Zero, position)
    }

    @Test fun coverUsesTheSameOriginInRtl() {
        val position = FullWindowPopupPositionProvider.calculatePosition(
            anchorBounds = IntRect(40, 200, 180, 340),
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Rtl,
            popupContentSize = IntSize(1080, 2400)
        )

        assertEquals(IntOffset.Zero, position)
    }
}
