package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.GenerationLoadingIndicator

/** The 3s/2s logo cycle fades without resizing the fixed logo or losing the loading mask. */
class GenerationLoadingIndicatorDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun generationLogoFadesInAndOutWithinTheLoadingMask() {
        var generating by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color.Red,
                surfaceContainerHighest = Color.Blue,
                onSurface = Color.Green
            )) {
                if (generating) GenerationLoadingIndicator(modelName = "model-test", modelId = "model-test")
            }
        }

        fun centerRed(): Float {
            val pixels = compose.onNodeWithTag("generation-logo-mask", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2].red
        }

        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("generation-loading-indicator", useUnmergedTree = true).assertIsDisplayed()
        val initialLogoBounds = compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val indicatorBounds = compose.onNodeWithTag("generation-loading-indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Logo and indicator must share exactly the same drawing area", indicatorBounds, initialLogoBounds)

        compose.mainClock.advanceTimeBy(2_800)
        val hidden = centerRed()
        compose.mainClock.advanceTimeBy(350)
        val fadingIn = centerRed()
        compose.mainClock.advanceTimeBy(400)
        val visible = centerRed()
        assertTrue("Logo should gradually replace the solid primary-colored mask",
            hidden > fadingIn + 0.1f && fadingIn > visible + 0.1f)

        compose.mainClock.advanceTimeBy(1_600)
        val fadingOut = centerRed()
        compose.mainClock.advanceTimeBy(400)
        val hiddenAgain = centerRed()
        assertTrue("Logo should fade out smoothly before the next hidden phase",
            visible + 0.1f < fadingOut && fadingOut + 0.1f < hiddenAgain)
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("The logo cannot resize or rotate when the mask morphs", initialLogoBounds,
            compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)

        compose.runOnIdle { generating = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("generation-model-logo", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("generation-logo-fallback", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("generation-loading-indicator", useUnmergedTree = true).assertDoesNotExist()
    }
}
