package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.shared.model.ConversationSummaryProgress
import org.starfall.generative.features.home.chat.ui.ConversationSummaryDivider

class ConversationSummaryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun runningSummaryShowsCancelButtonAndCancelsOnClick() {
        var cancellations = 0
        compose.setContent {
            MaterialTheme {
                ConversationSummaryDivider(
                    summary = null,
                    progress = ConversationSummaryProgress(
                        conversationId = "conversation",
                        throughMessageId = "message",
                        label = "Summarizing conversation…",
                        progress = 0.35f
                    ),
                    onOpenSummary = {},
                    onCancelSummary = { cancellations++ }
                )
            }
        }

        compose.onNodeWithContentDescription("Cancel")
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }
}
