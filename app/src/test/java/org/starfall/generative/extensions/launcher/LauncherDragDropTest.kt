package org.starfall.generative.extensions.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDragDropTest {
    @Test fun sourceCanDisappearWhileDraggingFromFolderToWorkspace() {
        val state = LauncherDragDropState<String>()
        val source = Any()
        var completion: LauncherDragOutcome? = null
        var entered = 0
        state.putSource(LauncherSource(source, LauncherDragItem("provider_a", "a"), 0, true,
            Rect(0f, 0f, 100f, 100f), {}, { completion = it }))
        val folderTarget = Any()
        state.putTarget(LauncherTarget(folderTarget, "folder", 1, true,
            Rect(0f, 0f, 100f, 100f), { true }, {}, {}, {}, { true }))
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true,
            Rect(100f, 0f, 400f, 400f), { true },
            { entered++ }, {}, {}, { true }))

        assertTrue(state.start(Offset(25f, 30f)))
        state.removeSource(source) // The open folder is unmounted.
        state.removeTarget(folderTarget)
        state.move(Offset(200f, 150f))

        assertEquals("provider_a", state.session?.item?.key)
        assertEquals("workspace", state.hoveredTargetKey)
        assertEquals(Rect(175f, 120f, 275f, 220f), state.session?.shadowBounds)
        assertEquals(1, entered)
        assertTrue(state.drop().accepted)
        assertEquals(LauncherDragOutcome(true, "workspace"), completion)
        assertNull(state.session)
    }

    @Test fun sourceIdentityIsCapturedOnTouchDownBeforeLayoutMoves() {
        val state = LauncherDragDropState<String>()
        val first = Any()
        val second = Any()
        state.putSource(LauncherSource(first, LauncherDragItem("a", "a"), 0, true,
            Rect(0f, 0f, 50f, 50f), {}, {}))
        state.putSource(LauncherSource(second, LauncherDragItem("b", "b"), 0, true,
            Rect(60f, 0f, 110f, 50f), {}, {}))
        val captured = state.sourceHitAt(Offset(25f, 25f))!!
        state.sourceBounds(first, Rect(60f, 0f, 110f, 50f))
        state.sourceBounds(second, Rect(0f, 0f, 50f, 50f))
        assertTrue(state.start(captured, Offset(25f, 25f), Offset(25f, 25f)))
        assertEquals("a", state.session?.item?.key)
        assertEquals(Rect(0f, 0f, 50f, 50f), state.session?.sourceBounds)
    }

    @Test fun specificTargetWinsAndRejectedDropRestoresOwnershipToSource() {
        val state = LauncherDragDropState<Int>()
        var final: LauncherDragOutcome? = null
        state.putSource(LauncherSource(Any(), LauncherDragItem("1", 1), 0, true,
            Rect(0f, 0f, 40f, 40f), {}, { final = it }))
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true,
            Rect(0f, 0f, 400f, 400f), { true }, {}, {}, {}, { true }))
        state.putTarget(LauncherTarget(Any(), "tile", 0, true,
            Rect(0f, 0f, 50f, 50f), { true }, {}, {}, {}, { false }))
        assertTrue(state.start(Offset(15f, 15f)))
        assertEquals("tile", state.hoveredTargetKey)
        assertFalse(state.drop().accepted)
        assertEquals(LauncherDragOutcome(false, null), final)
    }

    @Test fun cancellationEndsOnlyOnce() {
        val state = LauncherDragDropState<String>()
        var completions = 0
        state.putSource(LauncherSource(Any(), LauncherDragItem("a", "value"), 0, true,
            Rect(0f, 0f, 20f, 20f), {}, { completions++ }))
        state.start(Offset(10f, 10f))
        state.cancel()
        state.cancel()
        assertEquals(1, completions)
    }

    @Test fun reorderAcrossFoldersCommitsOnceAndCancellationRollsBack() {
        val layout = LauncherReorderLayout({ it },
            mapOf("workspace" to listOf("folder", "b"),
                "folder" to listOf("a", "c")))
        assertTrue(layout.begin("a"))
        assertTrue(layout.hover("workspace", "b", after = false))
        assertEquals(listOf("folder", "a", "b"), layout.preview["workspace"])
        assertEquals(listOf("c"), layout.preview["folder"])
        layout.cancel()
        assertEquals(listOf("a", "c"), layout.preview["folder"])
        assertEquals(listOf("folder", "b"), layout.preview["workspace"])

        assertTrue(layout.begin("a"))
        assertTrue(layout.hover("workspace", "b", after = true))
        val result = layout.commit()
        assertEquals(listOf("folder", "b", "a"), result?.get("workspace"))
        assertEquals(listOf("c"), result?.get("folder"))
        assertNull(layout.draggedKey)
        assertNull(layout.commit())
    }

    @Test fun hoverRejectsMissingTargetAndDuplicateKeys() {
        val layout = LauncherReorderLayout({ it },
            mapOf("root" to listOf("a", "b"), "folder" to emptyList<String>()))
        assertTrue(layout.begin("a"))
        assertFalse(layout.hover("missing"))
        assertFalse(layout.hover("root", "unavailable"))
        assertTrue(layout.hover("folder"))
        assertEquals(listOf("a"), layout.preview["folder"])
        assertTrue(layout.hover("root", "b", after = false))
        assertEquals(listOf("a", "b"), layout.preview["root"])

        val duplicate = LauncherReorderLayout({ it }, mapOf("root" to listOf("a", "a")))
        assertFalse(duplicate.begin("a"))
    }

    @Test fun gridUsesMeasuredVisualCentersNotRowIndex() {
        val cells = listOf(
            LauncherCell("left", Rect(0f, 0f, 90f, 100f)),
            LauncherCell("right", Rect(120f, 0f, 200f, 100f))
        )
        assertEquals(LauncherReorderHit("right", false),
            LauncherGridGeometry.closestSlot(Offset(130f, 35f), cells, horizontal = true))
        assertEquals(LauncherReorderHit("right", true),
            LauncherGridGeometry.closestSlot(Offset(190f, 35f), cells, horizontal = true))
        assertNull(LauncherGridGeometry.closestSlot(Offset.Zero, emptyList(), horizontal = false))
    }

    @Test fun pointerEventsFollowTheCurrentTargetAndDropCompletesOnce() {
        val state = LauncherDragDropState<String>()
        val events = mutableListOf<String>()
        state.putSource(LauncherSource(Any(), LauncherDragItem("tile", "tile"), 0, true,
            Rect(0f, 0f, 100f, 100f),
            { events += "start" }, { events += "finish:${it.accepted}:${it.targetKey}" }))
        state.putTarget(LauncherTarget(Any(), "folder", 5, true,
            Rect(0f, 0f, 120f, 120f), { true },
            { events += "folder:enter" }, { events += "folder:move" },
            { events += "folder:exit" }, { events += "folder:drop"; true }))
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true,
            Rect(120f, 0f, 400f, 400f), { true },
            { events += "workspace:enter" }, { events += "workspace:move" },
            { events += "workspace:exit" }, { events += "workspace:drop"; true }))

        assertTrue(state.start(Offset(40f, 40f)))
        state.move(Offset(60f, 60f))
        state.move(Offset(180f, 100f))
        assertEquals(LauncherDragOutcome(true, "workspace"), state.drop())
        assertEquals(listOf(
            "start", "folder:enter", "folder:move", "folder:exit",
            "workspace:enter", "workspace:move", "workspace:drop",
            "workspace:exit", "finish:true:workspace"
        ), events)
        assertNull(state.session)
        assertEquals(LauncherDragOutcome(false, null), state.drop())
    }

    @Test fun removingHoveredFolderRetargetsWorkspaceWithoutRestartingTheGesture() {
        val state = LauncherDragDropState<String>()
        val folder = Any()
        var begins = 0
        var finishes = 0
        state.putSource(LauncherSource(Any(), LauncherDragItem("member", "member"), 0, true,
            Rect(10f, 10f, 90f, 90f), { begins++ }, { finishes++ }))
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true,
            Rect(0f, 0f, 400f, 400f), { true }, {}, {}, {}, { true }))
        state.putTarget(LauncherTarget(folder, "folder-region", 20, true,
            Rect(0f, 0f, 200f, 200f), { true }, {}, {}, {}, { true }))
        assertTrue(state.start(Offset(40f, 40f)))
        assertTrue(state.isPointerInside("folder-region"))
        assertEquals("folder-region", state.hoveredTargetKey)

        state.removeTarget(folder) // Simulates the closing folder's disposal.
        assertEquals("member", state.session?.item?.key)
        assertEquals("workspace", state.hoveredTargetKey)
        assertFalse(state.isPointerInside("folder-region"))
        state.move(Offset(300f, 300f))
        assertTrue(state.drop().accepted)
        assertEquals(1, begins)
        assertEquals(1, finishes)
    }

    @Test fun rejectingOrDisablingTargetsDoesNotFallThroughToAnInvalidDrop() {
        val state = LauncherDragDropState<String>()
        val source = Any()
        state.putSource(LauncherSource(source, LauncherDragItem("tile", "tile"), 0, false,
            Rect(0f, 0f, 80f, 80f), {}, {}))
        assertFalse(state.start(Offset(40f, 40f)))
        state.putSource(LauncherSource(source, LauncherDragItem("tile", "tile"), 0, true,
            onStart = {}, onEnd = {}))
        val forbidden = Any()
        state.putTarget(LauncherTarget(forbidden, "forbidden", 20, true,
            Rect(0f, 0f, 80f, 80f), { it.key != "tile" }, {}, {}, {}, { true }))
        state.putTarget(LauncherTarget(Any(), "workspace", 0, false,
            Rect(0f, 0f, 200f, 200f), { true }, {}, {}, {}, { true }))
        assertTrue(state.start(Offset(40f, 40f)))
        assertNull(state.hoveredTargetKey)
        assertEquals(LauncherDragOutcome(false, null), state.drop())
    }

    @Test fun cancellingAfterHoverCallsExitAndCompletesSourceExactlyOnce() {
        val state = LauncherDragDropState<String>()
        var entered = 0
        var exited = 0
        var ended = 0
        var outcome: LauncherDragOutcome? = null
        state.putSource(LauncherSource(Any(), LauncherDragItem("tile", "tile"), 0, true,
            Rect(0f, 0f, 50f, 50f), {}, { ended++; outcome = it }))
        state.putTarget(LauncherTarget(Any(), "workspace", 0, true,
            Rect(0f, 0f, 200f, 200f), { true },
            { entered++ }, {}, { exited++ }, { true }))
        assertTrue(state.start(Offset(20f, 20f)))
        state.cancel()
        state.cancel()
        assertEquals(1, entered)
        assertEquals(1, exited)
        assertEquals(1, ended)
        assertEquals(LauncherDragOutcome(false, null), outcome)
        assertNull(state.hoveredTargetKey)
    }

    @Test fun geometryRemainsAnchoredToTheOriginalTouchInsteadOfTheItemTopLeft() {
        val drag = LauncherDragSession(
            LauncherDragItem("tile", "tile"),
            Rect(100f, 200f, 180f, 280f),
            Offset(110f, 250f), Offset(310f, 400f)
        )
        assertEquals(Rect(300f, 350f, 380f, 430f), drag.shadowBounds)
        assertEquals(Offset(340f, 390f), drag.visualCenter)
    }

    @Test fun updatingStorageDuringDragDoesNotOverwriteTheTransactionSnapshot() {
        val initial = mapOf("workspace" to listOf("a", "b"), "folder" to listOf("c"))
        val layout = LauncherReorderLayout({ it }, initial)
        assertTrue(layout.begin("a"))
        layout.hover("folder", "c", after = false)
        layout.update(mapOf("workspace" to listOf("different"), "folder" to emptyList()))
        assertEquals(listOf("a", "c"), layout.preview["folder"])
        layout.cancel()
        assertEquals(initial, layout.preview)
        assertNull(layout.draggedKey)
    }

    @Test fun reorderNoOpOrRejectedDestinationCannotChangeExistingOrder() {
        val initial = mapOf("workspace" to listOf("folder", "a", "b"),
            "folder" to listOf("x", "y"))
        val layout = LauncherReorderLayout({ it }, initial)
        assertFalse(layout.hover("workspace", "b")) // No drag has begun.
        assertTrue(layout.begin("a"))
        assertFalse(layout.begin("b")) // Concurrent second drag cannot start.
        assertFalse(layout.hover("folder", "not-a-child"))
        assertFalse(layout.hover("workspace", "a")) // Hovering oneself.
        assertFalse(layout.hover("workspace", "b", after = false)) // Already before b.
        assertEquals(initial, layout.preview)
        assertEquals(initial, layout.commit())
        assertNull(layout.commit())
    }

    @Test fun geometryIgnoresInvalidCellsAndUsesRealCentersInVerticalLayouts() {
        val cells = listOf(
            LauncherCell("invalid", Rect.Zero),
            LauncherCell("top", Rect(20f, 0f, 160f, 90f)),
            LauncherCell("bottom", Rect(20f, 200f, 160f, 290f))
        )
        assertEquals(LauncherReorderHit("top", false),
            LauncherGridGeometry.closestSlot(Offset(80f, 10f), cells, horizontal = false))
        assertEquals(LauncherReorderHit("bottom", true),
            LauncherGridGeometry.closestSlot(Offset(80f, 270f), cells, horizontal = false))
    }

}
