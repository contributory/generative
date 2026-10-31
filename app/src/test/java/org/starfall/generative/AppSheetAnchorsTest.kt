package org.starfall.generative

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.shared.ui.sheets.BottomSheet

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class AppSheetAnchorsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nativePartialAndExpandedStopsKeepTheExpandedSheetBelowStatusBar() {
        var statusInset = 0f
        compose.setContent {
            val inset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
            SideEffect { statusInset = inset }
            MaterialTheme {
                BottomSheet(onDismissRequest = {}) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()

        val collapsedTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("bottom-sheet-drag-handle").performTouchInput { swipeUp() }
        compose.waitForIdle()

        val expandedTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        val collapsedPx = with(compose.density) { collapsedTop.toPx() }
        val expandedPx = with(compose.density) { expandedTop.toPx() }
        assertTrue("Swiping up should settle on the second (expanded) stop", expandedPx < collapsedPx - 40f)
        assertTrue("Expanded content should remain below the status bar", expandedPx >= statusInset - 2f)
    }

    @Test
    fun shortContentAlsoKeepsTheDefaultAndExpandedStops() {
        compose.setContent {
            MaterialTheme {
                BottomSheet(onDismissRequest = {}) { Text("Short sheet") }
            }
        }
        compose.waitForIdle()
        val partialTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("bottom-sheet-drag-handle").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val expandedTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        assertTrue("Short content must still have a second sheet stop", expandedTop < partialTop)
    }

    @Test
    fun fixedFractionSheetsStillExposeBothNativeStops() {
        compose.setContent {
            MaterialTheme {
                BottomSheet(onDismissRequest = {}, fixedHeightFraction = 0.82f) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        val partialTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("bottom-sheet-drag-handle").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val expandedTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        assertTrue("Fixed-fraction sheets must not skip PartiallyExpanded",
            expandedTop < partialTop)
    }
    @Test
    fun automaticExpansionStillAllowsNativePartialStopByDraggingDown() {
        compose.setContent {
            MaterialTheme {
                BottomSheet(onDismissRequest = {}, expandOnOpen = true) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        val expandedTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("bottom-sheet-drag-handle").performTouchInput { swipeDown() }
        compose.waitForIdle()
        val partialTop = compose.onNodeWithTag("bottom-sheet-window")
            .getUnclippedBoundsInRoot().top
        assertTrue("Auto-expand must not remove the native partially expanded anchor", partialTop > expandedTop)
    }

}
