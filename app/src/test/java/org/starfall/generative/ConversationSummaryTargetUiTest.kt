package org.starfall.generative

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.shared.ui.ConversationSummarySheet
import org.starfall.generative.shared.model.ConversationSummaryRequest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ConversationSummaryTargetUiTest {
    @get:Rule val compose = createComposeRule()
    private val requests = mutableListOf<ConversationSummaryRequest>()
    private var dismissals = 0
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(accept: Boolean = false, customTarget: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                ConversationSummarySheet(
                    onStart = { requests += it; accept },
                    onDismiss = { dismissals++ })
            }
        }
        if (customTarget) compose.onNode(isToggleable()).performScrollTo().performClick()
    }

    private fun target() = compose.onNode(hasSetTextAction() and hasText("Target size (tokens)"))
    private fun start() = compose.onNodeWithText("Start summarization")

    @Test fun opensAsConversationSummarizationWithSixThousandTokenDefaultAndNoDescriptionLines() {
        show(customTarget = false)
        compose.onNodeWithText(context.getString(R.string.conversation_summarization_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.conversation_summary_target_size, 6000)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.conversation_summary_description)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.conversation_summary_tokens_note)).assertDoesNotExist()
        start().performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(6000, requests.single().targetTokens) }
    }

    @Test fun customTargetsAboveSixThousandAndIntegerBoundariesEnableStartAndReachTheCallback() {
        show()
        for (tokens in listOf(1, 6000, 6001, 8000, 10000, Int.MAX_VALUE)) {
            target().performScrollTo().performTextReplacement(tokens.toString())
            start().performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(tokens, requests.last().targetTokens)
                assertEquals(false, requests.last().chunked)
            }
        }
        compose.runOnIdle { assertEquals(6, requests.size) }
    }

    @Test fun acceptedLargeTargetDismissesTheSheet() {
        show(accept = true)
        target().performScrollTo().performTextReplacement("10000")
        start().performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(10_000, requests.single().targetTokens)
            assertEquals(1, dismissals)
        }
    }

    @Test fun invalidTargetsShowLocalizedFeedbackAndNeverStartOrSilentlyBecomePositive() {
        show()
        val feedback = context.getString(R.string.conversation_summary_invalid_target)
        for (invalid in listOf("", "0", "-1", "1.5", "abc", "2147483648", "999999999999999999999999")) {
            target().performScrollTo().performTextReplacement(invalid)
            start().assertIsNotEnabled()
            compose.onNodeWithText(feedback).performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, requests.size) }
        }
        target().performScrollTo().performTextReplacement("8000")
        compose.onNodeWithText(feedback).assertDoesNotExist()
        start().assertIsEnabled()
    }

    @Test fun chunkBoundsStillApplyAndOnlyMatterInMultiplePassMode() {
        show()
        target().performScrollTo().performTextReplacement("8000")
        compose.onNodeWithText("Multiple passes").performScrollTo().performClick()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        val chunk = compose.onNode(hasSetTextAction() and hasText("Tokens per pass"))
        val feedback = context.getString(R.string.conversation_summary_invalid_chunk)
        for (invalid in listOf("", "0", "-1", "100001", "2147483648")) {
            chunk.performScrollTo().performTextReplacement(invalid)
            start().assertIsNotEnabled()
            compose.onNodeWithText(feedback).performScrollTo().assertIsDisplayed()
        }
        for (valid in listOf(1, 100_000)) {
            chunk.performScrollTo().performTextReplacement(valid.toString())
            start().performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(8000, requests.last().targetTokens)
                assertEquals(true, requests.last().chunked)
                assertEquals(valid, requests.last().tokensPerChunk)
            }
        }
        chunk.performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("All at once").performScrollTo().performClick()
        compose.onNodeWithText(feedback).assertDoesNotExist()
        start().assertIsEnabled()
    }
}
