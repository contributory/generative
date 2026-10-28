package org.starfall.generative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.material3.MaterialTheme
import org.starfall.generative.features.home.chat.ui.AssistantMessageCard
import org.starfall.generative.features.home.chat.ui.ChatRenderMetrics
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.features.home.chat.ui.rememberChatAction
import org.starfall.generative.features.home.chat.ui.rememberChatValueAction

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatMessageCallbacksTest {
    @get:Rule val compose = createComposeRule()

    @Test fun callbackIdentitySurvivesRecompositionAndInvokesLatestAction() {
        var generation by mutableStateOf(0)
        var invoked = -1
        var callback: (() -> Unit)? = null
        compose.setContent {
            val current = generation
            callback = rememberChatAction { invoked = current }
        }
        compose.waitForIdle()
        val original = callback!!
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()
        assertSame(original, callback)
        compose.runOnIdle { original() }
        assertEquals(1, invoked)
    }

    @Test fun unchangedAssistantCardSkipsParentGenerationTransition() {
        ChatRenderMetrics.resetForTests()
        val oldReply = StoredMessage("earlier", ChatRole.MODEL, listOf(MessageVersion()))
        var generating by mutableStateOf(true)
        var latestCopy: (() -> Unit)? = null
        var observedGeneration: Boolean? = null
        compose.setContent {
            val isGenerating = generating
            val onCopy = rememberChatAction { observedGeneration = isGenerating }
            latestCopy = onCopy
            MaterialTheme {
                AssistantMessageCard(
                    message = oldReply,
                    isStreaming = false,
                    onCopy = onCopy,
                    onRegenerate = rememberChatAction {},
                    onEdit = rememberChatAction {},
                    onDelete = rememberChatAction {},
                    onRead = rememberChatAction {},
                    onSwitchVersion = rememberChatValueAction { _: Int -> }
                )
            }
        }
        compose.waitForIdle()
        val renderedBefore = ChatRenderMetrics.messageRecompositionCountForTests()
        compose.runOnIdle { generating = false }
        compose.waitForIdle()
        assertEquals("An unchanged historical card should be skipped", renderedBefore,
            ChatRenderMetrics.messageRecompositionCountForTests())
        compose.runOnIdle { latestCopy!!() }
        assertEquals(false, observedGeneration)
    }

    @Test fun parameterizedCallbackAlsoUsesLatestMessageState() {
        var content by mutableStateOf("before")
        var result = ""
        var callback: ((Int) -> Unit)? = null
        compose.setContent {
            val latest = content
            callback = rememberChatValueAction { index: Int -> result = "$latest:$index" }
        }
        compose.waitForIdle()
        val original = callback!!
        compose.runOnIdle { content = "after" }
        compose.waitForIdle()
        assertSame(original, callback)
        compose.runOnIdle { original(2) }
        assertEquals("after:2", result)
    }
}
