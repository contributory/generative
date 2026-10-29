package org.starfall.generative

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.features.home.chat.ui.*
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage

class ChatMessageListStateTest {
    private fun message(id: String, role: ChatRole) = StoredMessage(id, role, listOf(MessageVersion(content = id)))

    @Test fun initialEndBoundaryUsesChronologicalStableTurns() {
        val messages = listOf(message("u1", ChatRole.USER), message("m1", ChatRole.MODEL),
            message("tool-block", ChatRole.MODEL), message("u2", ChatRole.USER), message("m2", ChatRole.MODEL))
        assertEquals(listOf(listOf("u1", "m1", "tool-block"), listOf("u2", "m2")),
            chatMessageTurns(messages).map { it.map { message -> message.id } })
        val state = ChatMessageListState(initialMessages = messages)
        assertEquals("Initial position is the boundary after the chronological turns", 2,
            state.listState.firstVisibleItemIndex)
        assertEquals(0, state.listState.firstVisibleItemScrollOffset)
        assertEquals(0L, state.requestCount)
        assertTrue(chatMessageTurns(emptyList()).isEmpty())
    }

    @Test fun assistantOnlyHistoryAndQueuedPromotionKeepStableKeys() {
        val assistant = message("m1", ChatRole.MODEL)
        val queued = message("q", ChatRole.USER).copy(isQueued = true)
        val before = chatMessageTurns(listOf(assistant, queued))
        val after = chatMessageTurns(listOf(assistant, queued.copy(isQueued = false), message("reply", ChatRole.MODEL)))
        assertEquals(before.map { it.first().id }, after.map { it.first().id })
    }

    @Test fun bottomGapIsDeclarativeAndCappedByUsableViewport() {
        assertEquals(240f, chatBottomGap(800.dp, 600.dp).value, 0.001f)
        assertEquals(180f, chatBottomGap(1000.dp, 180.dp).value, 0.001f)
        assertEquals(0f, chatBottomGap(800.dp, (-1).dp).value, 0.001f)
    }

    @Test fun sendHasPriorityOverContentFollowAndDoesNotEnablePreference() {
        val state = ChatMessageListState()
        state.send("user")
        val send = state.request!!
        state.follow(enabled = true)
        assertEquals(send, state.request)
        state.complete(send)
        state.follow(enabled = false)
        assertNull(state.request)
    }

    @Test fun manualTakeoverCancelsRequestsAndMinimapDoesNotEnableFollow() {
        val state = ChatMessageListState()
        state.follow(enabled = true)
        state.manualScroll(towardsEnd = false)
        state.follow(enabled = true)
        assertNull(state.request)
        state.navigate("old")
        val navigation = state.request!!
        state.follow(enabled = true)
        assertEquals(navigation, state.request)
        state.complete(navigation)
        assertTrue(state.detached)
        state.jump()
        assertFalse(state.detached)
        assertEquals(ChatListAction.Jump, state.request!!.action)
    }

    @Test fun repeatedMinimapTargetKeepsTheInFlightNativeNavigation() {
        val state = ChatMessageListState()
        state.navigate("reply", toEnd = true)
        val first = state.request
        val count = state.requestCount
        state.navigate("reply", toEnd = true)
        assertEquals(first, state.request)
        assertEquals(count, state.requestCount)
        state.navigate("reply", toEnd = false)
        assertNotEquals(first, state.request)
        assertTrue(state.detached)
    }

    @Test fun latestFollowSupersedesObsoleteFollowButCannotClearNewerSend() {
        val state = ChatMessageListState()
        state.follow(enabled = true)
        val first = state.request!!
        state.follow(enabled = true)
        assertNotEquals(first, state.request)
        state.send("new-user")
        state.complete(first)
        assertEquals(ChatListAction.Send("new-user"), state.request!!.action)
    }

    @Test fun contentGrowthIsTheOnlyAutomaticFollowTriggerAndRespectsSettings() {
        val state = ChatMessageListState()
        state.contentEndPosition("reply", 100, enabled = true) // Initial layout is not a stream update.
        assertEquals(0L, state.requestCount)
        state.contentEndPosition("reply", 100, enabled = true) // Scroll moves the viewport, not the content end within its turn.
        assertEquals(0L, state.requestCount)
        state.contentEndPosition("reply", 140, enabled = false) // Auto-scroll disabled in settings.
        assertEquals(0L, state.requestCount)
        state.contentEndPosition("reply", 180, enabled = true)
        assertEquals(ChatListAction.Follow, state.request?.action)
        assertEquals(1L, state.requestCount)
        state.contentEndPosition("reply", 180, enabled = true)
        assertEquals(1L, state.requestCount)
        state.manualScroll(towardsEnd = false)
        state.contentEndPosition("reply", 230, enabled = true)
        assertEquals("Manual drag pauses automatic follow", 1L, state.requestCount)
        state.resume()
        state.contentEndPosition("reply", 250, enabled = true)
        assertEquals(2L, state.requestCount)
        state.contentEndPosition("another-reply", 10, enabled = true)
        assertEquals("New messages establish a new layout baseline", 2L, state.requestCount)
    }

    @Test fun freezeCancelsOnlyAutomaticFollowNotExplicitNavigation() {
        val state = ChatMessageListState()
        state.follow(true)
        state.cancelFollow()
        assertNull(state.request)
        state.send("user")
        val send = state.request
        state.cancelFollow()
        assertEquals(send, state.request)
    }

    @Test fun onlyNativeManualReturnToVisibleRealEndResumesFollow() {
        val state = ChatMessageListState()
        state.detach()
        state.endVisibility("end", true, "end")
        assertTrue("Minimap navigation alone cannot resume following", state.detached)
        state.manualScroll(towardsEnd = true)
        state.endVisibility("old", true, "end")
        assertTrue(state.detached)
        state.endVisibility("end", true, "end")
        assertFalse(state.detached)
    }
}
