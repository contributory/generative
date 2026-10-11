package org.starfall.multigateway

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.multigateway.data.model.*
import org.starfall.multigateway.ui.chat.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi")
class ReasoningEffectsLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun redEffectContinuesAfterSheetDismissalWithoutBlockingClicks() {
        var clicks = 0
        lateinit var burst: ThinkingColorBurst
        val provider = LlmProviderInfo("p", "Provider", ProviderType.OPENAI, baseUrl = "")
        compose.setContent {
            MaterialTheme {
                val screenBurst = rememberThinkingColorBurst()
                burst = screenBurst
                ThinkingScreenBurstLayer(screenBurst)
                var sheetVisible by remember { mutableStateOf(true) }
                var effort by remember { mutableStateOf<String?>(null) }
                CompositionLocalProvider(LocalThinkingScreenBurst provides screenBurst) {
                    Button(onClick = { clicks++ }) { Text("Underlying action") }
                    if (sheetVisible) SelectedModelOverviewSheet(
                        provider = provider,
                        folderName = null,
                        modelId = "Effect model",
                        config = ModelConfiguration(),
                        conversationReasoningEffort = effort,
                        onSetReasoningEffort = { effort = it },
                        onOpenModelPicker = { sheetVisible = false },
                        onDismiss = { sheetVisible = false }
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reasoning-slider").performSemanticsAction(SemanticsActions.SetProgress) {
            it(5f)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertTrue(burst.progress.value < 1f) }
        compose.onNodeWithText("Effect model").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("reasoning-slider").assertDoesNotExist()
        compose.runOnIdle { assertTrue("Dismissing the sheet must not cancel the screen effect", burst.progress.value < 1f) }
        compose.onNodeWithText("Underlying action").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.mainClock.advanceTimeBy(7000)
        compose.runOnIdle { assertEquals(1f, burst.progress.value, 0.001f) }
    }
}
