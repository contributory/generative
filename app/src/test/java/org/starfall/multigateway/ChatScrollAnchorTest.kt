package org.starfall.multigateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.starfall.multigateway.home.chat.logic.SentMessageDetector
import org.starfall.multigateway.home.chat.logic.anchorTailSpacer
import org.starfall.multigateway.home.chat.logic.sentMessageScrollOffset
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage

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

    @Test fun shortMessageSitsInTheMiddle() {
        // visible area 600px, message 80px: top at 300px below the top of the area
        assertEquals(-300, sentMessageScrollOffset(visibleHeight = 600, messageHeight = 80))
        assertEquals(-300, sentMessageScrollOffset(visibleHeight = 600, messageHeight = 300))
    }

    @Test fun messageTallerThanHalfIsAlignedByItsBottomEdge() {
        // 500px tall: top at 100px so the bottom edge meets the end of the area
        assertEquals(-100, sentMessageScrollOffset(visibleHeight = 600, messageHeight = 500))
    }

    @Test fun messageTallerThanTheAreaShowsItsEnd() {
        // 900px tall in a 600px area: scrolled 300px into the message
        assertEquals(300, sentMessageScrollOffset(visibleHeight = 600, messageHeight = 900))
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
