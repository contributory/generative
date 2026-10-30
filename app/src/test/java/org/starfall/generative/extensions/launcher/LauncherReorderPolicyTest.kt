package org.starfall.generative.extensions.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class LauncherReorderPolicyTest {
    private val bounds = Rect(100f, 0f, 200f, 100f)
    private fun drag(id: String, x: Float = 110f, y: Float = 50f) = LauncherDragSession(
        LauncherDragItem(id, id), Rect(0f, 0f, 100f, 100f), Offset(50f, 50f), Offset(x, y))
    private fun layout() = LauncherReorderLayout({ it }, mapOf("root" to listOf("a", "b", "c")))

    @Test fun entryMovesForwardWithoutCrossingTheTargetsCenter() {
        val layout = layout()
        layout.begin("a")
        assertTrue(layout.hover("root", "b", drag("a"), bounds, horizontal = true))
        assertEquals(listOf("b", "a", "c"), layout.preview["root"])
    }

    @Test fun entryMovesBackwardEvenInTheTargetsFarHalf() {
        val layout = layout()
        layout.begin("c")
        assertTrue(layout.hover("root", "b", drag("c", 190f), bounds, horizontal = true))
        assertEquals(listOf("a", "c", "b"), layout.preview["root"])
    }

    @Test fun crossingTheCenterRemainsAnExplicitOptInRule() {
        val layout = layout()
        layout.begin("a")
        assertFalse(layout.hover("root", "b", drag("a"), bounds, LauncherReorderPolicy.CrossCenter, horizontal = true))
        assertTrue(layout.hover("root", "b", drag("a", 190f), bounds, LauncherReorderPolicy.CrossCenter, horizontal = true))
        assertEquals(listOf("b", "a", "c"), layout.preview["root"])
    }

    @Test fun listEntryDoesNotReserveTheUpperHalfAsADeadRegion() {
        val layout = layout()
        layout.begin("a")
        assertTrue(layout.hover("root", "b", drag("a", 150f, 5f), bounds))
        assertEquals(listOf("b", "a", "c"), layout.preview["root"])
    }

    @Test fun crossContainerEntryUsesTheChosenSideAndRtlReadingOrder() {
        val layout = LauncherReorderLayout({ it }, mapOf("root" to listOf("a"), "folder" to listOf("b", "c")))
        layout.begin("a")
        layout.hover("folder", "b", drag("a", 110f), bounds, horizontal = true, rtl = true)
        assertEquals(listOf("b", "a", "c"), layout.preview["folder"])
        layout.cancel()
        assertEquals(listOf("a"), layout.preview["root"])
    }

    @Test fun fixedSidesAreIndependentOfPointerPosition() {
        val layout = layout()
        layout.begin("a")
        layout.hover("root", "c", drag("a"), bounds, LauncherReorderPolicy.After)
        assertEquals(listOf("b", "c", "a"), layout.preview["root"])
        layout.hover("root", "b", drag("a", 190f), bounds, LauncherReorderPolicy.Before)
        assertEquals(listOf("a", "b", "c"), layout.preview["root"])
    }

    @Test fun customRuleReceivesCurrentPreviewIndices() {
        val layout = layout()
        layout.begin("a")
        layout.hover("root", "c", after = true)
        var captured: LauncherReorderContext? = null
        layout.hover("root", "b", drag("a"), bounds, LauncherReorderPolicy.Custom { captured = it; false })
        assertEquals(2, captured?.sourceIndex)
        assertEquals(0, captured?.targetIndex)
        assertEquals(listOf("a", "b", "c"), layout.preview["root"])
    }

    @Test fun invalidGeometryOrDifferentDragCannotMutateThePreview() {
        val layout = layout()
        layout.begin("a")
        assertFalse(layout.hover("root", "b", drag("wrong"), bounds))
        assertFalse(layout.hover("root", "b", drag("a"), Rect.Zero))
        assertEquals(listOf("a", "b", "c"), layout.preview["root"])
    }
    @Test fun nearestSlotIsBoundedByDistanceToItsActualSurface() {
        val cells = listOf(LauncherCell("b", bounds))
        assertNull(LauncherGridGeometry.closestSlot(Offset(50f, 50f), cells, true, maxDistancePx = 12f))
        assertEquals("b", LauncherGridGeometry.closestSlot(Offset(95f, 50f), cells, true, maxDistancePx = 6f)?.key)
        assertNull(LauncherGridGeometry.closestSlot(Offset(93f, 50f), cells, true, maxDistancePx = 6f))
    }

    @Test fun aPointInsideAWideItemCannotChooseAFartherItemsCloserCenter() {
        val cells = listOf(LauncherCell("wide", Rect(0f, 0f, 400f, 100f)),
            LauncherCell("small", Rect(410f, 0f, 450f, 100f)))
        assertEquals("wide", LauncherGridGeometry.closestSlot(Offset(390f, 50f), cells, true)?.key)
    }

    @Test fun zeroProximityRequiresContactAndInvalidDistancesRejectTheSearch() {
        val cells = listOf(LauncherCell("b", bounds))
        assertNull(LauncherGridGeometry.closestSlot(Offset(99f, 50f), cells, true, maxDistancePx = 0f))
        assertEquals("b", LauncherGridGeometry.closestSlot(Offset(100f, 50f), cells, true, maxDistancePx = 0f)?.key)
        assertNull(LauncherGridGeometry.closestSlot(Offset(150f, 50f), cells, true, maxDistancePx = Float.NaN))
    }

}
