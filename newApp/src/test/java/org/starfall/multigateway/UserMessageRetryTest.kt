package org.starfall.multigateway

import org.junit.Assert.*
import org.junit.Test
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.ui.chat.prepareUserMessageRetry
import org.starfall.multigateway.ui.chat.unansweredUserMessageIds

class UserMessageRetryTest {
    private fun user(id: String) = StoredMessage(id, ChatRole.USER,
        listOf(MessageVersion(content = id, files = listOf("content://attachment/$id"))))
    private fun conversation(messages: List<StoredMessage>) = Conversation("chat", "Chat", 1, 1, messages)

    @Test fun onlyUnansweredSavedUserMessagesHaveResendButtons() {
        val messages = listOf(user("answered"), StoredMessage("answer", ChatRole.MODEL),
            user("pending1"), user("pending2"), user("queued").copy(isQueued = true))
        assertEquals(setOf("pending1", "pending2"), unansweredUserMessageIds(messages))
    }

    @Test fun retryResetsVersionsAndMessagesAfterTheSelectedQuestion() {
        val original = conversation(listOf(user("first"), user("later")))
        val version = MessageVersion(providerId = "provider", modelId = "model")
        val retried = prepareUserMessageRetry(original, "first", "reply", version)!!
        assertEquals(listOf("first", "reply"), retried.messages.map { it.id })
        assertEquals(original.messages.first(), retried.messages.first())
        assertEquals(ChatRole.MODEL, retried.messages[1].role)
        assertEquals(version, retried.messages[1].activeVersion)
        assertTrue(unansweredUserMessageIds(retried.messages).isEmpty())

        val secondRetry = prepareUserMessageRetry(retried, "first", "reply-2", version)!!
        assertEquals(listOf("first", "reply-2"), secondRetry.messages.map { it.id })
    }

    @Test fun queuedAndMissingMessagesCannotBeRetried() {
        val original = conversation(listOf(user("answered"), StoredMessage("answer", ChatRole.MODEL),
            user("queued").copy(isQueued = true)))
        for (id in listOf("answer", "queued", "missing")) {
            assertNull(prepareUserMessageRetry(original, id, "reply", MessageVersion()))
        }
    }

    @Test fun answeredUserMessageCanBeResentAndDropsAllLaterTurns() {
        val userVersions = listOf(MessageVersion(content = "old"), MessageVersion(content = "selected"))
        val answerVersions = listOf(MessageVersion(content = "first answer"), MessageVersion(content = "other answer"))
        val original = conversation(listOf(
            StoredMessage("target", ChatRole.USER, userVersions, activeVersionIndex = 1),
            StoredMessage("answer", ChatRole.MODEL, answerVersions, activeVersionIndex = 1),
            user("later"),
            StoredMessage("later-answer", ChatRole.MODEL, listOf(MessageVersion(content = "later answer")))
        ))

        val retried = prepareUserMessageRetry(original, "target", "new-answer", MessageVersion(content = "new response"))!!

        assertEquals(listOf("target", "new-answer"), retried.messages.map { it.id })
        assertEquals(listOf("selected"), retried.messages.first().versions.map { it.content })
        assertEquals("new response", retried.messages.last().content)
    }

    @Test fun retryInvalidatesOnlySummariesCoveringTheInsertedResponse() {
        val original = conversation(listOf(user("earlier"), user("target"), user("later")))
        val summary = ConversationSummary("summary", "Summary", "later")
        assertNull(prepareUserMessageRetry(original.copy(summary = summary), "target", "reply", MessageVersion())!!.summary)
        val earlierSummary = summary.copy(throughMessageId = "earlier")
        assertEquals(earlierSummary, prepareUserMessageRetry(original.copy(summary = earlierSummary),
            "target", "reply", MessageVersion())!!.summary)
    }
}
