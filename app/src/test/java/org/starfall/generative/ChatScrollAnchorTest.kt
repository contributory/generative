package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.starfall.generative.features.home.chat.logic.SentMessageDetector
import org.starfall.generative.features.home.chat.logic.anchorTailSpacer
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.features.home.chat.logic.withUpdatedMessageStructure

class ChatScrollAnchorTest {
    private fun user(id: String, text: String = id) =
        StoredMessage(id, ChatRole.USER, listOf(MessageVersion(content = text)))

    private fun model(id: String, text: String = "") =
        StoredMessage(id, ChatRole.MODEL, listOf(MessageVersion(content = text)))

    private val chat = listOf(user("u1"), model("m1", "answer"))

    @Test fun firstSnapshotIsABaselineNotASend() {
        assertNull(SentMessageDetector().observe(chat))
    }

    @Test fun typingAMessageIsASend() {
        val detector = SentMessageDetector().also { it.observe(chat) }
        assertEquals("u2", detector.observe(chat + user("u2") + model("m2")))
    }

    @Test fun drainedQueueMessageWithAFreshIdIsASend() {
        // The queue drain removes the queued id and sends a new message, so the id is never seen before.
        val detector = SentMessageDetector().also { it.observe(chat) }
        assertEquals("fresh", detector.observe(chat + user("fresh", "pending text") + model("m2")))
    }

    @Test fun messageHandedOverByTheToolLoopKeepsItsQueuedIdAndIsASend() {
        // The queued message never was in history, so a same-id arrival is still new to history.
        val detector = SentMessageDetector().also { it.observe(chat) }
        assertEquals("queued-id", detector.observe(chat + user("queued-id") + model("m2")))
    }

    @Test fun streamingUpdatesAndEditsOfExistingMessagesAreNotSends() {
        val detector = SentMessageDetector().also { it.observe(chat) }
        assertNull(detector.observe(listOf(user("u1"), model("m1", "answer, now longer"))))
        assertNull(detector.observe(listOf(user("u1", "edited"), model("m1", "answer, now longer"))))
    }

    @Test fun regeneratingOrRetryingAddsNoUserMessage() {
        val detector = SentMessageDetector().also { it.observe(chat) }
        assertNull(detector.observe(listOf(user("u1"), model("m1-retry"))))
    }

    @Test fun sameListInstanceIsIgnored() {
        val detector = SentMessageDetector().also { it.observe(chat) }
        val next = chat + user("u2") + model("m2")
        assertEquals("u2", detector.observe(next))
        assertNull(detector.observe(next))
    }

    @Test fun bulkChangesAreNotSends() {
        val detector = SentMessageDetector().also { it.observe(chat) }
        val reloaded = chat + (2..8).flatMap { listOf(user("u$it"), model("m$it")) }
        assertNull(detector.observe(reloaded))
    }

    @Test fun firstMessageOfAnEmptyChatHasNothingToScrollAgainst() {
        val detector = SentMessageDetector().also { it.observe(emptyList()) }
        assertNull(detector.observe(chat))
    }

    @Test fun explicitStructuralRevisionSkipsContentOnlyHistoryScans() {
        val detector = SentMessageDetector()
        assertNull(detector.observe(chat, structuralRevision = 4L))

        val contentOnlyUpdate = listOf(user("u1", "edited"), model("m1", "longer answer"))
        assertNull(detector.observe(contentOnlyUpdate, structuralRevision = 4L))
        assertEquals("no repeated whole-history ID scan", 1L, detector.idScanCount)

        val sent = chat + user("u2") + model("m2")
        assertEquals("u2", detector.observe(sent, structuralRevision = 5L))
        assertEquals(2L, detector.idScanCount)
    }

    @Test fun messageContentChangesDoNotAdvanceStructureRevisionButIdentityChangesDo() {
        val conversation = Conversation("chat", "Chat", 1L, 1L, messages = chat)
        val edited = conversation.withUpdatedMessageStructure(
            listOf(user("u1", "edited"), model("m1", "answer"))
        )
        assertEquals(0L, edited.structuralRevision)

        val deleted = edited.withUpdatedMessageStructure(listOf(user("u1", "edited")))
        assertEquals(1L, deleted.structuralRevision)
    }

    @Test fun spacerFillsTheGapToOneVisibleAreaAndShrinksAsTheReplyGrows() {
        assertEquals(600, anchorTailSpacer(visibleHeight = 600, tailHeight = 0))
        assertEquals(450, anchorTailSpacer(visibleHeight = 600, tailHeight = 150))
        assertEquals(0, anchorTailSpacer(visibleHeight = 600, tailHeight = 600))
        assertEquals(0, anchorTailSpacer(visibleHeight = 600, tailHeight = 2_000))
    }

    @Test fun contentPlusSpacerNeverChangesWhileTheReplyIsShorterThanTheArea() {
        val totals = (0..600 step 50).map { tail -> tail + anchorTailSpacer(600, tail) }.toSet()
        assertEquals(setOf(600), totals)
    }
}
