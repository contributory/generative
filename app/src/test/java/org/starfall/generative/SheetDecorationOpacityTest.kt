package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.shared.ui.sheets.sheetDecorationBrushAlpha

/** Maximum-effort decoration must not hide an active profile photo. */
class SheetDecorationOpacityTest {
    @Test
    fun noPhotoOrFullyTransparentPhotoKeepsOriginalDecoration() {
        assertEquals(1f, sheetDecorationBrushAlpha(null, 0f), 0.001f)
        assertEquals(1f, sheetDecorationBrushAlpha(null, 1f), 0.001f)
        assertEquals(1f, sheetDecorationBrushAlpha("", 0.5f), 0.001f)
        assertEquals(1f, sheetDecorationBrushAlpha("/images/photo.jpg", 1f), 0.001f)
    }

    @Test
    fun visiblePhotoKeepsDecorationPartlyTransparent() {
        assertEquals(0.32f, sheetDecorationBrushAlpha("/images/photo.jpg", 0f), 0.001f)
        assertEquals(0.32f, sheetDecorationBrushAlpha("/images/photo.jpg", 0.5f), 0.001f)
        assertEquals(0.32f, sheetDecorationBrushAlpha("/images/photo.jpg", 0.99f), 0.001f)
    }
}
