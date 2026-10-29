package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.GenerationLoadingIndicator

/** Verify the 3s/2s logo cycle preserves Material's mask and the logo bounds. */
class GenerationLoadingIndicatorDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun generationHidesLogoForThreeSecondsAndShowsItForTwoSeconds() {
        var generating by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            if (generating) GenerationLoadingIndicator(modelName = "model-test", modelId = "model-test")
        } }
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("generation-loading-indicator", useUnmergedTree = true).assertIsDisplayed()
        val initialLogoBounds = compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val indicatorBounds = compose.onNodeWithTag("generation-loading-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Logo and indicator must share exactly the same drawing area", indicatorBounds, initialLogoBounds)
        compose.mainClock.advanceTimeBy(2_800)
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_700)
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("The logo cannot resize or rotate when the mask morphs", initialLogoBounds,
            compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { generating = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("generation-loading-indicator", useUnmergedTree = true).assertDoesNotExist()
    }
}
