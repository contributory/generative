package org.starfall.generative

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.starfall.generative.features.home.chat.ui.RetainedChatConversations
import org.starfall.generative.features.home.chat.ui.RetainedChatPages
import org.starfall.generative.features.home.chat.ui.retainedHeapFraction
import org.starfall.generative.shared.model.Conversation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = LibraryTestApplication::class)
class RetainedChatConversationsTest {
    @get:Rule val compose = createComposeRule()

    private fun chat(id: String, temporary: Boolean = false) = Conversation(
        id = id, title = id, createdAt = 1L, updatedAt = 1L, isTemporary = temporary
    )

    @Test fun switchingChatsParksCompositionAndRestoresOriginalOnReturn() {
        val a = chat("a")
        val b = chat("b")
        var current by mutableStateOf<Conversation?>(a)
        val retained = RetainedChatPages()
        val mounts = mutableMapOf<String, Int>()
        val disposals = mutableMapOf<String, Int>()
        compose.setContent {
            RetainedChatConversations(retained, current, setOf("a", "b"), true) { conversation, _ ->
                val id = conversation?.id ?: "empty"
                DisposableEffect(Unit) {
                    mounts[id] = (mounts[id] ?: 0) + 1
                    onDispose { disposals[id] = (disposals[id] ?: 0) + 1 }
                }
                Box(Modifier.fillMaxSize().testTag(id))
            }
        }
        compose.onNodeWithTag("a").assertIsDisplayed()
        compose.runOnIdle { current = b }
        compose.onNodeWithTag("b").assertIsDisplayed()
        compose.onNodeWithTag("a").assertIsNotDisplayed()
        compose.runOnIdle {
            assertEquals(1, mounts["a"])
            assertEquals(null, disposals["a"])
        }
        compose.runOnIdle { current = a }
        compose.onNodeWithTag("a").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, mounts["a"]) }
        compose.runOnIdle { retained.trimToFraction(0f) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, disposals["b"]) }
        compose.runOnIdle { current = b }
        compose.onNodeWithTag("b").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, mounts["b"]) }
    }

    @Test fun recentlyVisitedChatsAreBoundedAndSelectionsFollowThePage() {
        val retained = RetainedChatPages()
        val ids = (1..10).map { "chat-$it" }.toSet()
        retained.record(chat("chat-1"), ids)
        val original = retained.selection("chat-1")
        original.toggle("file-a")
        (2..9).forEach { retained.record(chat("chat-$it"), ids) }
        assertSame(original, retained.selection("chat-1"))
        retained.record(chat("chat-10"), ids)
        assertEquals(10, retained.ids.size) // No arbitrary chat-count cutoff.
        retained.trimToFraction(0.5f)
        assertEquals(5, retained.ids.size) // Active plus four recently used chats (9 / 2 truncated).
        assertFalse("chat-1" in retained.ids)
        assertTrue(retained.selection("chat-1").attachments.value.isEmpty())
        retained.trimToFraction(0f)
        assertEquals(listOf("chat-10"), retained.ids.toList())
    }

    @Test fun availableHeapControlsRetentionInsteadOfAnArbitraryCount() {
        assertEquals(1f, retainedHeapFraction(50, 100), 0f)
        assertEquals(0f, retainedHeapFraction(90, 100), 0f)
        assertEquals(0.5f, retainedHeapFraction(75, 100), 0.001f)
        assertEquals(0f, retainedHeapFraction(0, 0), 0f)
    }

    @Test fun temporaryChatsAndDeletedHistoryAreNeverCached() {
        val retained = RetainedChatPages()
        retained.record(chat("a"), setOf("a"))
        retained.record(chat("temp", temporary = true), setOf("a"))
        assertFalse("temp" in retained.ids)
        retained.record(chat("a"), setOf("a"))
        retained.discard(setOf("a"))
        assertTrue(retained.ids.isEmpty())
    }
}
