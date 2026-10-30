package org.starfall.generative.extensions.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class LauncherDragRobustnessTest {
    private val bounds = Rect(0f, 0f, 100f, 100f)
    private fun source(state: LauncherDragDropState<String>, end: (LauncherDragOutcome) -> Unit = {}): Any {
        val token = Any()
        state.putSource(LauncherSource(token, LauncherDragItem("item", "item"), 0, true,
            bounds, {}, end))
        return token
    }

    @Test fun clippedSourceKeepsItsFullShadowAndTouchAnchor() {
        val state = LauncherDragDropState<String>()
        val token = source(state)
        state.sourceBounds(token, Rect(0f, -50f, 100f, 50f), Rect(0f, 0f, 100f, 50f))
        assertNull(state.sourceHitAt(Offset(50f, -25f)))
        assertTrue(state.start(Offset(20f, 25f)))
        state.move(Offset(120f, 225f))
        assertEquals(Rect(100f, 150f, 200f, 250f), state.session!!.shadowBounds)
    }

    @Test fun artworkCenterChoosesFolderEvenWhenFingerIsOverAnotherCell() {
        val state = LauncherDragDropState<String>()
        val token = source(state)
        state.putSource(LauncherSource(token, LauncherDragItem("item", "item"), 0, true,
            onStart = {}, onEnd = {}, dragRegion = Rect(20f, 0f, 80f, 60f)))
        state.putTarget(LauncherTarget(Any(), "folder", 5, true, Rect(100f, 0f, 200f, 70f),
            { true }, {}, {}, {}, { true }, hitTest = LauncherHitTest.VisualCenter))
        state.start(Offset(5f, 90f))
        state.move(Offset(105f, 90f))
        assertEquals(Offset(150f, 30f), state.session!!.visualCenter)
        assertEquals("folder", state.hoveredTargetKey)
        assertTrue(state.drop().accepted)
    }

    @Test fun containerHitTestingStillUsesPointerRatherThanIconCenter() {
        val state = LauncherDragDropState<String>()
        source(state)
        state.putTarget(LauncherTarget(Any(), "container", 0, true, Rect(0f, 0f, 30f, 100f),
            { true }, {}, {}, {}, { true }))
        state.start(Offset(10f, 90f))
        assertEquals("container", state.hoveredTargetKey)
    }

    @Test fun completionIsImmediateWhileLandingKeepsOnlySourceHidden() {
        val state = LauncherDragDropState<String>()
        var calls = 0
        source(state) { calls++ }
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true, bounds,
            { true }, {}, {}, {}, { true }))
        state.start(Offset(20f, 20f))
        assertTrue(state.drop().accepted)
        assertEquals(1, calls)
        assertNull(state.session)
        assertTrue(state.isDragging("item"))
        assertFalse(state.isDragging("other"))
        state.clearSettlement()
        assertFalse(state.isDragging("item"))
    }

    @Test fun newDragInterruptsLandingWithoutRepeatingCompletion() {
        val state = LauncherDragDropState<String>()
        var calls = 0
        source(state) { calls++ }
        state.start(Offset(20f, 20f))
        state.cancel()
        val oldTail = state.settlement!!
        assertTrue(state.start(Offset(20f, 20f)))
        assertNull(state.settlement)
        state.clearSettlement(oldTail)
        assertNotNull(state.session)
        assertEquals(1, calls)
    }

    @Test fun synchronousFolderRemovalFromEnterDoesNotLeaveStaleTarget() {
        val state = LauncherDragDropState<String>()
        source(state)
        val folder = Any()
        var exits = 0
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true, bounds,
            { true }, {}, {}, {}, { true }))
        state.putTarget(LauncherTarget(folder, "folder", 5, true, bounds,
            { true }, { state.removeTarget(folder) }, {}, { exits++ }, { true }))
        state.start(Offset(20f, 20f))
        assertEquals("workspace", state.hoveredTargetKey)
        assertEquals(1, exits)
        assertEquals("workspace", state.drop().targetKey)
    }

    @Test fun dropExceptionClearsGestureAndRollsBackExactlyOnce() {
        val state = LauncherDragDropState<String>()
        val outcomes = mutableListOf<LauncherDragOutcome>()
        source(state) { outcomes += it }
        state.putTarget(LauncherTarget(Any(), "bad", 0, true, bounds,
            { true }, {}, {}, {}, { error("Rejected by application") }))
        state.start(Offset(20f, 20f))
        assertThrows(IllegalStateException::class.java) { state.drop() }
        assertNull(state.session)
        assertEquals(listOf(LauncherDragOutcome(false, null)), outcomes)
        state.cancel()
        assertEquals(1, outcomes.size)
    }

    @Test fun exitExceptionStillCompletesTheSource() {
        val state = LauncherDragDropState<String>()
        var calls = 0
        source(state) { calls++ }
        state.putTarget(LauncherTarget(Any(), "bad", 0, true, bounds,
            { true }, {}, {}, { error("exit") }, { true }))
        state.start(Offset(20f, 20f))
        assertThrows(IllegalStateException::class.java) { state.cancel() }
        assertEquals(1, calls)
        assertNull(state.session)
    }

    @Test fun completionUsesLatestRegisteredCallbackButSurvivesSourceDisposal() {
        val state = LauncherDragDropState<String>()
        var value = ""
        val token = source(state) { value = "old" }
        state.start(Offset(20f, 20f))
        state.putSource(LauncherSource(token, LauncherDragItem("item", "item"), 0, true,
            onStart = {}, onEnd = { value = "latest" }))
        state.cancel()
        assertEquals("latest", value)
        state.start(Offset(20f, 20f))
        state.removeSource(token)
        state.cancel()
        assertEquals("latest", value)
    }

    @Test fun backgroundDropPreservesThePreviewedInsertionAndRelativeSiblingOrder() {
        val layout = LauncherReorderLayout({ it }, mapOf("root" to listOf("a", "b", "c", "d"),
            "folder" to listOf("x", "y")))
        layout.begin("a")
        layout.hover("root", "c", after = true)
        layout.enter("root")
        assertEquals(listOf("b", "c", "a", "d"), layout.preview["root"])
        layout.hover("folder", "y", after = false)
        layout.enter("folder")
        assertEquals(listOf("x", "a", "y"), layout.commit()!!["folder"])
    }

    @Test fun rtlInsertionUsesReadingOrderAndDuplicateIdsAreRejectedGlobally() {
        val cell = listOf(LauncherCell("a", bounds))
        assertEquals(LauncherReorderHit("a", false),
            LauncherGridGeometry.closestSlot(Offset(80f, 50f), cell, horizontal = true, rtl = true))
        val layout = LauncherReorderLayout({ it }, mapOf("root" to listOf("a", "b", "b")))
        assertFalse(layout.begin("a"))
    }

    @Test fun nonFinitePointerCannotCorruptAnActiveShadow() {
        val state = LauncherDragDropState<String>()
        source(state)
        assertFalse(state.start(Offset(Float.NaN, 10f)))
        assertTrue(state.start(Offset(20f, 20f)))
        state.move(Offset(Float.POSITIVE_INFINITY, 0f))
        assertEquals(Offset(20f, 20f), state.session!!.pointer)
    }
    @Test fun scaledArtworkRegionAndFolderExitUseVisualCenter() {
        val state = LauncherDragDropState<String>()
        val token = source(state)
        state.putSource(LauncherSource(token, LauncherDragItem("item", "item"), 0, true,
            onStart = {}, onEnd = {}, dragRegion = Rect(20f, 0f, 80f, 60f)))
        state.sourceBounds(token, Rect(0f, 0f, 200f, 200f), localSize = androidx.compose.ui.geometry.Size(100f, 100f))
        state.putTarget(LauncherTarget(Any(), "folder", 0, true, Rect(0f, 0f, 200f, 100f),
            { true }, {}, {}, {}, { true }))
        state.start(Offset(10f, 180f))
        assertEquals(Offset(100f, 60f), state.session!!.visualCenter)
        assertTrue(state.isVisualCenterInside("folder"))
        assertFalse(state.isPointerInside("folder"))
    }

    @Test fun dropCallbackCannotFinishANewGestureItStartedReentrantly() {
        val state = LauncherDragDropState<String>()
        var calls = 0
        source(state) { calls++ }
        state.putTarget(LauncherTarget(Any(), "target", 0, true, bounds,
            { true }, {}, {}, {}, {
                state.cancel()
                state.start(Offset(30f, 30f))
                true
            }))
        state.start(Offset(20f, 20f))
        assertFalse(state.drop().accepted)
        assertEquals(1, calls)
        assertEquals(Offset(30f, 30f), state.session!!.pointer)
    }

    @Test fun repeatedCrossContainerPreviewsNeverLoseDuplicateOrShuffleSiblings() {
        val initial = mapOf("root" to listOf("a", "b", "c"), "one" to listOf("d", "e"),
            "two" to listOf("f", "g"))
        val layout = LauncherReorderLayout({ it }, initial)
        val random = java.util.Random(42)
        layout.begin("a")
        repeat(200) {
            val destination = initial.keys.toList()[random.nextInt(3)]
            val candidates = layout.preview.getValue(destination).filterNot { it == "a" }
            val target = candidates.getOrNull(if (candidates.isEmpty()) 0 else random.nextInt(candidates.size))
            layout.hover(destination, target, random.nextBoolean())
            assertEquals(initial.values.flatten().sorted(), layout.preview.values.flatten().sorted())
            for (key in initial.keys) {
                assertEquals(initial.getValue(key).filterNot { it == "a" },
                    layout.preview.getValue(key).filterNot { it == "a" })
            }
        }
        layout.cancel()
        assertEquals(initial, layout.preview)
    }
    @Test fun explicitLandingAreaFollowsTheTargetsLatestMeasuredBounds() {
        val state = LauncherDragDropState<String>()
        source(state)
        var destination = bounds
        state.putTarget(LauncherTarget(Any(), "folder", 0, true, bounds,
            { true }, {}, {}, {}, { true }, landingBounds = { destination }))
        state.start(Offset(20f, 20f))
        state.drop()
        destination = Rect(200f, 100f, 250f, 150f)
        assertEquals(destination, state.settlement!!.landingBounds!!.invoke())
    }
    @Test fun cancellationCanReturnToFolderIconAfterTheOriginalTileIsUnmounted() {
        val state = LauncherDragDropState<String>()
        val token = source(state)
        val icon = Rect(200f, 0f, 250f, 50f)
        state.putSource(LauncherSource(token, LauncherDragItem("item", "item"), 0, true,
            onStart = {}, onEnd = {}, returnBounds = { icon }))
        state.start(Offset(20f, 20f))
        state.removeSource(token)
        state.cancel()
        assertFalse(state.settlement!!.accepted)
        assertNull(state.sourceRect("item"))
        assertEquals(icon, state.settlement!!.returnBounds!!.invoke())
    }
}
