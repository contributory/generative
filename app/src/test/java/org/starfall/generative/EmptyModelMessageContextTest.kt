package org.starfall.generative

import org.junit.Assert.assertEquals
import org.junit.Test
import org.starfall.generative.shared.model.*

class EmptyModelMessageContextTest {
    private fun message(id: String, role: ChatRole, content: String = "", reasoning: String? = null, files: List<String> = emptyList()) =
        StoredMessage(id, role, listOf(MessageVersion(content = content, reasoningContent = reasoning, files = files)))

    @Test fun stoppedRepliesWithoutAnswerTextAreNotSentAsContext() {
        val messages = listOf(
            message("u1", ChatRole.USER, "hello"),
            message("reasoningOnly", ChatRole.MODEL, reasoning = "thinking..."),
            message("whitespace", ChatRole.MODEL, "  \n"),
            message("u2", ChatRole.USER, "again")
        )
        assertEquals(listOf("u1", "u2"), messages.withoutEmptyModelMessages().map { it.id })
    }

    @Test fun answersAndFileRepliesAreKept() {
        val messages = listOf(
            message("u1", ChatRole.USER, "hello"),
            message("answer", ChatRole.MODEL, "partial answer"),
            message("image", ChatRole.MODEL, files = listOf("content://attachment/image"))
        )
        assertEquals(listOf("u1", "answer", "image"), messages.withoutEmptyModelMessages().map { it.id })
    }
}
