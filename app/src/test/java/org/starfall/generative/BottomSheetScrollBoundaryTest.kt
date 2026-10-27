package org.starfall.generative

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.shared.ui.sheets.bottomSheetListScrollBoundary
import org.starfall.generative.shared.ui.sheets.bottomSheetHeightBounds

class BottomSheetScrollBoundaryTest {
    @Test fun flingCanStopAtRealListEdgesAndRegularListMotionIsNotConsumed() = runBlocking {
        val boundary = bottomSheetListScrollBoundary()
        for (delta in listOf(-48f, 48f)) {
            assertEquals(Offset.Zero, boundary.onPreScroll(Offset(0f, delta), NestedScrollSource.UserInput))
            assertEquals(Offset.Zero, boundary.onPostScroll(Offset(0f, delta), Offset.Zero, NestedScrollSource.UserInput))
            assertEquals(Offset.Zero, boundary.onPostScroll(Offset.Zero, Offset(0f, delta), NestedScrollSource.SideEffect))
            assertEquals(Velocity.Zero, boundary.onPreFling(Velocity(0f, delta * 100)))
        }
    }

    @Test fun listStartPassesUnusedDownwardScrollAndFlingToTheSheet() = runBlocking {
        val boundary = bottomSheetListScrollBoundary()

        assertEquals(
            Offset.Zero,
            boundary.onPostScroll(Offset.Zero, Offset(5f, 48f), NestedScrollSource.UserInput)
        )
        assertEquals(
            Offset(0f, -48f),
            boundary.onPostScroll(Offset.Zero, Offset(5f, -48f), NestedScrollSource.UserInput)
        )
        assertEquals(
            Velocity.Zero,
            boundary.onPostFling(Velocity.Zero, Velocity(50f, 2_000f))
        )
        assertEquals(
            Velocity(0f, -2_000f),
            boundary.onPostFling(Velocity.Zero, Velocity(50f, -2_000f))
        )
    }

    @Test fun upwardDragAndFlingExpandFirstAndOnlyConsumeTheRemainingHeight() = runBlocking {
        val bounds = bottomSheetHeightBounds(1200, 1200, topInset = 80)
        var height = 700f
        val boundary = bottomSheetListScrollBoundary { delta ->
            val consumed = bounds.consumeUpwardScroll(height, delta)
            height -= consumed
            consumed
        }

        assertEquals(Offset(0f, -100f), boundary.onPreScroll(Offset(4f, -100f), NestedScrollSource.UserInput))
        assertEquals(800f, height, 0f)
        assertEquals(Offset.Zero, boundary.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput))
        // Programmatic list animations do not resize the sheet.
        assertEquals(Offset.Zero, boundary.onPreScroll(Offset(0f, -40f), NestedScrollSource.SideEffect))
        boundary.onPreFling(Velocity(0f, -2_000f))
        assertEquals(Offset(0f, -320f), boundary.onPreScroll(Offset(0f, -500f), NestedScrollSource.SideEffect))
        assertEquals(1120f, height, 0f)
        assertEquals(Offset.Zero, boundary.onPreScroll(Offset(0f, -40f), NestedScrollSource.SideEffect))
        boundary.onPostFling(Velocity.Zero, Velocity.Zero)
        Unit
    }

    @Test fun newUserInputClearsAnInterruptedFlingBeforeProgrammaticScrolling() = runBlocking {
        val boundary = bottomSheetListScrollBoundary { it }
        boundary.onPreFling(Velocity(0f, -1_000f))
        boundary.onPreScroll(Offset(0f, 5f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, boundary.onPreScroll(Offset(0f, -40f), NestedScrollSource.SideEffect))
    }
}
