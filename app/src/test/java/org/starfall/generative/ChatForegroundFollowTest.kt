package org.starfall.generative

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.model.*
import org.starfall.generative.features.home.chat.ui.*
import org.starfall.generative.navigation.*
import org.starfall.generative.shared.model.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xhdpi", application = LibraryTestApplication::class)
class ChatForegroundFollowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun streamingWhileMenuIsForegroundDoesNotNavigateBackAndResumesOnReturn() {
        val messages = listOf(
            StoredMessage("user", ChatRole.USER, listOf(MessageVersion(content = "Hello"))),
            StoredMessage("reply", ChatRole.MODEL, listOf(MessageVersion(content = "")))
        )
        val list = ChatMessageListState(initialMessages = messages)
        var content by mutableStateOf("A short reply")
        val stream = MutableStateFlow<ActiveChatStreamState?>(null)
        var generation = 0L
        fun publish() {
            generation++
            stream.value = ActiveChatStreamState(
                conversationId = "chat", messageId = "reply", generationId = 1L,
                revision = generation, status = ChatStreamStatus.STREAMING,
                content = StreamTextSnapshot.of(content), reasoning = StreamTextSnapshot.of(),
                stopSignal = StreamFreezeSignal(), lastCommitMetrics = null,
                cumulativeMetrics = StreamMetricsSnapshot()
            )
        }
        lateinit var nav: NavHostController
        compose.setContent { MaterialTheme {
            nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            RetainedNavHost(nav, startDestination = "chat") {
                composable("chat") {
                    CompositionLocalProvider(LocalChatForeground provides (entry?.destination?.route == "chat")) {
                        ChatMessageList(list, messages, true, true, false, stream, 80.dp, 100.dp) {
                            Text(if (it.id == "reply") content else "Hello", Modifier.fillMaxWidth())
                        }
                    }
                }
                composable("features") { Box(Modifier.fillMaxSize().testTag("menu-root")) }
            }
        } }
        compose.runOnIdle { publish() }
        compose.waitForIdle()
        compose.runOnIdle { nav.navigate("features") }
        compose.waitForIdle()
        val countWhileHidden = list.requestCount
        compose.runOnIdle {
            content = (1..100).joinToString("\n") { "Streaming line $it" }
            publish()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("menu-root").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Hidden chat must not request relocation", countWhileHidden, list.requestCount)
            assertEquals("features", nav.currentDestination?.route)
        }
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("chat", nav.currentDestination?.route)
            assertTrue("Resume should catch up to new stream content", list.requestCount > countWhileHidden)
        }
    }

    @Test fun liveThinkingAnimationPausesBehindOtherFeaturesAndCatchesUpOnReturn() {
        var foreground by mutableStateOf(true)
        var reasoning by mutableStateOf((1..12).joinToString("\n\n") { "Thinking paragraph $it." })
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalChatForeground provides foreground) {
                LiveThinkingBlock(reasoning, active = true, showHeader = false)
            }
        } }
        fun range() = compose.onNodeWithTag("live-thinking-preview").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        compose.waitUntil(10_000) { range().maxValue() > 0f && range().value() == range().maxValue() }
        compose.runOnIdle {
            foreground = false
            reasoning = (1..45).joinToString("\n\n") { "Thinking paragraph $it." }
        }
        compose.waitForIdle()
        val hiddenOffset = range().value()
        assertTrue("New content must enlarge the scroll range", range().maxValue() > hiddenOffset)
        compose.waitForIdle()
        assertEquals("Thinking should not animate while chat is hidden", hiddenOffset, range().value(), 0f)
        compose.runOnIdle { foreground = true }
        compose.waitUntil(10_000) { range().maxValue() > 0f && range().value() == range().maxValue() }
    }
}
