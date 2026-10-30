package org.starfall.generative.extensions.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class LauncherDragPolicyTest {
    private val bounds = Rect(0f, 0f, 100f, 100f)
    private fun drag(x: Float, y: Float) = LauncherDragSession(
        LauncherDragItem("tile", "tile"), bounds, bounds.center, Offset(x, y))

    @Test fun folderCenterLeavesItsEdgesAvailableForReordering() {
        val hit = LauncherHitTest.Center()
        assertTrue(hit.contains(drag(50f, 50f), bounds))
        assertFalse(hit.contains(drag(10f, 50f), bounds))
        assertFalse(hit.contains(drag(50f, 90f), bounds))
        assertTrue(LauncherHitTest.VisualCenter.contains(drag(10f, 50f), bounds))
    }

    @Test fun centerFractionsAreIndependentForWideListRows() {
        val row = Rect(0f, 0f, 400f, 80f)
        val hit = LauncherHitTest.Center(1f, 0.5f)
        assertTrue(hit.contains(drag(10f, 40f), row))
        assertFalse(hit.contains(drag(200f, 10f), row))
    }

    @Test fun overlapRequiresActualArtworkCoverageRatherThanEdgeContact() {
        val hit = LauncherHitTest.Overlap(0.5f)
        assertTrue(hit.contains(drag(100f, 50f), bounds))
        assertFalse(hit.contains(drag(101f, 50f), bounds))
        assertFalse(hit.contains(drag(150f, 50f), bounds))
        val artwork = drag(100f, 50f).copy(dragRegion = Rect(25f, 25f, 75f, 75f))
        assertTrue(hit.contains(artwork, bounds))
    }

    @Test fun customShapeCanRejectRectangleCorners() {
        val circle = LauncherHitTest.Custom { session, target ->
            (session.visualCenter - target.center).getDistance() < target.width / 2
        }
        assertTrue(circle.contains(drag(50f, 50f), bounds))
        assertFalse(circle.contains(drag(1f, 1f), bounds))
        assertFalse(circle.contains(drag(0f, 0f), Rect.Zero))
    }

    @Test fun invalidCollisionAndAnimationOptionsFailEarly() {
        assertThrows(IllegalArgumentException::class.java) { LauncherHitTest.Center(0f) }
        assertThrows(IllegalArgumentException::class.java) { LauncherHitTest.Center(heightFraction = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { LauncherHitTest.Overlap(1.1f) }
        assertThrows(IllegalArgumentException::class.java) { LauncherDragStyle(pickupScale = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { LauncherDragActivation.LongPress(-1) }
    }

    @Test fun logicalSlotWinsOverAnAnimatedTilesTemporaryDrawingBounds() {
        val state = LauncherDragDropState<String>()
        state.putSource(LauncherSource(Any(), LauncherDragItem("tile", "tile"), 0, true, bounds, {}, {}))
        state.putTarget(LauncherTarget(Any(), "slot", 0, true, Rect(200f, 0f, 300f, 100f),
            { true }, {}, {}, {}, { true }, hitBounds = bounds))
        state.start(Offset(50f, 50f))
        assertEquals("slot", state.hoveredTargetKey)
        state.move(Offset(250f, 50f))
        assertNull(state.hoveredTargetKey)
    }

    @Test fun sourceHandleUsesLocalCoordinatesAndScalesWithTheSource() {
        val state = LauncherDragDropState<String>()
        val source = Any()
        state.putSource(LauncherSource(source, LauncherDragItem("tile", "tile"), 0, true,
            bounds, {}, {}, handleBounds = Rect(0f, 0f, 20f, 100f)))
        state.sourceBounds(source, Rect(100f, 200f, 300f, 400f), localSize = bounds.size)
        assertNull(state.sourceHitAt(Offset(150f, 250f)))
        assertTrue(state.start(Offset(120f, 250f)))
        assertEquals(Rect(100f, 200f, 300f, 400f), state.session!!.sourceBounds)
    }
}
